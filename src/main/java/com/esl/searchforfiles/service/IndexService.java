package com.esl.searchforfiles.service;

import com.esl.searchforfiles.database.DatabaseManager;


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
    private static final int REPORT_INTERVAL = 1000;

    /**
     * Construtor - Cria ExecutorService com Virtual Threads
     */
    public IndexService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        // Virtual Thread Executor - Pode criar milhares de threads sem problema!
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Indexa um diretório usando Virtual Threads
     * Cada arquivo é processado em uma Virtual Thread separada
     */
    public void indexDirectory(String rootPath) throws IOException, InterruptedException, SQLException {
        Path root = Paths.get(rootPath);

        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Diretório não encontrado: " + rootPath);
        }

        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("O caminho não é um diretório: " + rootPath);
        }

        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║  INDEXAÇÃO COM VIRTUAL THREADS (Java 25)                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println("📁 Diretório: " + rootPath);
        System.out.println("🚀 Usando Virtual Threads (escalabilidade infinita!)");
        System.out.println("");

        long startTime = System.currentTimeMillis();
        AtomicInteger fileCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger dirCount = new AtomicInteger(0);

        // Lista para armazenar todos os Futures
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Varre o sistema de arquivos e submete cada arquivo para uma Virtual Thread
        Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        // Submete para Virtual Thread
                        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                            try {
                                dbManager.indexFile(file, attrs);
                                int count = fileCount.incrementAndGet();

                                if (count % REPORT_INTERVAL == 0) {
                                    long elapsed = System.currentTimeMillis() - startTime;
                                    double rate = count / (elapsed / 1000.0);
                                    System.out.printf("⚡ %,d arquivos indexados (%.0f arq/s) [Virtual Thread: %s]\n",
                                            count, rate, Thread.currentThread());
                                }
                            } catch (SQLException e) {
                                errorCount.incrementAndGet();
                            }
                        }, virtualExecutor);

                        futures.add(future);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        dirCount.incrementAndGet();

                        // Também indexa o diretório
                        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                            try {
                                dbManager.indexFile(dir, attrs);
                                fileCount.incrementAndGet();
                            } catch (SQLException e) {
                                errorCount.incrementAndGet();
                            }
                        }, virtualExecutor);

                        futures.add(future);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        errorCount.incrementAndGet();
                        return FileVisitResult.CONTINUE;
                    }
                });

        System.out.println("\n⏳ Aguardando conclusão de " + futures.size() + " Virtual Threads...");

        // Aguarda TODAS as Virtual Threads completarem
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        try {
            allOf.join(); // Aguarda todas completarem
        } catch (Exception e) {
            System.err.println("❌ Erro durante indexação: " + e.getMessage());
        }

        // Compacta banco de dados
        System.out.println("\n🔧 Otimizando banco de dados...");
        dbManager.compactDatabase();

        // Relatório final
        long elapsed = System.currentTimeMillis() - startTime;
        double rate = fileCount.get() / (elapsed / 1000.0);

        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║  INDEXAÇÃO CONCLUÍDA (Virtual Threads)                         ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.printf("✓ Arquivos indexados: %,d\n", fileCount.get());
        System.out.printf("✓ Diretórios varridos: %,d\n", dirCount.get());
        System.out.printf("✓ Virtual Threads criadas: %,d\n", futures.size());
        System.out.printf("✓ Tempo decorrido: %.2f segundos\n", elapsed / 1000.0);
        System.out.printf("✓ Taxa média: %.0f arquivos/segundo\n", rate);
        System.out.printf("⚠️  Erros encontrados: %,d\n", errorCount.get());
        System.out.println("🎉 Virtual Threads = Zero overhead de memória!");
        System.out.println("");
    }

    /**
     * Indexa pasta com Virtual Threads
     */
    public void indexFolder(String folderPath, boolean includeSubfolders) throws IOException, InterruptedException, SQLException {
        Path folder = Paths.get(folderPath);

        if (!Files.exists(folder)) {
            throw new IllegalArgumentException("Pasta não encontrada: " + folderPath);
        }

        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("O caminho não é uma pasta: " + folderPath);
        }

        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║  INDEXANDO PASTA (Virtual Threads)                             ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println("📁 Pasta: " + folderPath);
        System.out.println("🔁 Incluir subpastas: " + (includeSubfolders ? "SIM" : "NÃO"));
        System.out.println("");

        if (includeSubfolders) {
            indexDirectory(folderPath);
        } else {
            indexFolderNonRecursive(folder);
        }
    }

    /**
     * Indexa apenas nível atual usando Virtual Threads
     */
    private void indexFolderNonRecursive(Path folder) throws IOException {
        long startTime = System.currentTimeMillis();
        AtomicInteger fileCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path entry : stream) {
                // Cada arquivo em uma Virtual Thread
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                        dbManager.indexFile(entry, attrs);
                        fileCount.incrementAndGet();
                    } catch (SQLException | IOException e) {
                        errorCount.incrementAndGet();
                    }
                }, virtualExecutor);

                futures.add(future);
            }
        }

        // Aguarda todas as Virtual Threads
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long elapsed = System.currentTimeMillis() - startTime;

        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║  INDEXAÇÃO CONCLUÍDA                                           ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.printf("✓ Arquivos indexados: %,d\n", fileCount.get());
        System.out.printf("✓ Virtual Threads: %,d\n", futures.size());
        System.out.printf("✓ Tempo decorrido: %.2f segundos\n", elapsed / 1000.0);
        System.out.printf("⚠️  Erros encontrados: %,d\n", errorCount.get());
        System.out.println("");
    }

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
            } catch (InterruptedException e) {
                virtualExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
