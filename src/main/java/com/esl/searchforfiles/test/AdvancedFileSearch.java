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
     * Obtém o serviço de monitoramento
     * Permite SearchController gerenciar monitoramento automático
     *
     * @return MonitoringService instance
     */
    public MonitoringService getMonitoringService() {
        return this.monitoringService;
    }

    /**
     * Obtém o gerenciador de banco de dados
     * Necessário para SyncService
     *
     * @return DatabaseManager instance
     */
    public DatabaseManager getDatabaseManager() {
        return this.dbManager;
    }

    /**
     * Obtém o serviço de busca
     * Necessário para SyncService
     *
     * @return SearchService instance
     */
    public SearchService getSearchService() {
        return this.searchService;
    }
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


//    /**
//     * Expõe SearchService para paginação
//     * NOVO MÉTODO
//     */
//    public SearchService getSearchService() {
//        return searchService;
//    }

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

}
