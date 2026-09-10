package com.esl.searchforfiles.service;

import com.esl.searchforfiles.database.DatabaseManager;
import com.esl.searchforfiles.ui.SearchController;


import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serviço responsável pela indexação de arquivos
 *
 * Funcionalidades:
 *  * Vantagens dos Virtual Threads:
 *  * - Usando Virtual Threads (Java 25+)
 *  * - Milhares de threads simultâneas com baixo overhead
 *  * - Melhor escalabilidade que threads tradicionais
 *  * - Menor uso de memória
 *  * - Performance superior em I/O bound operations
 * - Varredura recursiva de diretórios
 * - Indexação seletiva (com ou sem subpastas)
 * - Tratamento robusto de erros
 * - Estatísticas de performance
 *
 * @author Sistema de Busca
 */
public class IndexService {
    private final DatabaseManager dbManager;
    private final ExecutorService virtualExecutor;
    private final IndexFilterService indexFilterService;
    private static final int REPORT_INTERVAL = 1000;

    /**
     * Construtor - Cria ExecutorService com Virtual Threads
     */
    public IndexService(DatabaseManager dbManager, IndexFilterService indexFilterService) {
        this.dbManager = dbManager;
        this.indexFilterService = indexFilterService;
        // Virtual Thread Executor - Pode criar milhares de threads sem problema!
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Callback de log para reportar progresso textual da indexação.
     * Desacopla o IndexService de qualquer framework de UI —
     * quem consome decide o que fazer com cada linha (JTextArea, console, etc).
     */
    @FunctionalInterface
    public interface IndexLogListener {
        void onLog(String message);
    }

    /**
     * Loga uma mensagem tanto no console quanto no listener, se presente.
     * Centraliza o que antes era só System.out.println espalhado pelo método.
     */
    private void log(IndexLogListener listener, String message) {
        System.out.println(message);
        if (listener != null) {
            listener.onLog(message);
        }
    }

//
//    public void indexDirectory(String rootPath, IndexLogListener logListener) throws IOException, SQLException {
//        Path root = Paths.get(rootPath);
//
//        if (!Files.exists(root)) {
//            throw new IllegalArgumentException("Diretório não encontrado: " + rootPath);
//        }
//        if (!Files.isDirectory(root)) {
//            throw new IllegalArgumentException("O caminho não é um diretório: " + rootPath);
//        }
//
//        log(logListener, "╔════════════════════════════════════════════════════════════════╗");
//        log(logListener, "║  INDEXAÇÃO COM VIRTUAL THREADS                                 ║");
//        log(logListener, "╚════════════════════════════════════════════════════════════════╝");
//        log(logListener, " Diretório: " + rootPath);
//        log(logListener, " Usando Virtual Threads (escalabilidade infinita!)");
//
//        long startTime = System.currentTimeMillis();
//        AtomicInteger fileCount = new AtomicInteger(0);
//        AtomicInteger errorCount = new AtomicInteger(0);
//        AtomicInteger dirCount = new AtomicInteger(0);
//
//        List<CompletableFuture<Void>> futures = new ArrayList<>();
//
//        Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
//                new SimpleFileVisitor<Path>() {
//                    @Override
//                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
//                        if (!indexFilterService.shouldIndex(file)) return FileVisitResult.CONTINUE;
//                        if (Objects.isNull(file) || Objects.isNull(attrs)) {
//                            errorCount.incrementAndGet();
//                            return FileVisitResult.CONTINUE;
//                        }
//
//                        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
//                            try {
//                                if (isValidPath(file)) {
//                                    dbManager.indexFile(file, attrs);
//                                    int count = fileCount.incrementAndGet();
//
//                                    // NOVO: log periódico vai para o listener também
//                                    if (count % REPORT_INTERVAL == 0) {
//                                        long elapsed = System.currentTimeMillis() - startTime;
//                                        double rate = count / (elapsed / 1000.0);
//                                        log(logListener, String.format(" %,d arquivos indexados (%.0f arq/s)",
//                                                count, rate));
//                                    }
//                                } else {
//                                    errorCount.incrementAndGet();
//                                }
//                            } catch (SQLException _) {
//                                errorCount.incrementAndGet();
//                            } catch (Exception e) {
//                                errorCount.incrementAndGet();
//                                log(logListener, "  Erro inesperado: " + e.getMessage());
//                            }
//                        }, virtualExecutor);
//
//                        futures.add(future);
//                        return FileVisitResult.CONTINUE;
//                    }
//
//                    @Override
//                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
//                        dirCount.incrementAndGet();
//
//                        if (!indexFilterService.shouldIndex(dir)) {
//                            log(logListener, "⏭ Pasta ignorada pelo filtro: " + dir);
//                            return FileVisitResult.SKIP_SUBTREE;
//                        }
//                        if (Objects.isNull(dir) || Objects.isNull(attrs)) {
//                            errorCount.incrementAndGet();
//                            return FileVisitResult.CONTINUE;
//                        }
//
//                        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
//                            try {
//                                if (isValidPath(dir)) {
//                                    dbManager.indexFile(dir, attrs);
//                                    fileCount.incrementAndGet();
//                                } else {
//                                    errorCount.incrementAndGet();
//                                }
//                            } catch (Exception _) {
//                                errorCount.incrementAndGet();
//                            }
//                        }, virtualExecutor);
//
//                        futures.add(future);
//                        return FileVisitResult.CONTINUE;
//                    }
//
//                    @Override
//                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
//                        errorCount.incrementAndGet();
//                        if (Objects.nonNull(exc) && !(exc instanceof AccessDeniedException)) {
//                            log(logListener, "⚠️  Falha ao acessar: " +
//                                    (Objects.nonNull(file) ? file : "caminho inválido") + " - " + exc.getMessage());
//                        }
//                        return FileVisitResult.CONTINUE;
//                    }
//                });
//
//        log(logListener, "\n⏳ Aguardando conclusão de " + futures.size() + " Virtual Threads...");
//
//        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
//        try {
//            allOf.join();
//        } catch (Exception e) {
//            log(logListener, "❌ Erro durante indexação: " + e.getMessage());
//        }
//
//        log(logListener, "\n🔧 Otimizando banco de dados...");
//        dbManager.compactDatabase();
//
//        long elapsed = System.currentTimeMillis() - startTime;
//        double rate = fileCount.get() / (elapsed / 1000.0);
//
//        log(logListener, "\n╔════════════════════════════════════════════════════════════════╗");
//        log(logListener, "║  INDEXAÇÃO CONCLUÍDA (Virtual Threads)                         ║");
//        log(logListener, "╚════════════════════════════════════════════════════════════════╝");
//        log(logListener, String.format("✓ Arquivos indexados: %,d", fileCount.get()));
//        log(logListener, String.format("✓ Diretórios varridos: %,d", dirCount.get()));
//        log(logListener, String.format("✓ Tempo decorrido: %.2f segundos", elapsed / 1000.0));
//        log(logListener, String.format("✓ Taxa média: %.0f arquivos/segundo", rate));
//        log(logListener, String.format("  Erros encontrados: %,d", errorCount.get()));
//    }

    public void indexFolder(String folderPath, boolean includeSubfolders,
                            IndexLogListener logListener,
                            SearchController.IndexCancellationToken cancelToken) throws IOException, SQLException {
        Path folder = Paths.get(folderPath);

        if (!Files.exists(folder)) {
            throw new IllegalArgumentException("Pasta não encontrada: " + folderPath);
        }
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("O caminho não é uma pasta: " + folderPath);
        }

        log(logListener, "╔════════════════════════════════════════════════════════════════╗");
        log(logListener, "║  INDEXANDO PASTA (Virtual Threads)                             ║");
        log(logListener, "╚════════════════════════════════════════════════════════════════╝");
        log(logListener, " Pasta: " + folderPath);
        log(logListener, " Incluir subpastas: " + (includeSubfolders ? "SIM" : "NÃO"));

        if (includeSubfolders) {
            indexDirectory(folderPath, logListener, cancelToken);
        } else {
            indexFolderNonRecursive(folder, logListener, cancelToken);
        }

        // NOVO: só registra a política se a indexação não foi cancelada no meio
        // (uma indexação parcial não deveria ser marcada como "raiz completa")
        if (cancelToken == null || !cancelToken.isCancelled()) {
            dbManager.registerIndexedFolder(folderPath, includeSubfolders);
        } else {
            log(logListener, "⚠️ Indexação cancelada — política não registrada, pasta permanece como não-indexada.");
        }
    }

    public void indexDirectory(String rootPath, IndexLogListener logListener,
                               SearchController.IndexCancellationToken cancelToken) throws IOException, SQLException {
        Path root = Paths.get(rootPath);

        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Diretório não encontrado: " + rootPath);
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("O caminho não é um diretório: " + rootPath);
        }

        log(logListener, " Diretório: " + rootPath);
        log(logListener, " Usando Virtual Threads (escalabilidade infinita!)");

        long startTime = System.currentTimeMillis();
        AtomicInteger fileCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger dirCount = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        // NOVO: verifica cancelamento ANTES de descobrir mais arquivos
                        if (isCancelled(cancelToken)) {
                            return FileVisitResult.TERMINATE;
                        }

                        if (!indexFilterService.shouldIndex(file)) return FileVisitResult.CONTINUE;
                        if (Objects.isNull(file) || Objects.isNull(attrs)) {
                            errorCount.incrementAndGet();
                            return FileVisitResult.CONTINUE;
                        }

                        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                            // NOVO: verifica de novo aqui — a tarefa pode ter ficado
                            // na fila do executor por um tempo antes de rodar
                            if (isCancelled(cancelToken)) return;

                            try {
                                if (isValidPath(file)) {
                                    dbManager.indexFile(file, attrs);
                                    int count = fileCount.incrementAndGet();

                                    if (count % REPORT_INTERVAL == 0) {
                                        long elapsed = System.currentTimeMillis() - startTime;
                                        double rate = count / (elapsed / 1000.0);
                                        log(logListener, String.format(" %,d arquivos indexados (%.0f arq/s)",
                                                count, rate));
                                    }
                                } else {
                                    errorCount.incrementAndGet();
                                }
                            } catch (SQLException _) {
                                errorCount.incrementAndGet();
                            } catch (Exception e) {
                                errorCount.incrementAndGet();
                                log(logListener, "  Erro inesperado: " + e.getMessage());
                            }
                        }, virtualExecutor);

                        futures.add(future);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        // NOVO: checagem também ao entrar em cada diretório —
                        // é o ponto mais cedo possível para interromper a descida recursiva
                        if (isCancelled(cancelToken)) {
                            return FileVisitResult.TERMINATE;
                        }

                        dirCount.incrementAndGet();

                        if (!indexFilterService.shouldIndex(dir)) {
                            log(logListener, "⏭ Pasta ignorada pelo filtro: " + dir);
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        if (Objects.isNull(dir) || Objects.isNull(attrs)) {
                            errorCount.incrementAndGet();
                            return FileVisitResult.CONTINUE;
                        }

                        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                            if (isCancelled(cancelToken)) return;
                            try {
                                if (isValidPath(dir)) {
                                    dbManager.indexFile(dir, attrs);
                                    fileCount.incrementAndGet();
                                } else {
                                    errorCount.incrementAndGet();
                                }
                            } catch (Exception _) {
                                errorCount.incrementAndGet();
                            }
                        }, virtualExecutor);

                        futures.add(future);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        errorCount.incrementAndGet();
                        if (Objects.nonNull(exc) && !(exc instanceof AccessDeniedException)) {
                            log(logListener, "⚠️  Falha ao acessar: " +
                                    (Objects.nonNull(file) ? file : "caminho inválido") + " - " + exc.getMessage());
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });

        log(logListener, "\n⏳ Aguardando conclusão de " + futures.size() + " Virtual Threads...");

        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            allOf.join();
        } catch (Exception e) {
            log(logListener, "❌ Erro durante indexação: " + e.getMessage());
        }

        long elapsed = System.currentTimeMillis() - startTime;
        double rate = fileCount.get() / (elapsed / 1000.0);

        if (isCancelled(cancelToken)) {
            log(logListener, "\n🛑 INDEXAÇÃO CANCELADA PELO USUÁRIO");
            log(logListener, String.format("✓ Arquivos indexados até o cancelamento: %,d", fileCount.get()));
        } else {
            log(logListener, "\n🔧 Otimizando banco de dados...");
            dbManager.compactDatabase();

            log(logListener, "\n╔════════════════════════════════════════════════════════════════╗");
            log(logListener, "║  INDEXAÇÃO CONCLUÍDA (Virtual Threads)                         ║");
            log(logListener, "╚════════════════════════════════════════════════════════════════╝");
            log(logListener, String.format("✓ Arquivos indexados: %,d", fileCount.get()));
            log(logListener, String.format("✓ Diretórios varridos: %,d", dirCount.get()));
            log(logListener, String.format("✓ Tempo decorrido: %.2f segundos", elapsed / 1000.0));
            log(logListener, String.format("✓ Taxa média: %.0f arquivos/segundo", rate));
            log(logListener, String.format("  Erros encontrados: %,d", errorCount.get()));
        }
    }

    /**
     * Helper null-safe para checar o token — permite continuar aceitando
     * chamadas sem cancelamento (token == null) sem quebrar código existente.
     */
    private boolean isCancelled(SearchController.IndexCancellationToken token) {
        return token != null && token.isCancelled();
    }



//    public void indexFolder(String folderPath, boolean includeSubfolders,
//                            IndexLogListener logListener) throws IOException, SQLException {
//        Path folder = Paths.get(folderPath);
//
//        if (!Files.exists(folder)) {
//            throw new IllegalArgumentException("Pasta não encontrada: " + folderPath);
//        }
//        if (!Files.isDirectory(folder)) {
//            throw new IllegalArgumentException("O caminho não é uma pasta: " + folderPath);
//        }
//
//        log(logListener, "📊 Indexando: " + folderPath + " (subpastas: " + includeSubfolders + ")");
//
//        if (includeSubfolders) {
//            indexDirectory(folderPath, logListener);
//        } else {
//            indexFolderNonRecursive(folder, logListener);
//        }
//
//        dbManager.registerIndexedFolder(folderPath, includeSubfolders);
//    }

    private void indexFolderNonRecursive(Path folder, IndexLogListener logListener,
                                         SearchController.IndexCancellationToken cancelToken) throws IOException {
        long startTime = System.currentTimeMillis();
        AtomicInteger fileCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path entry : stream) {
                if (isCancelled(cancelToken)) break; // para de enfileirar novas tarefas

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    if (isCancelled(cancelToken)) return;
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                        dbManager.indexFile(entry, attrs);
                        fileCount.incrementAndGet();
                    } catch (SQLException | IOException _) {
                        errorCount.incrementAndGet();
                    }
                }, virtualExecutor);
                futures.add(future);
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long elapsed = System.currentTimeMillis() - startTime;
        if (isCancelled(cancelToken)) {
            log(logListener, "🛑 Indexação cancelada — " + fileCount.get() + " arquivos indexados até então");
        } else {
            log(logListener, String.format("✓ Arquivos indexados: %,d", fileCount.get()));
            log(logListener, String.format("✓ Tempo decorrido: %.2f segundos", elapsed / 1000.0));
            log(logListener, String.format("⚠️  Erros encontrados: %,d", errorCount.get()));
        }
    }

    /**
     * Valida se um Path é seguro para indexar
     * NOVO MÉTODO
     */
    private boolean isValidPath(Path path) {
        if (Objects.isNull(path)) {
            return false;
        }

        try {
            // Tenta obter informações básicas
            String pathStr = path.toAbsolutePath().toString();

            // Ignora caminhos vazios
            if (Objects.isNull(pathStr) || pathStr.isEmpty()) {
                return false;
            }

            // Ignora arquivos de sistema do Windows (opcional)
            String lower = pathStr.toLowerCase();
            if (lower.contains("$recycle.bin") ||
                    lower.contains("system volume information") ||
                    lower.contains("hiberfil.sys") ||
                    lower.contains("pagefile.sys") ||
                    lower.contains("swapfile.sys")) {
                return false;
            }

            return true;

        } catch (Exception _) {
            return false;
        }
    }
    /**
     * Indexa pasta com Virtual Threads
     */
//    public void indexFolder(String folderPath, boolean includeSubfolders,IndexLogListener logListener) throws IOException, SQLException {
//        Path folder = Paths.get(folderPath);
//
//        if (!Files.exists(folder)) {
//            throw new IllegalArgumentException("Pasta não encontrada: " + folderPath);
//        }
//
//        if (!Files.isDirectory(folder)) {
//            throw new IllegalArgumentException("O caminho não é uma pasta: " + folderPath);
//        }
//
//        System.out.println("╔════════════════════════════════════════════════════════════════╗");
//        System.out.println("║  INDEXANDO PASTA (Virtual Threads)                             ║");
//        System.out.println("╚════════════════════════════════════════════════════════════════╝");
//        System.out.println(" Pasta: " + folderPath);
//        System.out.println(" Incluir subpastas: " + (includeSubfolders ? "SIM" : "NÃO"));
//        System.out.println("");
//
//        if (includeSubfolders) {
//            indexDirectory(folderPath,logListener);
//        } else {
//            indexFolderNonRecursive(folder,logListener);
//        }
//
//        // NOVO: registra a política usada nesta indexação
//        dbManager.registerIndexedFolder(folderPath, includeSubfolders);
//    }

    /**
     * Indexa apenas nível atual usando Virtual Threads
     */
//    private void indexFolderNonRecursive(Path folder) throws IOException {
//        long startTime = System.currentTimeMillis();
//        AtomicInteger fileCount = new AtomicInteger(0);
//        AtomicInteger errorCount = new AtomicInteger(0);
//
//        List<CompletableFuture<Void>> futures = new ArrayList<>();
//
//        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
//            for (Path entry : stream) {
//                // Cada arquivo em uma Virtual Thread
//                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
//                    try {
//                        BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
//                        dbManager.indexFile(entry, attrs);
//                        fileCount.incrementAndGet();
//                    } catch (SQLException | IOException _) {
//                        errorCount.incrementAndGet();
//                    }
//                }, virtualExecutor);
//
//                futures.add(future);
//            }
//        }
//
//        // Aguarda todas as Virtual Threads
//        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
//
//        long elapsed = System.currentTimeMillis() - startTime;
//
//        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
//        System.out.println("║  INDEXAÇÃO CONCLUÍDA                                           ║");
//        System.out.println("╚════════════════════════════════════════════════════════════════╝");
//        System.out.printf("✓ Arquivos indexados: %,d\n", fileCount.get());
//        System.out.printf("✓ Virtual Threads: %,d\n", futures.size());
//        System.out.printf("✓ Tempo decorrido: %.2f segundos\n", elapsed / 1000.0);
//        System.out.printf("⚠️  Erros encontrados: %,d\n", errorCount.get());
//        System.out.println("");
//    }
    /**
     * Shutdown - Virtual Threads são automaticamente gerenciadas
     */
    public void shutdown() {
        if (virtualExecutor != null && !virtualExecutor.isShutdown()) {
            virtualExecutor.shutdown();
            try {
                if (!virtualExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    virtualExecutor.shutdownNow();
                }
            } catch (InterruptedException _) {
                virtualExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
