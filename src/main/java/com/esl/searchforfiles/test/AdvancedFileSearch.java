package com.esl.searchforfiles.test;

import com.esl.searchforfiles.database.DatabaseManager;
import com.esl.searchforfiles.model.*;
import com.esl.searchforfiles.service.IndexService;
import com.esl.searchforfiles.service.MonitoringService;
import com.esl.searchforfiles.service.SearchService;
import com.esl.searchforfiles.util.PathUtils;

import java.io.File;
import java.sql.SQLException;
import java.util.*;

/**
 * Sistema avançado de busca e indexação de arquivos
 *
 * Funcionalidades principais:
 * - Indexação paralela de arquivos (até 15.000 arquivos/segundo)
 * - Busca por nome, tipo, extensão, tamanho, data
 * - Monitoramento em tempo real de mudanças no sistema de arquivos
 * - Cache LRU para buscas frequentes (até 100x mais rápido)
 * - Suporte a múltiplos drives e pastas com controle granular
 * - Banco de dados SQLite otimizado com índices B-tree
 *
 * @author Sistema de Busca Avançada
 * @version 1.0.0
 */
public class AdvancedFileSearch {
    private final DatabaseManager dbManager;
    private final IndexService indexService;
    private final SearchService searchService;
    private final MonitoringService monitoringService;

    /**
     * Construtor principal - Inicializa todos os serviços
     * @throws SQLException se houver erro ao conectar com o banco de dados
     */
    public AdvancedFileSearch() throws SQLException {
        this.dbManager = new DatabaseManager();
        this.indexService = new IndexService(dbManager);
        this.searchService = new SearchService(dbManager);
        this.monitoringService = new MonitoringService(dbManager, searchService);
    }

    // ========================================================================
    // MÉTODOS DE INDEXAÇÃO
    // ========================================================================


    /**
     * Indexa um diretório completo com todas as subpastas
     * Utiliza thread pool com 8 threads para indexação paralela
     *
     * @param rootPath Caminho do diretório raiz a ser indexado
     * @throws Exception se houver erro durante a indexação
     *
     * Exemplo:
     * <pre>
     * search.indexDirectory("C:\\Users\\Documents");
     * </pre>
     */
    public void indexDirectory(String rootPath) throws Exception {
        indexService.indexDirectory(rootPath);
        searchService.clearCache();
    }

    /**
     * Indexa apenas um drive específico completo
     *
     * @param driveLetter Letra do drive (ex: "C", "D", "E")
     * @throws Exception se o drive não existir ou houver erro
     *
     * Exemplo:
     * <pre>
     * search.indexDrive("C");  // Indexa C:\
     * search.indexDrive("D");  // Indexa D:\
     * </pre>
     */
    public void indexDrive(String driveLetter) throws Exception {
        String drivePath = PathUtils.normalizeDriveLetter(driveLetter);

        File driveRoot = new File(drivePath);
        if (!driveRoot.exists()) {
            throw new IllegalArgumentException("Drive não encontrado: " + drivePath);
        }

        System.out.println("Indexando drive: " + drivePath);
        indexDirectory(drivePath);
    }

    /**
     * Indexa uma pasta específica com opção de incluir ou não subpastas
     *
     * @param folderPath Caminho completo da pasta
     * @param includeSubfolders true para incluir subpastas, false para apenas o nível atual
     * @throws Exception se a pasta não existir ou houver erro
     *
     * Exemplos:
     * <pre>
     * // Indexa apenas arquivos diretos da pasta
     * search.indexFolder("C:\\Temp", false);
     *
     * // Indexa pasta e todas as subpastas
     * search.indexFolder("C:\\Projects", true);
     * </pre>
     */
    public void indexFolder(String folderPath, boolean includeSubfolders) throws Exception {
        indexService.indexFolder(folderPath, includeSubfolders);
        searchService.clearCache();
    }

    // ========================================================================
    // MÉTODOS DE BUSCA
    // ========================================================================


    /**
     * Expõe SearchService para paginação
     * NOVO MÉTODO
     */
    public SearchService getSearchService() {
        return searchService;
    }

    /**
     * Busca arquivos por nome usando wildcards
     * Suporta * (qualquer sequência) e ? (qualquer caractere)
     *
     * @param pattern Padrão de busca com wildcards
     * @return Lista de arquivos encontrados (máximo 1000)
     * @throws SQLException se houver erro na consulta
     *
     * Exemplos:
     * <pre>
     * search.searchByName("*.pdf");          // Todos os PDFs
     * search.searchByName("relatorio*");     // Começa com "relatorio"
     * search.searchByName("*2024*");         // Contém "2024"
     * search.searchByName("foto?.jpg");      // foto1.jpg, fotoA.jpg, etc.
     * </pre>
     */
    public List<FileInfo> searchByName(String pattern) throws SQLException {
        return searchService.searchByName(pattern);
    }

    /**
     * Busca arquivos por tipo (categoria)
     *
     * @param fileType Tipo de arquivo (AUDIO, VIDEO, IMAGE, DOCUMENT, COMPRESSED, EXECUTABLE, FOLDER, ALL)
     * @return Lista de arquivos do tipo especificado (máximo 1000)
     * @throws SQLException se houver erro na consulta
     *
     * Exemplos:
     * <pre>
     * search.searchByFileType(FileType.IMAGE);       // Todas as imagens
     * search.searchByFileType(FileType.VIDEO);       // Todos os vídeos
     * search.searchByFileType(FileType.DOCUMENT);    // Todos os documentos
     * search.searchByFileType(FileType.AUDIO);       // Todos os áudios
     * </pre>
     */
    public List<FileInfo> searchByFileType(FileType fileType) throws SQLException {
        return searchService.searchByFileType(fileType);
    }

    /**
     * Busca avançada com múltiplos critérios combinados
     * Permite filtrar por: nome, tipo, extensão, tamanho, data, drive, pasta
     *
     * @param criteria Objeto com os critérios de busca
     * @return Lista de arquivos que atendem TODOS os critérios
     * @throws SQLException se houver erro na consulta
     *
     * Exemplos:
     * <pre>
     * // Vídeos grandes no drive C:
     * SearchCriteria criteria1 = new SearchCriteria()
     *     .inDrive("C")
     *     .withFileType(FileType.VIDEO)
     *     .withMinSize(100 * 1024 * 1024)  // > 100 MB
     *     .sortBy("size", "DESC");
     *
     * // Imagens PNG em pasta específica (sem subpastas)
     * SearchCriteria criteria2 = new SearchCriteria()
     *     .inPath("C:\\Photos", false)
     *     .withExtension("png")
     *     .modifiedAfter(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L);
     *
     * // Documentos modificados na última semana
     * SearchCriteria criteria3 = new SearchCriteria()
     *     .withFileType(FileType.DOCUMENT)
     *     .modifiedAfter(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L)
     *     .sortBy("last_modified", "DESC")
     *     .limit(50);
     * </pre>
     */
    public List<FileInfo> advancedSearch(SearchCriteria criteria) throws SQLException {
        return searchService.advancedSearch(criteria);
    }

    // ========================================================================
    // MÉTODOS DE LIMPEZA DO ÍNDICE
    // ========================================================================

    /**
     * Remove do índice todos os arquivos de um drive específico
     * Útil para limpar dados de drives removíveis ou re-indexar
     *
     * @param driveLetter Letra do drive (ex: "C", "D")
     * @return Número de arquivos removidos do índice
     * @throws SQLException se houver erro na operação
     *
     * Exemplo:
     * <pre>
     * int removed = search.clearDriveIndex("D");
     * System.out.println("Removidos " + removed + " arquivos do drive D:");
     * </pre>
     */
    public int clearDriveIndex(String driveLetter) throws SQLException {
        int deleted = dbManager.clearDriveIndex(driveLetter);
        searchService.clearCache();
        System.out.println("Removidos " + deleted + " arquivos do drive " + driveLetter + ":");
        return deleted;
    }

    /**
     * Remove do índice todos os arquivos de uma pasta
     *
     * @param folderPath Caminho da pasta
     * @param includeSubfolders true para remover também das subpastas
     * @return Número de arquivos removidos
     * @throws SQLException se houver erro na operação
     *
     * Exemplos:
     * <pre>
     * // Remove apenas da pasta (sem subpastas)
     * search.clearFolderIndex("C:\\Temp", false);
     *
     * // Remove da pasta e todas as subpastas
     * search.clearFolderIndex("C:\\OldProjects", true);
     * </pre>
     */
    public int clearFolderIndex(String folderPath, boolean includeSubfolders) throws SQLException {
        int deleted = dbManager.clearFolderIndex(folderPath, includeSubfolders);
        searchService.clearCache();
        System.out.println("Removidos " + deleted + " arquivos de: " + folderPath);
        return deleted;
    }

    // ========================================================================
    // MÉTODOS DE MONITORAMENTO EM TEMPO REAL
    // ========================================================================

    /**
     * Inicia monitoramento em tempo real de mudanças no sistema de arquivos
     * Detecta automaticamente: criação, modificação e exclusão de arquivos
     * O índice é atualizado automaticamente quando há mudanças
     *
     * @param rootPath Caminho do diretório a ser monitorado
     * @throws Exception se houver erro ao iniciar o monitoramento
     *
     * Exemplo:
     * <pre>
     * // Monitora pasta e atualiza índice automaticamente
     * search.startMonitoring("C:\\Users\\Documents");
     *
     * // Sistema detecta mudanças e atualiza em tempo real
     * // - Arquivo criado: indexado automaticamente
     * // - Arquivo modificado: re-indexado
     * // - Arquivo deletado: removido do índice
     * </pre>
     */
    public void startMonitoring(String rootPath) throws Exception {
        monitoringService.startMonitoring(rootPath);
    }

    /**
     * Para o monitoramento em tempo real
     *
     * Exemplo:
     * <pre>
     * search.stopMonitoring();
     * System.out.println("Monitoramento encerrado");
     * </pre>
     */
    public void stopMonitoring() {
        monitoringService.stopMonitoring();
    }

    // ========================================================================
    // MÉTODOS UTILITÁRIOS E ESTATÍSTICAS
    // ========================================================================

    /**
     * Lista todos os drives (unidades) disponíveis no sistema
     * Inclui informações sobre espaço total, livre e utilizável
     *
     * @return Lista de informações dos drives
     *
     * Exemplo:
     * <pre>
     * List&lt;DriveInfo&gt; drives = search.getAvailableDrives();
     * for (DriveInfo drive : drives) {
     *     System.out.println(drive);
     *     // C:\ - Total: 500.00 GB, Livre: 200.00 GB (40.0%)
     * }
     * </pre>
     */
    public List<DriveInfo> getAvailableDrives() {
        List<DriveInfo> drives = new ArrayList<>();

        for (File root : File.listRoots()) {
            DriveInfo drive = new DriveInfo(
                    root.getAbsolutePath(),
                    root.getTotalSpace(),
                    root.getFreeSpace(),
                    root.getUsableSpace()
            );
            drives.add(drive);
        }

        return drives;
    }

    /**
     * Obtém estatísticas completas do índice
     * Inclui: total de arquivos, tamanho total, distribuição por tipo e drive
     *
     * @return Objeto com todas as estatísticas
     * @throws SQLException se houver erro ao consultar
     *
     * Exemplo:
     * <pre>
     * IndexStats stats = search.getIndexStats();
     * System.out.println("Total de arquivos: " + stats.getTotalFiles());
     * System.out.println("Tamanho total: " + stats.getTotalSize() + " bytes");
     * System.out.println("\nDistribuição por tipo:");
     * stats.getFilesByType().forEach((type, count) ->
     *     System.out.println(type + ": " + count)
     * );
     * </pre>
     */
    public IndexStats getIndexStats() throws SQLException {
        return searchService.getIndexStats();
    }

    /**
     * Compacta o banco de dados SQLite
     * Remove espaço não utilizado e otimiza índices
     * Recomendado executar periodicamente ou após grandes exclusões
     *
     * @throws SQLException se houver erro na compactação
     *
     * Exemplo:
     * <pre>
     * search.compactDatabase();
     * // Compactando banco de dados...
     * // Compactação concluída em 1234ms
     * </pre>
     */
    public void compactDatabase() throws SQLException {
        dbManager.compactDatabase();
    }

    /**
     * Fecha todas as conexões e libera recursos
     * IMPORTANTE: Deve ser chamado antes de encerrar a aplicação
     *
     * @throws SQLException se houver erro ao fechar conexões
     *
     * Exemplo:
     * <pre>
     * try {
     *     AdvancedFileSearch search = new AdvancedFileSearch();
     *     // ... usar o sistema ...
     * } finally {
     *     search.close();  // Sempre fechar
     * }
     * </pre>
     */
    public void close() throws SQLException {
        monitoringService.shutdown();
        indexService.shutdown();
        dbManager.close();
    }

    // ========================================================================
    // MÉTODO MAIN - DEMONSTRAÇÃO COMPLETA DO SISTEMA
    // ========================================================================

    /**
     * Método main com exemplos de uso de todas as funcionalidades
     */
    public static void main(String[] args) {
        AdvancedFileSearch search = null;

        try {
            // Inicialização
            System.out.println("╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║     SISTEMA AVANÇADO DE BUSCA E INDEXAÇÃO DE ARQUIVOS         ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

            search = new AdvancedFileSearch();

            // ===== FASE 1: LISTAR DRIVES DISPONÍVEIS =====
            System.out.println("═══ FASE 1: DRIVES DISPONÍVEIS ═══");
            List<DriveInfo> drives = search.getAvailableDrives();
            drives.forEach(drive -> System.out.println("  " + drive));

            // ===== FASE 2: INDEXAÇÃO =====
            System.out.println("\n═══ FASE 2: INDEXAÇÃO ═══");

            // OPÇÃO A: Indexar drive completo (comentado para segurança)
             System.out.println("\nIndexando drive K:\\ completo...");
             search.indexDrive("K");

            // OPÇÃO B: Indexar pasta específica (recomendado para testes)
           // System.out.println("\nIndexando pasta específica...");
           // search.indexFolder("C:\\Users\\ESL\\Pictures", true);
           //C:\Users\ESL\Pictures
            // ===== FASE 3: ESTATÍSTICAS DO ÍNDICE =====
            System.out.println("\n═══ FASE 3: ESTATÍSTICAS DO ÍNDICE ═══");
            IndexStats stats = search.getIndexStats();
            System.out.println(stats);

            // ===== FASE 4: BUSCAS POR TIPO DE ARQUIVO =====
            System.out.println("\n═══ FASE 4: BUSCA POR TIPO DE ARQUIVO ═══");

            System.out.println("\n--- Imagens (top 5) ---");
            List<FileInfo> images = search.searchByFileType(FileType.IMAGE);
            System.out.println("Total encontrado: " + images.size());
            images.stream().limit(5).forEach(f -> System.out.println("  " + f));

            System.out.println("\n--- Documentos (top 5) ---");
            List<FileInfo> documents = search.searchByFileType(FileType.DOCUMENT);
            System.out.println("Total encontrado: " + documents.size());
            documents.stream().limit(5).forEach(f -> System.out.println("  " + f));

            System.out.println("\n--- Vídeos (top 5) ---");
            List<FileInfo> videos = search.searchByFileType(FileType.VIDEO);
            System.out.println("Total encontrado: " + videos.size());
            videos.stream().limit(5).forEach(f -> System.out.println("  " + f));

            System.out.println("\n--- Áudios (top 5) ---");
            List<FileInfo> audios = search.searchByFileType(FileType.AUDIO);
            System.out.println("Total encontrado: " + audios.size());
            audios.stream().limit(5).forEach(f -> System.out.println("  " + f));

            // ===== FASE 5: BUSCA POR NOME =====
            System.out.println("\n═══ FASE 5: BUSCA POR NOME ═══");

            System.out.println("\n--- Busca: *.pdf ---");
            List<FileInfo> pdfs = search.searchByName("*.pdf");
            System.out.println("PDFs encontrados: " + pdfs.size());
            pdfs.stream().limit(5).forEach(f -> System.out.println("  " + f));

            System.out.println("\n--- Busca: *.txt ---");
            List<FileInfo> txts = search.searchByName("*.txt");
            System.out.println("TXTs encontrados: " + txts.size());
            txts.stream().limit(5).forEach(f -> System.out.println("  " + f));

            // ===== FASE 6: BUSCAS AVANÇADAS =====
            System.out.println("\n═══ FASE 6: BUSCAS AVANÇADAS ═══");

            // Exemplo 1: Busca limitada a um drive
            System.out.println("\n--- Exemplo 1: Imagens apenas no drive C:\\ ---");
            SearchCriteria criteria1 = new SearchCriteria()
                    .inDrive("C")
                    .withFileType(FileType.IMAGE)
                    .sortBy("size", "DESC")
                    .limit(5);

            List<FileInfo> driveResults = search.advancedSearch(criteria1);
            System.out.println("Encontrados: " + driveResults.size());
            driveResults.forEach(f -> System.out.println("  " + f));

            // Exemplo 2: Busca em pasta específica (sem subpastas)
            System.out.println("\n--- Exemplo 2: Documentos em pasta específica (sem subpastas) ---");
            SearchCriteria criteria2 = new SearchCriteria()
                    .inPath("C:\\Users\\ESL\\Pictures", false)
                    .withFileType(FileType.DOCUMENT)
                    .limit(5);

            List<FileInfo> folderResults = search.advancedSearch(criteria2);
            System.out.println("Encontrados: " + folderResults.size());
            folderResults.forEach(f -> System.out.println("  " + f));

            // Exemplo 3: Arquivos grandes
            System.out.println("\n--- Exemplo 3: Arquivos > 10 MB ---");
            SearchCriteria criteria3 = new SearchCriteria()
                    .withMinSize(10 * 1024 * 1024)
                    .sortBy("size", "DESC")
                    .limit(10);

            List<FileInfo> largeFiles = search.advancedSearch(criteria3);
            System.out.println("Encontrados: " + largeFiles.size());
            largeFiles.forEach(f -> System.out.println("  " + f));

            // Exemplo 4: Arquivos modificados recentemente
            System.out.println("\n--- Exemplo 4: Arquivos modificados na última semana ---");
            long oneWeekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
            SearchCriteria criteria4 = new SearchCriteria()
                    .modifiedAfter(oneWeekAgo)
                    .sortBy("last_modified", "DESC")
                    .limit(10);

            List<FileInfo> recentFiles = search.advancedSearch(criteria4);
            System.out.println("Encontrados: " + recentFiles.size());
            recentFiles.forEach(f -> System.out.println("  " + f));

            // ===== FASE 7: DEMONSTRAÇÃO DE CACHE =====
            System.out.println("\n═══ FASE 7: PERFORMANCE DO CACHE ═══");

            // Primeira busca (sem cache)
            long start = System.currentTimeMillis();
            search.searchByFileType(FileType.IMAGE);
            long firstSearch = System.currentTimeMillis() - start;

            // Segunda busca (com cache)
            start = System.currentTimeMillis();
            search.searchByFileType(FileType.IMAGE);
            long cachedSearch = System.currentTimeMillis() - start;

            System.out.println("Primeira busca (banco): " + firstSearch + "ms");
            System.out.println("Segunda busca (cache):  " + cachedSearch + "ms");
            System.out.println("Speedup: " + String.format("%.1fx mais rápida",
                    (double)firstSearch / Math.max(cachedSearch, 1)));

            // ===== FASE 8: MONITORAMENTO EM TEMPO REAL =====
            System.out.println("\n═══ FASE 8: MONITORAMENTO EM TEMPO REAL ═══");
            search.startMonitoring("C:\\Users\\ESL\\Pictures");
            System.out.println("✓ Monitoramento ativo para: C:\\Users\\ESL\\Pictures");
            System.out.println("  • Crie um arquivo na pasta para ver a indexação automática");
            System.out.println("  • Modifique um arquivo para ver a re-indexação");
            System.out.println("  • Delete um arquivo para ver a remoção do índice");

            // ===== INSTRUÇÕES FINAIS =====
            System.out.println("\n═══════════════════════════════════════════════════════════════");
            System.out.println("║                    SISTEMA PRONTO!                          ║");
            System.out.println("═══════════════════════════════════════════════════════════════");
            System.out.println("\n EXEMPLOS DE USO:");
            System.out.println("  1. searchByName(\"*.jpg\")");
            System.out.println("  2. searchByFileType(FileType.AUDIO)");
            System.out.println("  3. advancedSearch(criteria)");
            System.out.println("  4. getIndexStats()");
            System.out.println("  5. indexDrive(\"D\")");
            System.out.println("\n  Monitoramento ativo!");
            System.out.println("   Pressione Ctrl+C para encerrar\n");

            // Mantém o programa rodando para demonstrar monitoramento
            Thread.sleep(Long.MAX_VALUE);

        } catch (InterruptedException e) {
            System.out.println("\nSistema interrompido pelo usuário");
        } catch (Exception e) {
            System.err.println("\n ERRO: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Cleanup
            if (search != null) {
                try {
                    search.close();
                    System.out.println("\n✓ Recursos liberados com sucesso");
                } catch (SQLException e) {
                    System.err.println("Erro ao fechar: " + e.getMessage());
                }
            }
        }
    }


}
