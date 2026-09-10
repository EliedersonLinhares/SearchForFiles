package com.esl.searchforfiles.service;

import com.esl.searchforfiles.configuration.FingerprintCalculator;
import com.esl.searchforfiles.configuration.logger.AppLogger;
import com.esl.searchforfiles.configuration.logger.LogDialog;
import com.esl.searchforfiles.database.DatabaseManager;
import com.esl.searchforfiles.ui.FileExplorerSwing;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Serviço responsável pelo monitoramento em tempo real do sistema de arquivos
 * <p>
 * Funcionalidades:
 * - Detecta criação de arquivos/pastas
 * - Detecta modificação de arquivos
 * - Detecta exclusão de arquivos/pastas
 * - Atualiza índice automaticamente
 * - Suporta monitoramento recursivo de diretórios
 * * Serviço de monitoramento usando Virtual Threads
 * * Cada evento de arquivo é processado em uma Virtual Thread separada
 * <p>
 * Utiliza WatchService do Java NIO para eficiência
 * <p>
 * MODIFICADO: Adiciona callback para auto-refresh
 *
 * @author Sistema de Busca
 */
public class MonitoringService {
    private static final long DELETE_WINDOW_MS = 2000; // janela de 2 segundos
    private final DatabaseManager dbManager;
    private final SearchService searchService;
    private final ExecutorService virtualExecutor;
    private final Map<String, Long> recentDeletes = new ConcurrentHashMap<>();
    private WatchService watchService;
    private Map<WatchKey, Path> watchKeys;
    private volatile boolean monitoring = false;
    private Thread monitorThread;
    // NOVO: Callback para notificar mudanças
    private FileChangeCallback fileChangeCallback;
    private  final FileExplorerSwing fileExplorerSwing;

    // Mapeia cada pasta-raiz monitorada → se foi registrada com subpastas.
    // Diferente de watchKeys (que mapeia WatchKey → diretório físico),
    // este mapa guarda a INTENÇÃO original de cada raiz escolhida pelo usuário.
    private final Map<Path, Boolean> monitoredRoots = new ConcurrentHashMap<>();

    public MonitoringService(DatabaseManager dbManager, SearchService searchService, FileExplorerSwing fileExplorerSwing) {
        this.dbManager = dbManager;
        this.searchService = searchService;
        this.fileExplorerSwing = fileExplorerSwing;
        // Virtual Thread Executor - pode lidar com milhares de eventos simultâneos!
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.watchKeys = new ConcurrentHashMap<>();
    }

    /**
     * Define callback para ser notificado quando arquivos mudarem
     * NOVO MÉTODO
     */
    public void setFileChangeCallback(FileChangeCallback callback) {
        this.fileChangeCallback = callback;
    }

    /**
     * Notifica callback sobre mudança no sistema de arquivos
     * NOVO MÉTODO
     */
    private void notifyFileChange() {
        if (fileChangeCallback != null) {
            try {
                fileChangeCallback.onFileChanged();
            } catch (Exception e) {
                System.err.println("⚠️ Erro ao notificar mudança: " + e.getMessage());
            }
        }
    }


    public void startMonitoring(String rootPath) throws IOException {
        boolean includeSubfolders;
        try {
            includeSubfolders = dbManager.getIncludeSubfoldersPolicy(rootPath);
        } catch (SQLException e) {
            includeSubfolders = false; // conservador
        }

        Path root = Paths.get(rootPath).toAbsolutePath().normalize();

        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Diretório não encontrado: " + rootPath);
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("O caminho não é um diretório: " + rootPath);
        }

        // NOVO: se essa raiz já está registrada, não faz nada (evita duplicar watchKeys)
        if (monitoredRoots.containsKey(root)) {
            System.out.println("️  Pasta já está sendo monitorada: " + root);
            return;
        }

        if (watchService == null) {
            watchService = FileSystems.getDefault().newWatchService();
        }

        // Registra esta raiz com sua própria política — independente de outras raízes já ativas
        monitoredRoots.put(root, includeSubfolders);
        registerDirectory(root, includeSubfolders);

        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║  MONITORAMENTO COM VIRTUAL THREADS ATIVO                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println(" Diretório: " + rootPath + " (subpastas: " + includeSubfolders + ")");
        System.out.println("  Observando: " + watchKeys.size() + " pasta(s) no total");

        // O loop de monitoramento só precisa rodar uma vez, cobrindo todas as raízes
        if (!monitoring) {
            monitoring = true;
            monitorThread = Thread.ofVirtual()
                    .name("FileMonitor-VirtualThread")
                    .start(this::monitorLoop); // não depende mais de um único rootPath
        }
    }

//    private void monitorLoop() {
//        System.out.println("🚀 Virtual Thread de monitoramento iniciada: " + Thread.currentThread());
//    }

    /**
     * Encontra a raiz monitorada que "governa" o diretório informado —
     * ou seja, a raiz mais específica (mais profunda) da qual o diretório
     * é descendente ou igual. Retorna null se nenhuma raiz cobre o diretório
     * (não deveria acontecer em uso normal, mas é tratado defensivamente).
     */
    private Path findGoverningRoot(Path dir) {
        Path best = null;
        for (Path root : monitoredRoots.keySet()) {
            if (dir.equals(root) || dir.startsWith(root)) {
                if (best == null || root.getNameCount() > best.getNameCount()) {
                    best = root;
                }
            }
        }
        return best;
    }

    /**
     * Verifica se a raiz que governa este diretório foi configurada
     * para incluir subpastas. Default conservador (false) se não encontrar.
     */
    private boolean isRecursiveForPath(Path dir) {
        Path root = findGoverningRoot(dir);
        return root != null && Boolean.TRUE.equals(monitoredRoots.get(root));
    }

    public void stopMonitoring() {
        if (!monitoring) {
            return;
        }

        System.out.println("\n Encerrando monitoramento...");
        monitoring = false;

        if (monitorThread != null) {
            try {
                monitorThread.join(2000);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("✓ Monitoramento encerrado");
    }

    /**
     * Loop principal de monitoramento
     * MODIFICADO: Notifica mudanças para auto-refresh
     */
    private void monitorLoop() {
        System.out.println("🚀 Virtual Thread de monitoramento iniciada: " + Thread.currentThread());

        while (monitoring) {
            try {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);

                if (key == null) {
                    continue;
                }

                Path dir = watchKeys.get(key);
                if (dir == null) {
                    key.reset();
                    continue;
                }

                boolean hasChanges = false; // NOVO: Flag para detectar mudanças

                // Processa eventos em Virtual Threads
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        System.out.println("⚠️ OVERFLOW - Alguns eventos foram perdidos");
                        hasChanges = true;
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();
                    Path child = dir.resolve(filename);

                    // Cada evento é processado em uma Virtual Thread separada
//                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
//                        hasChanges = true;
//                        Thread.ofVirtual().start(() -> handleFileCreated(child));
//
//                        if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
//                            try {
//                                registerDirectory(child);
//                            } catch (IOException e) {
//                                System.err.println("❌ Erro ao registrar diretório: " + child);
//                            }
//                        }
//
//                    }
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        hasChanges = true;
                        Thread.ofVirtual().start(() -> handleFileCreated(child));

                        if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                            // NOVO: só desce automaticamente se a raiz que governa esta pasta é recursiva
                            if (isRecursiveForPath(child)) {
                                try {
                                    registerDirectory(child, true);
                                } catch (IOException e) {
                                    System.err.println("❌ Erro ao registrar diretório: " + child);
                                }
                            } else {
                                System.out.println("⏭ Subpasta criada fora do escopo monitorado (raiz não-recursiva): " + child);
                            }
                        }
                    }
                    else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        hasChanges = true;
                        Thread.ofVirtual().start(() -> handleFileDeleted(child));

                    } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        hasChanges = true;
                        Thread.ofVirtual().start(() -> handleFileModified(child));
                    }
                }

                // NOVO: Notifica mudanças para auto-refresh
                if (hasChanges) {
                    notifyFileChange();
                }

                boolean valid = key.reset();
                if (!valid) {
                    watchKeys.remove(key);
                    System.out.println("📂 Diretório removido do monitoramento: " + dir);

                    if (watchKeys.isEmpty()) {
                        System.out.println("⚠️ Nenhum diretório para monitorar. Encerrando...");
                        monitoring = false;
                        break;
                    }
                }

            } catch (InterruptedException e) {
                System.out.println("⚠️ Monitoramento interrompido");
                break;
            } catch (Exception e) {
                System.err.println("❌ Erro no monitoramento: " + e.getMessage());
            }
        }

        System.out.println("🏁 Virtual Thread de monitoramento encerrada");
    }

//    private void registerDirectory(Path dir) throws IOException {
//        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
//            @Override
//            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
//                try {
//                    WatchKey key = dir.register(
//                            watchService,
//                            StandardWatchEventKinds.ENTRY_CREATE,
//                            StandardWatchEventKinds.ENTRY_DELETE,
//                            StandardWatchEventKinds.ENTRY_MODIFY
//                    );
//                    watchKeys.put(key, dir);
//                } catch (IOException e) {
//                    // Ignora diretórios sem permissão
//                }
//                return FileVisitResult.CONTINUE;
//            }
//
//            @Override
//            public FileVisitResult visitFileFailed(Path file, IOException exc) {
//                return FileVisitResult.CONTINUE;
//            }
//        });
//    }
    /**
     * Registra a pasta para monitoramento.
     * @param recursive se true, registra também todas as subpastas;
     *                  se false, registra APENAS o diretório informado,
     *                  respeitando a mesma política usada na indexação.
     */
    private void registerDirectory(Path dir, boolean recursive) throws IOException {
        if (!recursive) {
            try {
                WatchKey key = dir.register(
                        watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY
                );
                watchKeys.put(key, dir);
            } catch (IOException e) {
                // Ignora pasta sem permissão
            }
            return;
        }

        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                try {
                    WatchKey key = d.register(
                            watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_DELETE,
                            StandardWatchEventKinds.ENTRY_MODIFY
                    );
                    watchKeys.put(key, d);
                } catch (IOException e) {
                    // Ignora diretório sem permissão
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
    }
    /**
     * Handler de arquivo criado
     * CORRIGIDO: Validações adicionais
     */
// Substitua handleFileCreated():
    private void handleFileCreated(Path file) {
        try {
            if (file == null) return;
            if (!Files.exists(file)) return;

            String absPath = file.toAbsolutePath().toString();

            // NOVO: pequeno delay para aguardar possível DELETE do mesmo nome
            // (movimentação entre pastas monitoradas gera DELETE + CREATE)
            Thread.sleep(300);

            BasicFileAttributes attrs =
                    Files.readAttributes(file, BasicFileAttributes.class);
            if (attrs == null) return;

            // NOVO: limpa entrada de delete recente para este path
            recentDeletes.remove(absPath);

            // NOVO: verifica se há um arquivo com mesmo fingerprint
            // recém-deletado de outra pasta (movimentação entre drives)
            String fp = FingerprintCalculator.calculate(file, attrs);
            cleanupStaleEntryByFingerprint(fp, absPath);

            dbManager.indexFile(file, attrs);
            searchService.clearCache();

            String type = attrs.isDirectory() ? "📁 Pasta" : "📄 Arquivo";
            Path fileName = file.getFileName();
            String name   = fileName != null ? fileName.toString() : absPath;
            System.out.println("➕ " + type + " criado: " + name
                    + " [VThread: " + Thread.currentThread().threadId() + "]");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("⚠️ Erro ao indexar arquivo criado: " + e.getMessage());
        }
    }

    /**
     * Remove entradas antigas do índice com mesmo fingerprint
     * mas path diferente — evita duplicatas ao mover entre drives.
     */
    private void cleanupStaleEntryByFingerprint(String fingerprint,
                                                String newPath) {
        if (fingerprint == null) return;
        try {
            String sql = """
            SELECT path FROM file_index
            WHERE fingerprint = ? AND path != ?
        """;
            try (PreparedStatement p =
                         dbManager.getConnection().prepareStatement(sql)) {
                p.setString(1, fingerprint);
                p.setString(2, newPath);
                try (ResultSet rs = p.executeQuery()) {
                    while (rs.next()) {
                        String stalePath = rs.getString("path");
                        // Só remove se o arquivo não existe mais no path antigo
                        if (!Files.exists(Paths.get(stalePath))) {
                            dbManager.deleteFile(stalePath);
                            System.out.println("🧹 Entrada obsoleta removida: "
                                    + stalePath);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Erro ao limpar entrada obsoleta: "
                    + e.getMessage());
        }
    }
    // Substitua handleFileDeleted():
    private void handleFileDeleted(Path file) {
        try {
            if (file == null) return;

            String absPath = file.toAbsolutePath().toString();

            // NOVO: registra o momento da deleção para uso no CREATE
            recentDeletes.put(absPath, System.currentTimeMillis());

            dbManager.deleteFile(absPath);
            searchService.clearCache();

            Path fileName = file.getFileName();
            String name   = fileName != null ? fileName.toString() : absPath;
            System.out.println("➖ Arquivo deletado: " + name
                    + " [VThread: " + Thread.currentThread().threadId() + "]");
        } catch (Exception e) {
            System.err.println("⚠️ Erro ao remover do índice: " + e.getMessage());
        }
    }
    /**
     * Handler de arquivo modificado
     * CORRIGIDO: Validações adicionais
     */
    private void handleFileModified(Path file) {
        try {
            // VALIDAÇÃO: Path não pode ser nulo
            if (file == null) {
                return;
            }

            if (Files.exists(file) && Files.isRegularFile(file)) {
                BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);

                if (attrs != null) {
                    dbManager.indexFile(file, attrs);
                    searchService.clearCache();

                    Path fileName = file.getFileName();
                    String name = (fileName != null) ? fileName.toString() : file.toString();

//                    LogDialog.show(fileExplorerSwing);
//                    AppLogger.get().info("✏️  Arquivo modificado: " + name +
//                            " [VThread: " + Thread.currentThread().threadId() + "]");


                    System.out.println("✏️  Arquivo modificado: " + name +
                            " [VThread: " + Thread.currentThread().threadId() + "]");
                }
            }
        } catch (Exception e) {
            // Erro silencioso
        }
    }

    public void shutdown() {
        stopMonitoring();

        if (virtualExecutor != null && !virtualExecutor.isShutdown()) {
            virtualExecutor.shutdown();
            try {
                if (!virtualExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    virtualExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                virtualExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException e) {
            System.err.println("Erro ao fechar WatchService: " + e.getMessage());
        }

        watchKeys.clear();
    }

    /**
     * Remove o monitoramento de uma pasta e de todas as suas subpastas
     * atualmente monitoradas. Deve ser chamado quando uma pasta é
     * removida do índice, para evitar eventos órfãos (ENTRY_MODIFY/DELETE)
     * de uma pasta que não está mais indexada.
     *
     * @param folderPath pasta a parar de monitorar
     * @return número de WatchKeys cancelados
     */
    public int unregisterDirectory(String folderPath) {
        Path target = Paths.get(folderPath).toAbsolutePath().normalize();

        List<WatchKey> keysToRemove = new ArrayList<>();
        for (Map.Entry<WatchKey, Path> entry : watchKeys.entrySet()) {
            Path watchedPath = entry.getValue();
            if (watchedPath.equals(target) || watchedPath.startsWith(target)) {
                keysToRemove.add(entry.getKey());
            }
        }

        for (WatchKey key : keysToRemove) {
            key.cancel();
            watchKeys.remove(key);
        }

        recentDeletes.keySet().removeIf(path -> Paths.get(path).startsWith(target));

        // NOVO: remove esta raiz do controle de políticas
        monitoredRoots.remove(target);

        if (!keysToRemove.isEmpty()) {
            System.out.println("🛑 Monitoramento removido: " + target
                    + " (" + keysToRemove.size() + " diretório(s) desregistrado(s))");
        }

        if (watchKeys.isEmpty() && monitoring) {
            System.out.println("⚠️ Nenhum diretório restante para monitorar. Encerrando monitoramento...");
            stopMonitoring();
        }

        return keysToRemove.size();
    }

    public boolean isMonitoring() {
        return monitoring;
    }

    public int getMonitoredDirectories() {
        return watchKeys.size();
    }

    /**
     * Interface para callback de mudanças
     * NOVA INTERFACE
     */
    public interface FileChangeCallback {
        void onFileChanged();
    }

    public SearchService getSearchService() {
        return searchService;
    }
}
