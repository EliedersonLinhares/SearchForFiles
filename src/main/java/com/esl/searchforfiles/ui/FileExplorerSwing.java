package com.esl.searchforfiles.ui;


import com.esl.searchforfiles.model.FileInfo;
import com.esl.searchforfiles.model.PaginationInfo;
import com.esl.searchforfiles.service.FavoritesService;
import com.esl.searchforfiles.service.SyncService;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Interface gráfica para o sistema de busca avançada
 * Adaptado do FileExplorer original para usar AdvancedFileSearch
 */
public class FileExplorerSwing extends JFrame {

    private final SearchPanel searchPanel;
    private final ResultsPanel resultsPanel;
    private final SearchController controller;
    private final FavoritesService favoritesService; // NOVO
    private final PaginationPanel paginationPanel;
    private FolderTreePanel treePanel;
    private FavoritesPanel favoritesPanel; // NOVO
    private JLabel statusLabel;
    private String selectedPath = "C:\\Users\\ESL\\Downloads";
    private int currentPage = 1; // NOVO
    private String currentSortBy = "last_modified"; // NOVO
    private String currentSortOrder = "DESC"; // NOVO
    private SubFolderPanel subFolderPanel;           // NOVO
    private boolean showSubfolderContents = true; // NOVO — controlado pelo menu

    // NOVO: Indicador de auto-refresh
    private JLabel autoRefreshIndicator;

    private JLabel syncIndicator; // NOVO
    private final NavigationHistory navigationHistory = new NavigationHistory();

    public FileExplorerSwing() {
        super("Advanced File Search - Interface Gráfica");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1400, 800);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        favoritesService = new FavoritesService();

        try {
            controller = new SearchController(this);
            controller.setFileSystemChangeListener(this::onFileSystemChanged);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Erro ao inicializar: " + e.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException(e);
        }

        // === PAINEL SUPERIOR ===
        searchPanel = new SearchPanel();
        resultsPanel = new ResultsPanel();
        FileItemPanel.ICON_CACHE.clear(); // força recarregamento sem distorção
        searchPanel.setSearchListener(this::onSearch);
        searchPanel.setIndexListener(this::onIndexRequest);
        searchPanel.setNavigationListener(new SearchPanel.NavigationListener() {
            @Override
            public void onBack() {
                String prev = navigationHistory.back();
                if (prev != null) navigateTo(prev, false);
            }
            @Override
            public void onForward() {
                String next = navigationHistory.forward();
                if (next != null) navigateTo(next, false);
            }
        });
        searchPanel.setThumbnailSizeListener(size ->
                resultsPanel.setThumbnailSize(size));
        add(searchPanel, BorderLayout.NORTH);

        // === PAINEL ESQUERDO ===
        JPanel leftPanel = createLeftPanel();

        // === PAINEL CENTRAL (resultados + paginação) ===

        resultsPanel.setBackgroundColor(new Color(45, 45, 45));
        resultsPanel.setFileItemClickListener(new ResultsPanel.FileItemClickListener() {
            @Override
            public void onFileDoubleClick(File file) {
                if (file.isDirectory()) {
                    navigateTo(file.getAbsolutePath(), true);
                } else {
                    try { Desktop.getDesktop().open(file); }
                    catch (IOException | IllegalArgumentException e) {
                        JOptionPane.showMessageDialog(FileExplorerSwing.this,
                                "Erro ao abrir: " + e.getMessage());
                    }

                }
            }
            @Override
            public void onFileRightClick(File file, FileInfo fileInfo,
                                         Component source, int x, int y,
                                         FileItemPanel itemPanel) {
                FileContextMenu menu = new FileContextMenu(
                        file, fileInfo, source, controller.getDbManager(), itemPanel,
                        path -> navigateTo(path, true),
                        showSubfolderContents,
                        include -> {
                            showSubfolderContents = include;
                            currentPage = 1;
                            performCurrentSearch();
                        }
                );
                menu.show(source, x, y);
            }
        });

        paginationPanel = new PaginationPanel();
        paginationPanel.setPaginationListener(new PaginationPanel.PaginationListener() {
            @Override
            public void onPageChanged(int newPage) {
                currentPage = newPage;
                performCurrentSearch();
            }
            @Override
            public void onPageSizeChanged(int newPageSize) {
                currentPage = 1;
                performCurrentSearch();
            }
        });

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(resultsPanel,    BorderLayout.CENTER);
        centerPanel.add(paginationPanel, BorderLayout.SOUTH);

        // === PAINEL DIREITO (subpastas) ===
        subFolderPanel = new SubFolderPanel();
        subFolderPanel.setFolderClickListener(folder ->
                navigateTo(folder.getAbsolutePath(), true));

        // === SPLITS ===
        // 1. rightSplit: centerPanel (resultados) + subFolderPanel
        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                centerPanel, subFolderPanel);
        rightSplit.setResizeWeight(1.0);      // resultados expande, subpastas fixo
        rightSplit.setDividerSize(4);
        rightSplit.setContinuousLayout(true);
        // Divider location em pixels após o frame estar visível (veja abaixo)

        // 2. mainSplit: leftPanel + rightSplit
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                leftPanel, rightSplit);
        mainSplit.setDividerLocation(300);
        mainSplit.setResizeWeight(0.0);

        add(mainSplit, BorderLayout.CENTER);  // ← único add ao CENTER

        // === PAINEL INFERIOR ===
        JPanel statusPanel = createStatusPanel();
        add(statusPanel, BorderLayout.SOUTH);

        showWelcomeMessage();
        setVisible(true);

        // Define divider do rightSplit após o frame estar visível
        // (só assim getWidth() tem valor real)
        SwingUtilities.invokeLater(() -> {
            int totalWidth = rightSplit.getWidth();
            rightSplit.setDividerLocation((int) (totalWidth * 0.80));
        });

       // performCurrentSearch();
        // MODIFICADO: em vez de performCurrentSearch() direto,
        // passa pelo navigateTo() para que a sincronização aconteça
        // antes de exibir os resultados
        navigateTo(selectedPath, true);  // selectedPath = "C:\" por padrão
    }

    /**
     * Navega para o caminho informado.
     * @param path        Caminho de destino
     * @param pushHistory true  = navegação normal (empurra no histórico)
     *                    false = movimento pelo histórico (back/forward)
     */
//    private void navigateTo(String path, boolean pushHistory) {
//        selectedPath = path;
//
//        if (pushHistory) navigationHistory.push(path);
//
//        searchPanel.updateNavigationState(navigationHistory);
//        showSyncIndicator("🔄 Verificando mudanças...");
//        controller.updateMonitoredFolder(selectedPath, createSyncCallback());
//
//        currentPage = 1;
//
//        // NOVO: atualiza SubFolderPanel
//        subFolderPanel.loadSubfolders(selectedPath, controller);
//
//        performCurrentSearch();
//    }


    private void navigateTo(String path, boolean pushHistory) {
        selectedPath = path;

        if (pushHistory) navigationHistory.push(path);

        searchPanel.updateNavigationState(navigationHistory);
        showSyncIndicator("🔄 Verificando mudanças...");

        // Subpastas carregam independentemente
        subFolderPanel.loadSubfolders(selectedPath, controller);

        // CORREÇÃO: busca executa imediatamente com o que há no índice,
        // sem esperar a sync terminar
        currentPage = 1;
        performCurrentSearch();

        // Sync roda em paralelo e atualiza os resultados sozinha
        // via refreshCurrentSearch() que já existe no SearchController
        controller.updateMonitoredFolder(path, createSyncCallback());
    }



    /**
     * Cria painel de status com indicador de auto-refresh
     * NOVO MÉTODO
     */
    private JPanel createStatusPanel() {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        // Label principal de status
        statusLabel = new JLabel("📂 Local de busca: C:\\ | Sistema pronto");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        // Painel de indicadores à direita
        JPanel indicatorsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        // Indicador de sincronização (NOVO)
        syncIndicator = new JLabel();
        syncIndicator.setFont(new Font("SansSerif", Font.BOLD, 11));
        syncIndicator.setForeground(new Color(33, 150, 243)); // Azul
        syncIndicator.setVisible(false);

        // Indicador de auto-refresh
        autoRefreshIndicator = new JLabel();
        autoRefreshIndicator.setFont(new Font("SansSerif", Font.BOLD, 11));
        autoRefreshIndicator.setForeground(new Color(76, 175, 80)); // Verde
        autoRefreshIndicator.setVisible(false);

        indicatorsPanel.add(syncIndicator);
        indicatorsPanel.add(autoRefreshIndicator);

        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(indicatorsPanel, BorderLayout.EAST);

        return statusPanel;
    }


    /**
     * Mostra indicador de sincronização
     * NOVO MÉTODO
     */
    private void showSyncIndicator(String message) {
        syncIndicator.setText(message);
        syncIndicator.setVisible(true);
    }


    /**
     * Esconde indicador com cor apropriada
     * MODIFICADO: Trata caso de pasta não indexada
     */
    private void hideSyncIndicator(boolean isWarning) {
        if (isWarning) {
            syncIndicator.setForeground(new Color(255, 152, 0)); // Laranja para aviso
        } else {
            syncIndicator.setForeground(new Color(33, 150, 243)); // Azul normal
        }

        Timer timer = new Timer(5000, e -> { // 5 segundos para avisos
            syncIndicator.setVisible(false);
        });
        timer.setRepeats(false);
        timer.start();
    }


    /**
     * Cria callback para sincronização
     * MODIFICADO: Trata caso de pasta não indexada
     */
    private SearchController.SyncCallback createSyncCallback() {
        return new SearchController.SyncCallback() {
            @Override
            public void onSyncCompleted(SyncService.SyncResult result) {
                SwingUtilities.invokeLater(() -> {
                    // CASO 1: Pasta não indexada
                    if (result.isNotIndexed()) {
                        showSyncIndicator("⚠️ Pasta não indexada - Use 'Indexar'");
                        hideSyncIndicator(true); // Aviso em laranja

                        String statusText = "📂 " + selectedPath +
                                " | ⚠️ Pasta não indexada";
                        statusLabel.setText(statusText);

                        // Limpa resultados se há busca ativa
                        resultsPanel.showMessage(
                                "⚠️ Pasta não está indexada\n\n" +
                                        "Clique em 'Indexar' para indexar esta pasta antes de buscar.",
                                ResultsPanel.MessageType.ERROR
                        );

                        return;
                    }

                    // CASO 2: Pasta indexada com mudanças
                    if (result.hasChanges()) {
                        String message = String.format(
                                "✅ Sincronizado: +%d | ↻%d | -%d",
                                result.getAdded(),
                                result.getUpdated(),
                                result.getDeleted()
                        );
                        showSyncIndicator(message);

                        String statusText = "📂 " + selectedPath + " | " + result.getSummary();
                        String monitoringStatus = controller.getMonitoringStatus();
                        if (!monitoringStatus.isEmpty()) {
                            statusText += " | " + monitoringStatus;
                        }
                        statusLabel.setText(statusText);

                    } else {
                        // CASO 3: Pasta indexada sem mudanças
                        showSyncIndicator("✅ Sincronizado");
                        updateStatusLabel();
                    }

                    hideSyncIndicator(false); // Normal em azul
                });
            }

            @Override
            public void onSyncError(Exception e) {
                SwingUtilities.invokeLater(() -> {
                    showSyncIndicator("❌ Erro na sincronização");
                    hideSyncIndicator(true);
                });
            }
        };
    }

    /**
     * Chamado quando o sistema de arquivos muda
     * NOVO MÉTODO
     */
    private void onFileSystemChanged() {
        // Mostra feedback visual temporário
        showAutoRefreshIndicator();

        System.out.println("🔄 Sistema de arquivos modificado - Auto-refresh ativado");
    }

    /**
     * Mostra indicador visual de auto-refresh
     * NOVO MÉTODO
     */
    private void showAutoRefreshIndicator() {
        autoRefreshIndicator.setText("🔄 Atualizando...");
        autoRefreshIndicator.setVisible(true);

        // Esconde após 2 segundos
        Timer timer = new Timer(2000, e -> {
            autoRefreshIndicator.setVisible(false);
        });
        timer.setRepeats(false);
        timer.start();
    }

    /**
     * NOVO: Cria painel esquerdo com Tree e Favoritos
     */
    private JPanel createLeftPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout(0, 5));

        // Tree de pastas
        treePanel = new FolderTreePanel(favoritesService);
        treePanel.setSelectionListener(this::onFolderSelected);

        // Painel de favoritos
        favoritesPanel = new FavoritesPanel(favoritesService, this);
        favoritesPanel.setSelectionListener(this::onFavoriteSelected);

        // NOVO: Split pane vertical (Tree em cima, Favoritos embaixo)
        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                treePanel, favoritesPanel);
        verticalSplit.setDividerLocation(400); // Tree fica com 400px de altura
        verticalSplit.setResizeWeight(0.67); // 2/3 para tree, 1/3 para favoritos
        verticalSplit.setBorder(BorderFactory.createEmptyBorder());

        leftPanel.add(verticalSplit, BorderLayout.CENTER);
        leftPanel.setPreferredSize(new Dimension(300, 600));

        return leftPanel;
    }

    // Substitua onSearch() existente:
    private void onSearch(String searchTerm, String filter,
                          String sortBy, String sortOrder,
                          int minRating, String tag) {          // NOVO
        currentPage = 1;
        currentSortBy = sortBy;
        currentSortOrder = sortOrder;
        onSearchWithPagination(
                searchTerm,
                searchPanel.getSelectedFilter(),
                searchPanel.getSortBy(),
                searchPanel.getSortOrder(),
                searchPanel.getMinRating(),
                searchPanel.getTagFilter(),
                showSubfolderContents,   // NOVO — passa flag de subpastas
                currentPage,
                paginationPanel.getPageSize()
        );
    }

    public void performCurrentSearch() {
        String searchTerm = searchPanel.getSearchTerm();

        // NOVO: se há termo de busca ativo, esconde o SubFolderPanel
        //       (busca textual é sempre recursiva por intenção do usuário)
        if (searchTerm != null && !searchTerm.isEmpty()) {
            subFolderPanel.hide();
        }

        onSearchWithPagination(
                searchTerm,
                searchPanel.getSelectedFilter(),
                searchPanel.getSortBy(),
                searchPanel.getSortOrder(),
                searchPanel.getMinRating(),
                searchPanel.getTagFilter(),
                showSubfolderContents,   // NOVO — passa flag de subpastas
                currentPage,
                paginationPanel.getPageSize()
        );
    }

    private void onSearchWithPagination(String searchTerm, String filter,
                                        String sortBy, String sortOrder,
                                        int minRating, String tag,boolean includeSubfolders,
                                        int page, int pageSize) {

        String loadingMessage;
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            loadingMessage = "📂 Carregando todos os arquivos...";
        } else {
            loadingMessage = "🔍 Buscando: " + searchTerm + "...";
        }

        resultsPanel.showMessage(loadingMessage, ResultsPanel.MessageType.LOADING);
        paginationPanel.setEnabled(false);

        controller.performSearchWithPagination(
                searchTerm, filter, selectedPath,
                sortBy, sortOrder,
                minRating, tag,
                includeSubfolders,   // NOVO
                page, pageSize,
                new SearchController.PaginatedSearchCallback()  {

                    @Override
                    public void onSearchStarted() {
                    }

                    @Override
                    public void onSearchCompleted(List<FileInfo> results, PaginationInfo pagination) {

                        // NOVO: remove a própria pasta atual dos resultados
                        List<FileInfo> filtered = results.stream()
                                .filter(f -> !f.getPath().equalsIgnoreCase(selectedPath))
                                .toList();

                        if (filtered.isEmpty()) {
                            String emptyMessage;
                            if (searchTerm == null || searchTerm.trim().isEmpty()) {
                                emptyMessage = "📂 Pasta vazia ou não indexada\n\n" +
                                        "Não há arquivos nesta pasta ou ela não foi indexada ainda.";
                            } else {
                                // NOVO: menciona os filtros ativos na mensagem de vazio
                                StringBuilder sb = new StringBuilder("Nenhum arquivo encontrado");
                                sb.append(" para: ").append(searchTerm);
                                if (minRating > 0)
                                    sb.append(" | Rating ≥ ").append(minRating).append("★");
                                if (tag != null && !tag.isEmpty())
                                    sb.append(" | Tag: \"").append(tag).append("\"");
                                emptyMessage = sb.toString();
                            }
                            resultsPanel.showMessage(emptyMessage, ResultsPanel.MessageType.NO_RESULTS);
                            paginationPanel.setEnabled(false);
                        } else {
                            resultsPanel.showResults(filtered);
                            paginationPanel.updatePagination(pagination);
                        }

                        // NOVO: inclui rating e tag no texto de status
                        String sortInfo = getSortDisplayName(sortBy) +
                                " " + (sortOrder.equals("ASC") ? "↑" : "↓");

                        StringBuilder filters = new StringBuilder();
                        if (minRating > 0)
                            filters.append(" | ").append("★".repeat(minRating)).append("+");
                        if (tag != null && !tag.isEmpty())
                            filters.append(" | 🏷️ ").append(tag);

                        String monitoringStatus = controller.getMonitoringStatus();

                        String statusText;
                        if (searchTerm == null || searchTerm.trim().isEmpty()) {
                            statusText = String.format("📂 Listando: %s | Ordem: %s%s | Local: %s",
                                    pagination, sortInfo, filters, selectedPath);
                        } else {
                            statusText = String.format("✅ %s | Ordem: %s%s | Local: %s",
                                    pagination, sortInfo, filters, selectedPath);
                        }

                        if (!monitoringStatus.isEmpty())
                            statusText += " | " + monitoringStatus + " 🔄";

                        statusLabel.setText(statusText);
                    }

                    @Override
                    public void onSearchError(Exception e) {
                        resultsPanel.showMessage("Erro: " + e.getMessage(),
                                ResultsPanel.MessageType.ERROR);
                        paginationPanel.setEnabled(false);
                    }
                });
    }

    /**
     * Converte nome do campo para exibição
     * NOVO MÉTODO
     */
    private String getSortDisplayName(String fieldName) {
        return switch (fieldName) {
            case "name" -> "Nome";
            case "last_modified" -> "Data";
            case "size" -> "Tamanho";
            case "file_type" -> "Tipo";
            case "path" -> "Caminho";
            default -> fieldName;
        };
    }

    private void onIndexRequest() {
        String message = String.format(
                "Deseja indexar a pasta selecionada?\n\n" +
                        "Pasta: %s\n\n" +
                        "Incluir subpastas?",
                selectedPath
        );

        int option = JOptionPane.showConfirmDialog(this,
                message,
                "Indexar Pasta",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (option == JOptionPane.YES_OPTION) {
            indexFolder(true);
        } else if (option == JOptionPane.NO_OPTION) {
            indexFolder(false);
        }
    }

    private void indexFolder(boolean includeSubfolders) {
        controller.indexFolder(selectedPath, includeSubfolders,
                new SearchController.IndexCallback() {
                    @Override
                    public void onIndexCompleted() {
                        statusLabel.setText("✓ Indexação concluída: " + selectedPath);
                    }

                    @Override
                    public void onIndexError(Exception e) {
                        statusLabel.setText("❌ Erro na indexação");
                    }
                });
    }

    private void onFolderSelected(File folder) {
        navigateTo(folder.getAbsolutePath(), true);
        System.out.println("📂 Selecionado: " + folder.getAbsolutePath());
    }

    private void onFavoriteSelected(File folder) {
        if (folder != null && folder.exists() && folder.isDirectory()) {
            navigateTo(folder.getAbsolutePath(), true);
            System.out.println("⭐ Favorito selecionado: " + folder.getAbsolutePath());
        }
    }

    /**
     * Atualiza label de status com informação de monitoramento
     * NOVO MÉTODO
     */
    private void updateStatusLabel() {
        String monitoringStatus = controller.getMonitoringStatus();
        String statusText = "📂 Pasta selecionada: " + selectedPath;

        if (!monitoringStatus.isEmpty()) {
            statusText += " | " + monitoringStatus;
        }

        statusLabel.setText(statusText);
    }

    /**
     * Mensagem de boas-vindas
     * MODIFICADO: Menciona busca vazia
     */
    private void showWelcomeMessage() {
        resultsPanel.showMessage(
                "Sistema de Busca Avançada\n" +
                        "Selecione uma pasta e clique em 'Indexar'\n\n" +
                        "💡 Recursos Ativos:\n" +
                        "   🔄 Sincronização automática (apenas pastas indexadas)\n" +
                        "   📡 Monitoramento em tempo real\n" +
                        "   ⚡ Auto-refresh de resultados\n" +
                        "   📂 Busca vazia = Lista TODOS os arquivos\n" +
                        "   ✅ Índice sempre atualizado",
                ResultsPanel.MessageType.WELCOME
        );
    }

    @Override
    public void dispose() {
        try {
            controller.close();
        } catch (SQLException e) {
            System.err.println("Erro ao fechar: " + e.getMessage());
        }
        super.dispose();
    }


}