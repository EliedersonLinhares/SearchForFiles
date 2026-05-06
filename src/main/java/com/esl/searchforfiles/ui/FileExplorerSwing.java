package com.esl.searchforfiles.ui;


import com.esl.searchforfiles.configuration.ConfigManager;
import com.esl.searchforfiles.model.FileInfo;
import com.esl.searchforfiles.model.PaginationInfo;
import com.esl.searchforfiles.others.ThumbnailSize;
import com.esl.searchforfiles.service.FavoritesService;
import com.esl.searchforfiles.service.IndexFilterService;
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
    private final NavigationHistory navigationHistory = new NavigationHistory();
    private FolderTreePanel treePanel;
    private FavoritesPanel favoritesPanel; // NOVO
    private ConfigManager configManager;
    private BottomIndicatorPanel bottomIndicatorPanel;
    private IndexFilterService indexFilterService;

    private String selectedPath = "C:\\";
    private int currentPage = 1; // NOVO
    private String currentSortBy = "last_modified"; // NOVO
    private String currentSortOrder = "DESC"; // NOVO
    private SubFolderPanel subFolderPanel;           // NOVO
    private boolean showSubfolderContents = false; // NOVO — controlado pelo menu


    public SearchController getController() {
        return controller;
    }
    public BottomIndicatorPanel getBottomIndicatorPanel() {return bottomIndicatorPanel;}


    public FileExplorerSwing() {
        super("Advanced File Search - Interface Gráfica");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1400, 800);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        favoritesService = new FavoritesService();
        configManager = new ConfigManager();
        setSelectedPath(configManager.getSavedDefaultFolder());

        bottomIndicatorPanel = new BottomIndicatorPanel(this);

        indexFilterService = new IndexFilterService();
        // Pastas que NUNCA devem ser indexadas
        indexFilterService.excludeFolder("C:\\Windows");
        indexFilterService.excludeFolder("C:\\Program Files");
        indexFilterService.excludeFolder("C:\\ProgramData");
        indexFilterService.excludeFolder("C:\\Program Files (x86)");

       // Dentro de C:\Projects, indexar SOMENTE as pastas src e docs
        indexFilterService.allowOnly("C:\\Projects\\MeuApp",
                "C:\\Projects\\MeuApp\\src",
                "C:\\Projects\\MeuApp\\docs"
        );

        try {
            controller = new SearchController(this, indexFilterService);
            controller.setFileSystemChangeListener(this::onFileSystemChanged);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Erro ao inicializar: " + e.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException(e);
        }

        // === PAINEL SUPERIOR ===
        searchPanel = new SearchPanel(this);
        resultsPanel = new ResultsPanel(this);
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
        searchPanel.setThumbnailSizeListener(resultsPanel::setThumbnailSize);


        //  add(searchPanel, BorderLayout.NORTH);
        // Envolve o searchPanel num wrapper com BorderLayout para que o
        // WrapLayout interno recalcule a altura e o NORTH se expanda ao fazer wrap.
        JPanel searchWrapper = new JPanel(new BorderLayout());
        searchWrapper.add(searchPanel, BorderLayout.CENTER);
        add(searchWrapper, BorderLayout.NORTH);  // ← wrapper no NORTH, não o searchPanel direto

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
                    try {
                        Desktop.getDesktop().open(file);
                    } catch (IOException | IllegalArgumentException e) {
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
                        }, FileExplorerSwing.this, favoritesService
                );
                menu.show(source, x, y);
            }
        });

        paginationPanel = new PaginationPanel(this);
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
        centerPanel.add(resultsPanel, BorderLayout.CENTER);


        JPanel paginationWrapper = new JPanel(new BorderLayout());
        paginationWrapper.add(paginationPanel, BorderLayout.CENTER);
        centerPanel.add(paginationWrapper, BorderLayout.SOUTH);  // ← wrapper no NORTH, não o searchPanel direto


        //centerPanel.add(paginationPanel, BorderLayout.SOUTH);

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
        JPanel statusPanel = bottomIndicatorPanel.createStatusPanel();
        add(statusPanel, BorderLayout.SOUTH);
        resultsPanel.setThumbnailSize(ThumbnailSize.fromLabel(getConfigManager().getSavedThumbnailsSize()));

        showWelcomeMessage();
        setVisible(true);

        // Define divider do rightSplit após o frame estar visível
        // (só assim getWidth() tem valor real)
        SwingUtilities.invokeLater(() -> {
            int totalWidth = rightSplit.getWidth();
            rightSplit.setDividerLocation((int) (totalWidth * 0.80));
        });

        // MODIFICADO: em vez de performCurrentSearch() direto,
        // passa pelo navigateTo() para que a sincronização aconteça
        // antes de exibir os resultados
        navigateTo(selectedPath, true);  // selectedPath = "C:\" por padrão

    }

    public ResultsPanel getResultsPanel() {
        return resultsPanel;
    }

    public String getSelectedPath() {
        return selectedPath;
    }

    public void setSelectedPath(String selectedPath) {
        this.selectedPath = selectedPath;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Navega para o caminho informado.
     *
     * @param path        Caminho de destino
     * @param pushHistory true  = navegação normal (empurra no histórico)
     *                    false = movimento pelo histórico (back/forward)
     */
    private void navigateTo(String path, boolean pushHistory) {
        selectedPath = path;

        if (pushHistory) navigationHistory.push(path);

        searchPanel.updateNavigationState(navigationHistory);
        bottomIndicatorPanel.showSyncIndicator("🔄 Verificando mudanças...");

        // Subpastas carregam independentemente
        subFolderPanel.loadSubfolders(selectedPath, controller);

        // CORREÇÃO: busca executa imediatamente com o que há no índice,
        // sem esperar a sync terminar
        currentPage = 1;
        performCurrentSearch();

        // Sync roda em paralelo e atualiza os resultados sozinha
        // via refreshCurrentSearch() que já existe no SearchController
        controller.updateMonitoredFolder(path, bottomIndicatorPanel.createSyncCallback(path));
    }
    /**
     * Chamado quando o sistema de arquivos muda
     * NOVO MÉTODO
     */
    private void onFileSystemChanged() {
        // Mostra feedback visual temporário
        bottomIndicatorPanel.showAutoRefreshIndicator();

        System.out.println("🔄 Sistema de arquivos modificado - Auto-refresh ativado");
    }

    /**
     * NOVO: Cria painel esquerdo com Tree e Favoritos
     */
    private JPanel createLeftPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout(0, 5));

        // Tree de pastas
        treePanel = new FolderTreePanel(favoritesService, this);
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
                configManager.getSavedSortBy(),
                searchPanel.getSortOrder(),
                configManager.getSavedStarRating(),
                searchPanel.getTagFilter(),
                showSubfolderContents,   // NOVO — passa flag de subpastas
                currentPage,
                paginationPanel.getPageSize()
        );
    }

    private void onSearchWithPagination(String searchTerm, String filter,
                                        String sortBy, String sortOrder,
                                        int minRating, String tag, boolean includeSubfolders,
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
                new SearchController.PaginatedSearchCallback() {

                    @Override
                    public void onSearchStarted() {
                    }

                    @Override
                    public void onSearchCompleted(List<FileInfo> results, PaginationInfo pagination) {

                        // NOVO: remove a própria pasta atual dos resultados
                        List<FileInfo> filtered = results.stream()
                                .filter(f -> !f.getPath().equalsIgnoreCase(selectedPath))
                                .filter(f -> !f.getExtension().equalsIgnoreCase("ini"))
                                .filter(f -> !f.getExtension().equalsIgnoreCase("db"))
                                .toList();

                        if (filtered.isEmpty()) {
                            String emptyMessage;
                            if (searchTerm == null || searchTerm.trim().isEmpty()) {
                                emptyMessage = "📂 Nenhum arquivo corresponde aos critérios de busca";
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

                            // NOVO: garante foco no painel de resultados
                            // para que as teclas funcionem imediatamente
                            SwingUtilities.invokeLater(resultsPanel::requestFocusInWindow);
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

                        bottomIndicatorPanel.getStatusLabel().setText(statusText);
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
                    public void onIndexCompleted() {bottomIndicatorPanel.getStatusLabel().setText("✓ Indexação concluída: " + selectedPath);}

                    @Override
                    public void onIndexError(Exception e) {bottomIndicatorPanel.getStatusLabel().setText("❌ Erro na indexação");}
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

//    /**
//     * Atualiza label de status com informação de monitoramento
//     * NOVO MÉTODO
//     */
//    private void updateStatusLabel() {
//        String monitoringStatus = controller.getMonitoringStatus();
//        String statusText = "📂 Pasta selecionada: " + selectedPath;
//
//        if (!monitoringStatus.isEmpty()) {
//            statusText += " | " + monitoringStatus;
//        }
//
//        statusLabel.setText(statusText);
//    }

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

    @Override
    public void dispose() {
        try {
            controller.close();
        } catch (SQLException e) {
            System.err.println("Erro ao fechar: " + e.getMessage());
        }
        resultsPanel.dispose();
        super.dispose();
    }


}