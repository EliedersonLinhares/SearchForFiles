package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.database.DatabaseManager;
import com.esl.searchforfiles.model.PaginationInfo;
import com.esl.searchforfiles.service.MonitoringService;
import com.esl.searchforfiles.service.SearchService;
import com.esl.searchforfiles.service.SyncService;
import com.esl.searchforfiles.test.AdvancedFileSearch;
import com.esl.searchforfiles.model.FileInfo;
import com.esl.searchforfiles.model.FileType;
import com.esl.searchforfiles.model.SearchCriteria;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;

/**
 * Controlador de busca com suporte a paginação
 * e sincronizaçao: Apenas sincroniza pastas já indexadas
 */
public class SearchController {

    private final AdvancedFileSearch searchSystem;
    private final JFrame parentFrame;
    private final MonitoringService monitoringService;
    private final SyncService syncService; // NOVO

    // Armazena última busca para paginação e auto-refresh
    private SearchCriteria lastCriteria;
    private String lastSelectedPath;
    private String lastSearchTerm;
    private String lastFilter;
    private String lastSortBy;
    private String lastSortOrder;
    private int lastPage;
    private int lastPageSize;
    private PaginatedSearchCallback lastCallback;

    // NOVO: Listener para mudanças no sistema de arquivos
    private FileSystemChangeListener fileSystemChangeListener;

    // Controle de monitoramento automático
    private String currentMonitoredPath = null;
    private static final long MAX_FOLDER_SIZE = 50_000;

    public SearchController(JFrame parentFrame) throws SQLException {
        this.parentFrame = parentFrame;
        this.searchSystem = new AdvancedFileSearch();
        this.monitoringService = searchSystem.getMonitoringService();

        this.syncService = new SyncService(
                searchSystem.getDatabaseManager(),
                searchSystem.getSearchService()
        ); // NOVO

        // NOVO: Configura listener de mudanças
        setupMonitoringListener();

        // ========================================================================
        // NOVO: CONFIGURAÇÃO DO CALLBACK PARA AUTO-REFRESH
        // ========================================================================

        // Configura callback no MonitoringService para ser notificado quando
        // arquivos forem criados, modificados ou deletados

         this.monitoringService.setFileChangeCallback(this::onFileSystemChange);

        System.out.println("✅ Sistema de busca inicializado");
        System.out.println("📡 Monitoramento automático ativado");
    }


    public DatabaseManager getDbManager() { return searchSystem.getDatabaseManager(); }

    public List<FileInfo> searchDirect(SearchCriteria criteria) throws Exception {
        return searchSystem.getSearchService().advancedSearch(criteria);
    }


    // ========================================================================
    // SINCRONIZAÇÃO AUTOMÁTICA (NOVO)
    // ========================================================================
    /**
     * Sincroniza pasta se já foi indexada
     * MODIFICADO: Valida antes de sincronizar
     *
     * @param folderPath Pasta a sincronizar
     * @param callback Callback para notificar resultado
     */
    public void syncFolderIfNeeded(String folderPath, SyncCallback callback) {
        if (folderPath == null || folderPath.trim().isEmpty()) {
            return;
        }

        if (isDriveRoot(folderPath)) {
            System.out.println("⚠️ Sincronização ignorada: drive raiz");
            return;
        }

        SwingWorker<SyncService.SyncResult, Void> worker = new SwingWorker<>() {
            @Override
            protected SyncService.SyncResult doInBackground() throws Exception {
                // VALIDAÇÃO CRÍTICA: Verifica se pasta foi indexada
                if (!syncService.isFolderIndexed(folderPath)) {
                    System.out.println("⚠️ Pasta não indexada, sincronização ignorada");
                    SyncService.SyncResult result = new SyncService.SyncResult(folderPath);
                    result.setNotIndexed(true);
                    return result;
                }

                // Verifica se precisa sincronizar
                if (!syncService.needsSync(folderPath)) {
                    System.out.println("✅ Índice já sincronizado");
                    return new SyncService.SyncResult(folderPath);
                }

                // Executa sincronização
                return syncService.synchronizeFolder(folderPath);
            }

            @Override
            protected void done() {
                try {
                    SyncService.SyncResult result = get();

                    if (callback != null) {
                        callback.onSyncCompleted(result);
                    }

                    // Se houve mudanças E há busca ativa, atualiza
                    if (result.hasChanges() && lastCallback != null) {
                        System.out.println("🔄 Atualizando resultados após sincronização...");
                        refreshCurrentSearch();
                    }

                } catch (Exception e) {
                    System.err.println("❌ Erro na sincronização: " + e.getMessage());
                    if (callback != null) {
                        callback.onSyncError(e);
                    }
                }
            }
        };

        worker.execute();
    }



    // ========================================================================
    // AUTO-REFRESH QUANDO ARQUIVOS MUDAM
    // ========================================================================

    /**
     * Configura listener para detectar mudanças no sistema de arquivos
     * NOVO MÉTODO
     */
    private void setupMonitoringListener() {
        // Adiciona hook no MonitoringService para ser notificado de mudanças
        // Nota: Isso requer modificação no MonitoringService para suportar callbacks
        System.out.println("🔄 Auto-refresh configurado");
    }

    /**
     * Atualiza resultados automaticamente após mudança no sistema de arquivos
     * NOVO MÉTODO
     */
    public void refreshCurrentSearch() {
        // Só atualiza se há uma busca anterior ativa
        if (lastCallback == null || lastSearchTerm == null || lastSearchTerm.isEmpty()) {
            System.out.println("⚠️ Nenhuma busca ativa para atualizar");
            return;
        }

        System.out.println("🔄 Auto-refresh: Atualizando resultados...");

        // Re-executa a última busca de forma silenciosa
        SwingWorker<SearchService.SearchResult, Void> worker = new SwingWorker<>() {
            @Override
            protected SearchService.SearchResult doInBackground() throws Exception {
                return searchSystem.getSearchService().advancedSearchWithPagination(
                        lastCriteria, lastPage, lastPageSize
                );
            }

            @Override
            protected void done() {
                try {
                    SearchService.SearchResult result = get();

                    // Notifica callback para atualizar UI
                    lastCallback.onSearchCompleted(result.getResults(), result.getPagination());

                    System.out.println("✅ Resultados atualizados automaticamente (" +
                            result.getResults().size() + " arquivos)");

                } catch (Exception e) {
                    System.err.println("❌ Erro ao atualizar: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    /**
     * Define listener para mudanças no sistema de arquivos
     * NOVO MÉTODO
     */
    public void setFileSystemChangeListener(FileSystemChangeListener listener) {
        this.fileSystemChangeListener = listener;
    }

    /**
     * Notifica que houve mudança no sistema de arquivos
     * Chamado pelo MonitoringService (requer modificação naquela classe)
     * NOVO MÉTODO
     */
    public void onFileSystemChange() {
        // Notifica listener (UI)
        if (fileSystemChangeListener != null) {
            SwingUtilities.invokeLater(() -> {
                fileSystemChangeListener.onFileSystemChanged();
            });
        }

        // Auto-refresh automático após pequeno delay (debounce)
        Timer timer = new Timer(500, e -> {
            refreshCurrentSearch();
        });
        timer.setRepeats(false);
        timer.start();
    }

    /**
     * Atualiza monitoramento com sincronização
     * MODIFICADO: Só monitora pastas indexadas
     */
    public void updateMonitoredFolder(String newPath, SyncCallback syncCallback) {
        if (newPath != null && newPath.equals(currentMonitoredPath)) {
            return;
        }

        if (newPath == null || newPath.trim().isEmpty()) {
            stopCurrentMonitoring();
            return;
        }

        if (isDriveRoot(newPath)) {
            System.out.println("⚠️ Drive raiz não será monitorado: " + newPath);
            stopCurrentMonitoring();
            return;
        }

        Path folderPath = Paths.get(newPath);
        if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
            System.out.println("⚠️ Pasta inválida: " + newPath);
            stopCurrentMonitoring();
            return;
        }

        long estimatedFiles = estimateFileCountFast(folderPath);
        if (estimatedFiles > MAX_FOLDER_SIZE) {
            System.out.println(String.format(
                    "⚠️ Pasta muito grande (%,d arquivos): %s",
                    estimatedFiles, newPath
            ));
            stopCurrentMonitoring();
            return;
        }

        stopCurrentMonitoring();

        // NOVO: Sincroniza antes de monitorar (se pasta foi indexada)
        syncFolderIfNeeded(newPath, new SyncCallback() {
            @Override
            public void onSyncCompleted(SyncService.SyncResult result) {
                // Notifica callback original
                if (syncCallback != null) {
                    syncCallback.onSyncCompleted(result);
                }

                // VALIDAÇÃO: Só monitora se pasta está indexada
                if (!result.isNotIndexed()) {
                    startMonitoringAsync(newPath);
                } else {
                    System.out.println("⚠️ Monitoramento não iniciado: pasta não indexada");
                }
            }

            @Override
            public void onSyncError(Exception e) {
                if (syncCallback != null) {
                    syncCallback.onSyncError(e);
                }
            }
        });
    }


    /**
     * Inicia monitoramento de forma assíncrona (não bloqueia UI)
     */

    /**
     * Inicia monitoramento de forma assíncrona
     */
    private void startMonitoringAsync(String path) {
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    System.out.println("📡 Iniciando monitoramento automático: " + path);
                    monitoringService.startMonitoring(path);
                    currentMonitoredPath = path;
                    return true;
                } catch (Exception e) {
                    System.err.println("❌ Erro ao iniciar monitoramento: " + e.getMessage());
                    return false;
                }
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        int dirCount = monitoringService.getMonitoredDirectories();
                        System.out.println(String.format(
                                "✅ Monitoramento ativo: %s (%d diretórios)",
                                path, dirCount
                        ));
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ Falha no monitoramento: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }
    /**
     * Para monitoramento atual (se existir)
     */
    private void stopCurrentMonitoring() {
        if (currentMonitoredPath != null && monitoringService.isMonitoring()) {
            System.out.println("🛑 Parando monitoramento de: " + currentMonitoredPath);
            monitoringService.stopMonitoring();
            currentMonitoredPath = null;
        }
    }

    /**
     * Verifica se o caminho é uma raiz de drive
     */
    private boolean isDriveRoot(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        String normalized = path.trim().toUpperCase();

        // Padrões de drive raiz: C:\, C:/, C:
        return normalized.matches("^[A-Z]:\\\\?$") ||
                normalized.matches("^[A-Z]:/?$");
    }

    /**
     * Estimativa rápida de quantidade de arquivos (limitada)
     * Para em 1000 arquivos para não travar
     */
    private long estimateFileCountFast(Path folder) {
        try {
            final long[] count = {0};
            final long quickLimit = 1000; // Limite rápido para decisão

            Files.walk(folder, 2) // Apenas 2 níveis de profundidade
                    .limit(quickLimit)
                    .forEach(path -> count[0]++);

            // Se chegou no limite, multiplica por estimativa
            if (count[0] >= quickLimit) {
                // Estima baseado em níveis de profundidade
                return count[0] * 10; // Estimativa conservadora
            }

            return count[0];

        } catch (Exception e) {
            return 0; // Em caso de erro, retorna 0 (não monitora)
        }
    }

    /**
     * Obtém status do monitoramento para exibição
     */
    public String getMonitoringStatus() {
        if (monitoringService.isMonitoring() && currentMonitoredPath != null) {
            int dirCount = monitoringService.getMonitoredDirectories();
            return String.format("📡 Monitorando (%d dirs)", dirCount);
        }
        return "";
    }

    /**
     * Verifica se há monitoramento ativo
     */
    public boolean isMonitoring() {
        return monitoringService.isMonitoring();
    }

    /**
     * Busca com paginação
     * MODIFICADO: Busca vazia mostra TODOS os arquivos da pasta
     */
    public void performSearchWithPagination(
            String searchTerm, String filter, String path,
            String sortBy, String sortOrder,
            int minRating, String tag,
            boolean includeSubfolders,   // NOVO
            int page, int pageSize,
            PaginatedSearchCallback callback) {

        SwingWorker<SearchService.SearchResult, Void> worker =
                new SwingWorker<>() {
                    @Override
                    protected SearchService.SearchResult doInBackground() throws Exception {

                        SearchCriteria criteria = new SearchCriteria()
                                .withName(searchTerm.isEmpty() ? null : "*" + searchTerm + "*")
                                .inPath(path, includeSubfolders)
                                .sortBy(sortBy, sortOrder);

                        if (!"TODOS".equals(filter))
                            criteria.withFileType(FileType.valueOf(filter));

                        if (minRating > 0)            // NOVO
                            criteria.withMinRating(minRating);

                        if (!tag.isEmpty())           // NOVO
                            criteria.withTag(tag);

                        return searchSystem.getSearchService().advancedSearchWithPagination(criteria, page, pageSize);
                    }

                    @Override
                    protected void done() {
                        try {
                            SearchService.SearchResult result = get();
                            SwingUtilities.invokeLater(() ->
                                    callback.onSearchCompleted(
                                            result.getResults(), result.getPagination()));
                        } catch (Exception e) {
                            SwingUtilities.invokeLater(() -> callback.onSearchError(e));
                        }
                    }
                };
        worker.execute();
    }

    public void goToPage(int page, int pageSize, PaginatedSearchCallback callback) {
        if (lastCriteria == null || lastSelectedPath == null) {
            System.err.println("Nenhuma busca anterior para paginar");
            return;
        }

        // MODIFICADO: Atualiza parâmetros para auto-refresh
        this.lastPage = page;
        this.lastPageSize = pageSize;
        this.lastCallback = callback;

        SwingWorker<SearchService.SearchResult, Void> worker = new SwingWorker<>() {
            @Override
            protected SearchService.SearchResult doInBackground() throws Exception {
                return searchSystem.getSearchService().advancedSearchWithPagination(
                        lastCriteria, page, pageSize
                );
            }

            @Override
            protected void done() {
                try {
                    SearchService.SearchResult result = get();
                    callback.onSearchCompleted(result.getResults(), result.getPagination());
                } catch (Exception e) {
                    callback.onSearchError(e);
                    e.printStackTrace();
                }
            }
        };

        callback.onSearchStarted();
        worker.execute();
    }


    public void performSearch(String searchTerm, String filter, String selectedPath,
                              SearchCallback callback) {

        if (searchTerm.isEmpty()) {
            JOptionPane.showMessageDialog(parentFrame,
                    "Digite um termo de busca!",
                    "Busca vazia", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String processedTerm = processSearchTerm(searchTerm);

        SwingWorker<List<FileInfo>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<FileInfo> doInBackground() throws Exception {
                SearchCriteria criteria = new SearchCriteria()
                        .withName(processedTerm)
                        .inPath(selectedPath, true)
                        .limit(3000);

                if (!"TODOS".equals(filter)) {
                    if ("FOLDER".equals(filter)) {
                        criteria.withFileType(FileType.FOLDER);
                    } else {
                        criteria.withFileType(FileType.valueOf(filter));
                    }
                }

                System.out.println("🔍 Buscando: " + searchTerm +
                        " → Padrão: " + processedTerm +
                        " | Tipo: " + filter +
                        " | Pasta: " + selectedPath);

                return searchSystem.advancedSearch(criteria);
            }

            @Override
            protected void done() {
                try {
                    List<FileInfo> results = get();
                    callback.onSearchCompleted(results);
                    System.out.println("✅ Encontrados: " + results.size() + " arquivos");
                } catch (Exception e) {
                    callback.onSearchError(e);
                    e.printStackTrace();
                }
            }
        };

        callback.onSearchStarted();
        worker.execute();
    }

    /**
     * Indexa uma pasta de forma assíncrona
     */
    public void indexFolder(String path, boolean includeSubfolders, IndexCallback callback) {
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setString("Indexando...");
        progressBar.setStringPainted(true);

        JPanel progressPanel = new JPanel(new BorderLayout(10, 10));
        progressPanel.add(new JLabel("📊 Indexando: " + path), BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);

        JDialog progressDialog = new JDialog(parentFrame, "Indexação em progresso", true);
        progressDialog.add(progressPanel);
        progressDialog.setSize(400, 100);
        progressDialog.setLocationRelativeTo(parentFrame);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                System.out.println("📊 Indexando: " + path +
                        " (subpastas: " + includeSubfolders + ")");
                searchSystem.indexFolder(path, includeSubfolders);
                return null;
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    get();
                    callback.onIndexCompleted();
                    JOptionPane.showMessageDialog(parentFrame,
                            "Indexação concluída com sucesso!",
                            "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    callback.onIndexError(e);
                    JOptionPane.showMessageDialog(parentFrame,
                            "Erro na indexação: " + e.getMessage(),
                            "Erro", JOptionPane.ERROR_MESSAGE);
                    e.printStackTrace();
                }
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    /**
     * Processa termo de busca adicionando wildcards
     */
    private String processSearchTerm(String term) {
        if (term == null || term.isEmpty()) {
            return "*";
        }

        if (term.contains("*") || term.contains("?")) {
            return term;
        }

        return "*" + term + "*";
    }

    public void close() throws SQLException {
        // Para monitoramento antes de fechar
        stopCurrentMonitoring();

        searchSystem.close();
        System.out.println("✓ Sistema encerrado");
    }

    public interface SearchCallback {
        void onSearchStarted();
        void onSearchCompleted(List<FileInfo> results);
        void onSearchError(Exception e);
    }

    public interface IndexCallback {
        void onIndexCompleted();
        void onIndexError(Exception e);
    }
    /**
     * Callback para busca paginada
     * NOVA INTERFACE
     */
    public interface PaginatedSearchCallback {
        void onSearchStarted();
        void onSearchCompleted(List<FileInfo> results, PaginationInfo pagination);
        void onSearchError(Exception e);
    }

    /**
     * Interface para notificar mudanças no sistema de arquivos
     * NOVA INTERFACE
     */
    public interface FileSystemChangeListener {
        void onFileSystemChanged();
    }

    /**
     * Callback para sincronização
     * NOVA INTERFACE
     */
    public interface SyncCallback {
        void onSyncCompleted(SyncService.SyncResult result);
        void onSyncError(Exception e);
    }

}