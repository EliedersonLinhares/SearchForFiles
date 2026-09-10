package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.configuration.UIConfig;
import com.esl.searchforfiles.database.DatabaseManager;
import com.esl.searchforfiles.model.FileInfo;
import com.esl.searchforfiles.model.FileType;
import com.esl.searchforfiles.model.PaginationInfo;
import com.esl.searchforfiles.model.SearchCriteria;
import com.esl.searchforfiles.service.*;
import com.esl.searchforfiles.test.AdvancedFileSearch;

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

    private static final long MAX_FOLDER_SIZE = 50_000;
    private final AdvancedFileSearch searchSystem;
    private final JFrame parentFrame;
    private final MonitoringService monitoringService;
    private final SyncService syncService; // NOVO
    private final IndexFilterService indexFilterService;
    private final FileExplorerSwing fileExplorerSwing;
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
    // Adicione os campos
    private Timer autoRefreshTimer;
    private volatile boolean editInProgress = false;
    private volatile boolean transferInProgress = false;

    public SearchController(JFrame parentFrame, IndexFilterService indexFilterService, FileExplorerSwing fileExplorerSwing) throws SQLException {
        this.parentFrame = parentFrame;
        this.indexFilterService = indexFilterService;
        this.fileExplorerSwing = fileExplorerSwing;
        this.searchSystem = new AdvancedFileSearch(indexFilterService, getFileExplorerSwing());
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

    public SyncService getSyncService() {
        return syncService;
    }

    public MonitoringService getMonitoringService() {
        return monitoringService;
    }

    public FileExplorerSwing getFileExplorerSwing() {
        return fileExplorerSwing;
    }

    public DatabaseManager getDbManager() {
        return searchSystem.getDatabaseManager();
    }

    public void setTransferInProgress(boolean inProgress) {
        this.transferInProgress = inProgress;
    }

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
     * @param callback   Callback para notificar resultado
     */
    public void syncFolderIfNeeded(String folderPath, SyncCallback callback) {
        if (folderPath == null || folderPath.trim().isEmpty()) {
            if (callback != null)
                callback.onSyncCompleted(new SyncService.SyncResult(folderPath));
            return;
        }

        if (isDriveRoot(folderPath)) {
            System.out.println("⚠️ Sincronização ignorada: drive raiz");
            if (callback != null) {
                SyncService.SyncResult r = new SyncService.SyncResult(folderPath);
                r.setNotIndexed(true);
                callback.onSyncCompleted(r);
            }
            return;
        }

        SwingWorker<SyncService.SyncResult, Void> worker = new SwingWorker<>() {
            @Override
            protected SyncService.SyncResult doInBackground() throws Exception {
                if (!syncService.isFolderIndexed(folderPath)) {
                    SyncService.SyncResult result = new SyncService.SyncResult(folderPath);
                    result.setNotIndexed(true);
                    return result;
                }

                if (!syncService.needsSync(folderPath)) {
                    return new SyncService.SyncResult(folderPath);
                }

                return syncService.synchronizeFolder(folderPath);
            }
            @Override
            protected void done() {
                try {
                    SyncService.SyncResult result = get();

                    if (callback != null)
                        callback.onSyncCompleted(result); // ← callback SEMPRE chamado

                    // Remova a condição result.hasChanges() daqui — o callback
                    // já decide se faz refresh

                } catch (Exception e) {
                    System.err.println("❌ Erro na sincronização: " + e.getMessage());
                    if (callback != null) callback.onSyncError(e);
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
        if (lastCallback == null) {
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

    public void setEditInProgress(boolean inProgress) {
        this.editInProgress = inProgress;
    }

    /**
     * Notifica que houve mudança no sistema de arquivos
     * Chamado pelo MonitoringService (requer modificação naquela classe)
     * NOVO MÉTODO
     */
    public void onFileSystemChange() {
        if (transferInProgress || editInProgress) {
            System.out.println("⏸️  Auto-refresh suspenso: operação em andamento");
            return;
        }

        if (fileSystemChangeListener != null)
            SwingUtilities.invokeLater(() -> fileSystemChangeListener.onFileSystemChanged());

        // Cancela o timer anterior antes de criar novo
        if (autoRefreshTimer != null && autoRefreshTimer.isRunning())
            autoRefreshTimer.stop();

        autoRefreshTimer = new Timer(800, e -> refreshCurrentSearch()); // aumenta debounce
        autoRefreshTimer.setRepeats(false);
        autoRefreshTimer.start();
    }

    public void resumeAfterEdit() {
        if (autoRefreshTimer != null && autoRefreshTimer.isRunning())
            autoRefreshTimer.stop();

        editInProgress = false;
        transferInProgress = false;

        System.out.println("resumeAfterEdit → lastSelectedPath=" + lastSelectedPath);

        if (lastSelectedPath != null) {
            syncFolderIfNeeded(lastSelectedPath, new SyncCallback() {
                @Override
                public void onSyncCompleted(SyncService.SyncResult result) {
                    System.out.println("onSyncCompleted → hasChanges=" + result.hasChanges()
                            + " notIndexed=" + result.isNotIndexed());
                    SwingUtilities.invokeLater(() -> refreshCurrentSearch());
                }

                @Override
                public void onSyncError(Exception e) {
                    System.out.println("onSyncError → " + e.getMessage());
                    SwingUtilities.invokeLater(() -> refreshCurrentSearch());
                }
            });
        } else {
            SwingUtilities.invokeLater(this::refreshCurrentSearch);
        }
    }

    /**
     * Inicia monitoramento de forma assíncrona
     */
    public void startMonitoringAsync(String path) {


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
    public void stopCurrentMonitoring() {
        if (currentMonitoredPath != null && monitoringService.isMonitoring()) {
            System.out.println("🛑 Parando monitoramento de: " + currentMonitoredPath);
            monitoringService.stopMonitoring();
            currentMonitoredPath = null;
        }
    }

    /**
     * Verifica se o caminho é uma raiz de drive
     */
    public boolean isDriveRoot(String path) {
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
//    public void performSearchWithPagination(
//            String searchTerm, String filter, String path,
//            String sortBy, String sortOrder,
//            int minRating, String tag,
//            boolean includeSubfolders,
//            int page, int pageSize,
//            PaginatedSearchCallback callback) {
//
//        SwingWorker<SearchService.SearchResult, Void> worker = new SwingWorker<>() {
//            @Override
//            protected SearchService.SearchResult doInBackground() throws Exception {
//
//                SearchCriteria criteria = new SearchCriteria()
//                        .withName(searchTerm == null || searchTerm.isEmpty()
//                                ? null : "*" + searchTerm + "*")
//                        .inPath(path, includeSubfolders)
//                        .sortBy(sortBy, sortOrder);
//
//                if (!"ALL".equals(filter))
//                    criteria.withFileType(FileType.valueOf(filter));
//                if (minRating > 0)
//                    criteria.withMinRating(minRating);
//                if (tag != null && !tag.isEmpty())
//                    criteria.withTag(tag);
//
//                // ── CORREÇÃO: salva estado para o refreshCurrentSearch() ──
//                lastCriteria = criteria;
//                lastSelectedPath = path;
//                lastSearchTerm = searchTerm != null ? searchTerm : "";
//                lastFilter = filter;
//                lastSortBy = sortBy;
//                lastSortOrder = sortOrder;
//                lastPage = page;
//                lastPageSize = pageSize;
//                lastCallback = callback;
//                // ─────────────────────────────────────────────────────────
//
//                return searchSystem.getSearchService()
//                        .advancedSearchWithPagination(criteria, page, pageSize);
//            }
//
//            @Override
//            protected void done() {
//                try {
//                    SearchService.SearchResult result = get();
//                    SwingUtilities.invokeLater(() ->
//                            callback.onSearchCompleted(
//                                    result.getResults(), result.getPagination()));
//                } catch (Exception e) {
//                    SwingUtilities.invokeLater(() -> callback.onSearchError(e));
//                }
//            }
//        };
//        worker.execute();
//    }
    public void performSearchWithPagination(
            String searchTerm, String filter, String path,
            String sortBy, String sortOrder,
            int minRating, String tag,
            boolean includeSubfolders,
            boolean globalSearch, // NOVO
            int page, int pageSize,
            PaginatedSearchCallback callback) {

        SwingWorker<SearchService.SearchResult, Void> worker = new SwingWorker<>() {
            @Override
            protected SearchService.SearchResult doInBackground() throws Exception {

                SearchCriteria criteria = new SearchCriteria()
                        .withName(searchTerm == null || searchTerm.isEmpty()
                                ? null : "*" + searchTerm + "*")
                        .sortBy(sortBy, sortOrder);

                // NOVO: só restringe por pasta se NÃO for busca global
                if (!globalSearch) {
                    criteria.inPath(path, includeSubfolders);
                }

                if (!"ALL".equals(filter))
                    criteria.withFileType(FileType.valueOf(filter));
                if (minRating > 0)
                    criteria.withMinRating(minRating);
                if (tag != null && !tag.isEmpty())
                    criteria.withTag(tag);

                lastCriteria = criteria;
                lastSelectedPath = globalSearch ? null : path; // NOVO: evita reusar path errado no refresh
                lastSearchTerm = searchTerm != null ? searchTerm : "";
                lastFilter = filter;
                lastSortBy = sortBy;
                lastSortOrder = sortOrder;
                lastPage = page;
                lastPageSize = pageSize;
                lastCallback = callback;

                return searchSystem.getSearchService()
                        .advancedSearchWithPagination(criteria, page, pageSize);
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

    public void indexFolder(String path, boolean includeSubfolders, IndexCallback callback) {
        IndexCancellationToken cancelToken = new IndexCancellationToken();

        JTextArea logArea = new JTextArea(18, 60);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        JButton cancelButton = new JButton("Cancelar");

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);

        JPanel progressPanel = new JPanel(new BorderLayout(10, 10));
        progressPanel.add(new JLabel("📊 Indexando: " + path), BorderLayout.NORTH);
        progressPanel.add(scrollPane, BorderLayout.CENTER);
        progressPanel.add(buttonPanel, BorderLayout.SOUTH);

        JDialog progressDialog = new JDialog(parentFrame, "Indexação em progresso", true);
        progressDialog.add(progressPanel);
        progressDialog.setSize(700, 480);
        progressDialog.setLocationRelativeTo(parentFrame);

        // Impede fechar via X sem confirmar (evita perder o progresso sem querer)
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                searchSystem.indexFolder(path, includeSubfolders, this::publish, cancelToken);
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String line : chunks) {
                    logArea.append(line + "\n");
                }
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    get();
                    if (cancelToken.isCancelled()) {
                        // Cancelado pelo usuário — trata como um resultado neutro, não erro
                        callback.onIndexCompleted();
                        JOptionPane.showMessageDialog(parentFrame,
                                "Indexação cancelada pelo usuário.",
                                "Cancelado", JOptionPane.WARNING_MESSAGE);
                    } else {
                        callback.onIndexCompleted();
                        JOptionPane.showMessageDialog(parentFrame,
                                "Indexação concluída com sucesso!",
                                "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                       // fileExplorerSwing.navigateTo(path, false);
                    }
                    fileExplorerSwing.navigateTo(path, false); // NOVO: sempre re-sincroniza, cancelado ou não
                } catch (Exception e) {
                    callback.onIndexError(e);
                    JOptionPane.showMessageDialog(parentFrame,
                            "Erro na indexação: " + e.getMessage(),
                            "Erro", JOptionPane.ERROR_MESSAGE);
                    e.printStackTrace();
                }
            }
        };

        // NOVO: botão de cancelar
        cancelButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(progressDialog,
                    "Deseja realmente cancelar a indexação?\nOs arquivos já indexados serão mantidos.",
                    "Confirmar cancelamento", JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                cancelToken.cancel();
                cancelButton.setEnabled(false);
                cancelButton.setText("Cancelando...");

                // NOVO: feedback imediato no log — já estamos na EDT aqui,
                // então appendamos direto em vez de usar publish()
                logArea.append("\n🛑 Cancelamento solicitado — aguardando finalização das threads em andamento...\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
        });

        worker.execute();
        progressDialog.setVisible(true);
    }

    public void close() throws SQLException {
        // Para monitoramento antes de fechar
        stopCurrentMonitoring();

        searchSystem.close();
        System.out.println("✓ Sistema encerrado");
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

    /**
     * Token de cancelamento cooperativo para operações de indexação.
     * Desacoplado de Swing/SwingWorker — qualquer camada pode marcar
     * cancel() e o IndexService verifica periodicamente.
     * <p>
     * "Cooperativo" porque Virtual Threads já em execução não são
     * interrompidas à força; elas terminam a operação atual (ex: indexar
     * um arquivo específico) e então param de submeter novo trabalho.
     */
    public class IndexCancellationToken {
        private volatile boolean cancelled = false;

        public void cancel() {
            cancelled = true;
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }

}