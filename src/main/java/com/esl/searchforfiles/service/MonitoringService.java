package com.esl.searchforfiles.service;

import com.esl.searchforfiles.configuration.FingerprintCalculator;
import com.esl.searchforfiles.database.DatabaseManager;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

    public MonitoringService(DatabaseManager dbManager, SearchService searchService) {
        this.dbManager = dbManager;
        this.searchService = searchService;
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
        if (monitoring) {
            System.out.println("️  Monitoramento já está ativo");
            return;
        }

        if (watchService == null) {
            watchService = FileSystems.getDefault().newWatchService();
        }

        Path root = Paths.get(rootPath);

        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Diretório não encontrado: " + rootPath);
        }

        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("O caminho não é um diretório: " + rootPath);
        }

        monitoring = true;

        System.out.println(" Registrando diretórios para monitoramento...");
        registerDirectory(root);
        System.out.println("✓ " + watchKeys.size() + " diretórios registrados");

        // Monitor thread agora é uma Virtual Thread!
        monitorThread = Thread.ofVirtual()
                .name("FileMonitor-VirtualThread")
                .start(() -> monitorLoop(rootPath));

        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║  MONITORAMENTO COM VIRTUAL THREADS ATIVO                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println(" Diretório: " + rootPath);
        System.out.println("  Observando: " + watchKeys.size() + " pastas");
        System.out.println(" Usando Virtual Threads para processamento");
        System.out.println(" Eventos: CREATE, MODIFY, DELETE");
        System.out.println("");
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
    private void monitorLoop(String rootPath) {
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
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        hasChanges = true;
                        Thread.ofVirtual().start(() -> handleFileCreated(child));

                        if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                            try {
                                registerDirectory(child);
                            } catch (IOException e) {
                                System.err.println("❌ Erro ao registrar diretório: " + child);
                            }
                        }

                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
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

    private void registerDirectory(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                try {
                    WatchKey key = dir.register(
                            watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_DELETE,
                            StandardWatchEventKinds.ENTRY_MODIFY
                    );
                    watchKeys.put(key, dir);
                } catch (IOException e) {
                    // Ignora diretórios sem permissão
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
//    private void handleFileCreated(Path file) {
//        try {
//            // VALIDAÇÃO: Path não pode ser nulo
//            if (file == null) {
//                return;
//            }
//
//            // VALIDAÇÃO: Arquivo deve existir
//            if (Files.exists(file)) {
//                BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
//
//                // VALIDAÇÃO: Atributos não podem ser nulos
//                if (attrs != null) {
//                    dbManager.indexFile(file, attrs);
//                    searchService.clearCache();
//
//                    String type = attrs.isDirectory() ? "📁 Pasta" : "📄 Arquivo";
//                    Path fileName = file.getFileName();
//                    String name = (fileName != null) ? fileName.toString() : file.toString();
//
//                    System.out.println("➕ " + type + " criado: " + name +
//                            " [VThread: " + Thread.currentThread().threadId() + "]");
//                }
//            }
//        } catch (Exception e) {
//            // Erro silencioso - comum durante monitoramento
//            System.err.println("⚠️  Erro ao indexar arquivo criado: " + e.getMessage());
//        }
//    }


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

//    /**
//     * Handler de arquivo deletado
//     * CORRIGIDO: Validações adicionais
//     */
//    private void handleFileDeleted(Path file) {
//        try {
//            // VALIDAÇÃO: Path não pode ser nulo
//            if (file == null) {
//                return;
//            }
//
//            dbManager.deleteFile(file.toAbsolutePath().toString());
//            searchService.clearCache();
//
//            Path fileName = file.getFileName();
//            String name = (fileName != null) ? fileName.toString() : file.toString();
//
//            System.out.println("➖ Arquivo deletado: " + name +
//                    " [VThread: " + Thread.currentThread().threadId() + "]");
//        } catch (Exception e) {
//            System.err.println("⚠️  Erro ao remover do índice: " + e.getMessage());
//        }
//    }

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
}
