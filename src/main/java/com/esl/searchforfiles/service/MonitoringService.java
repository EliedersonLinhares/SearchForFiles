package com.esl.searchforfiles.service;

import com.esl.searchforfiles.database.DatabaseManager;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Serviço responsável pelo monitoramento em tempo real do sistema de arquivos
 *
 * Funcionalidades:
 * - Detecta criação de arquivos/pastas
 * - Detecta modificação de arquivos
 * - Detecta exclusão de arquivos/pastas
 * - Atualiza índice automaticamente
 * - Suporta monitoramento recursivo de diretórios
 *  * Serviço de monitoramento usando Virtual Threads
 *  * Cada evento de arquivo é processado em uma Virtual Thread separada
 *
 * Utiliza WatchService do Java NIO para eficiência
 *
 * @author Sistema de Busca
 */
public class MonitoringService {
    private final DatabaseManager dbManager;
    private final SearchService searchService;
    private final ExecutorService virtualExecutor;
    private WatchService watchService;
    private Map<WatchKey, Path> watchKeys;
    private volatile boolean monitoring = false;
    private Thread monitorThread;

    public MonitoringService(DatabaseManager dbManager, SearchService searchService) {
        this.dbManager = dbManager;
        this.searchService = searchService;
        // Virtual Thread Executor - pode lidar com milhares de eventos simultâneos!
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.watchKeys = new ConcurrentHashMap<>();
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
     * Roda em uma Virtual Thread
     */
    private void monitorLoop(String rootPath) {
        System.out.println(" Virtual Thread de monitoramento iniciada: " + Thread.currentThread());

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

                // Processa eventos em Virtual Threads
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        System.out.println("⚠️  OVERFLOW - Alguns eventos foram perdidos");
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();
                    Path child = dir.resolve(filename);

                    // Cada evento é processado em uma Virtual Thread separada!
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        // Virtual Thread para criação
                        Thread.ofVirtual().start(() -> handleFileCreated(child));

                        if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                            try {
                                registerDirectory(child);
                            } catch (IOException e) {
                                System.err.println("❌ Erro ao registrar diretório: " + child);
                            }
                        }

                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        // Virtual Thread para deleção
                        Thread.ofVirtual().start(() -> handleFileDeleted(child));

                    } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        // Virtual Thread para modificação
                        Thread.ofVirtual().start(() -> handleFileModified(child));
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    watchKeys.remove(key);
                    System.out.println("  Diretório removido do monitoramento: " + dir);

                    if (watchKeys.isEmpty()) {
                        System.out.println("  Nenhum diretório para monitorar. Encerrando...");
                        monitoring = false;
                        break;
                    }
                }

            } catch (InterruptedException e) {
                System.out.println("  Monitoramento interrompido");
                break;
            } catch (Exception e) {
                System.err.println(" Erro no monitoramento: " + e.getMessage());
            }
        }

        System.out.println(" Virtual Thread de monitoramento encerrada");
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

//    /**
//     * Handlers executam em Virtual Threads
//     */
//    private void handleFileCreated(Path file) {
//        try {
//            if (Files.exists(file)) {
//                BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
//                dbManager.indexFile(file, attrs);
//                searchService.clearCache();
//
//                String type = attrs.isDirectory() ? " Pasta" : " Arquivo";
//                System.out.println("➕ " + type + " criado: " + file.getFileName() +
//                        " [VThread: " + Thread.currentThread().threadId() + "]");
//            }
//        } catch (Exception e) {
//            // Erro silencioso
//        }
//    }
    /**
     * Handler de arquivo criado
     * CORRIGIDO: Validações adicionais
     */
    private void handleFileCreated(Path file) {
        try {
            // VALIDAÇÃO: Path não pode ser nulo
            if (file == null) {
                return;
            }

            // VALIDAÇÃO: Arquivo deve existir
            if (Files.exists(file)) {
                BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);

                // VALIDAÇÃO: Atributos não podem ser nulos
                if (attrs != null) {
                    dbManager.indexFile(file, attrs);
                    searchService.clearCache();

                    String type = attrs.isDirectory() ? "📁 Pasta" : "📄 Arquivo";
                    Path fileName = file.getFileName();
                    String name = (fileName != null) ? fileName.toString() : file.toString();

                    System.out.println("➕ " + type + " criado: " + name +
                            " [VThread: " + Thread.currentThread().threadId() + "]");
                }
            }
        } catch (Exception e) {
            // Erro silencioso - comum durante monitoramento
            System.err.println("⚠️  Erro ao indexar arquivo criado: " + e.getMessage());
        }
    }
//    private void handleFileDeleted(Path file) {
//        try {
//            dbManager.deleteFile(file.toAbsolutePath().toString());
//            searchService.clearCache();
//            System.out.println("➖ Arquivo deletado: " + file.getFileName() +
//                    " [VThread: " + Thread.currentThread().threadId() + "]");
//        } catch (SQLException e) {
//            System.err.println(" Erro ao remover do índice: " + file.getFileName());
//        }
//    }
    /**
     * Handler de arquivo deletado
     * CORRIGIDO: Validações adicionais
     */
    private void handleFileDeleted(Path file) {
        try {
            // VALIDAÇÃO: Path não pode ser nulo
            if (file == null) {
                return;
            }

            dbManager.deleteFile(file.toAbsolutePath().toString());
            searchService.clearCache();

            Path fileName = file.getFileName();
            String name = (fileName != null) ? fileName.toString() : file.toString();

            System.out.println("➖ Arquivo deletado: " + name +
                    " [VThread: " + Thread.currentThread().threadId() + "]");
        } catch (Exception e) {
            System.err.println("⚠️  Erro ao remover do índice: " + e.getMessage());
        }
    }
//    private void handleFileModified(Path file) {
//        try {
//            if (Files.exists(file) && Files.isRegularFile(file)) {
//                BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
//                dbManager.indexFile(file, attrs);
//                searchService.clearCache();
//                System.out.println("  Arquivo modificado: " + file.getFileName() +
//                        " [VThread: " + Thread.currentThread().threadId() + "]");
//            }
//        } catch (Exception e) {
//            // Erro silencioso
//        }
//    }
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
}
