package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.cache.thumbnail.ThumbnailCacheManager;
import com.esl.searchforfiles.model.FileInfo;
import com.esl.searchforfiles.model.PaginationInfo;
import com.esl.searchforfiles.service.FavoritesService;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import javax.swing.*;
import java.util.List;


/**
 * Interface gráfica para o sistema de busca avançada
 * Adaptado do FileExplorer original para usar AdvancedFileSearch
 */
public class FileExplorerSwing extends JFrame {

    private final SearchPanel searchPanel;
    private FolderTreePanel treePanel;
    private FavoritesPanel favoritesPanel; // NOVO
    private final ResultsPanel resultsPanel;
    private final JLabel statusLabel;
    private final SearchController controller;
    private final FavoritesService favoritesService; // NOVO
    private final PaginationPanel paginationPanel;

    private String selectedPath = "C:\\";
    private int currentPage = 1; // NOVO
    private String currentSortBy = "last_modified"; // NOVO
    private String currentSortOrder = "DESC"; // NOVO



    public FileExplorerSwing() {
        super("Advanced File Search - Interface Gráfica");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1400, 800);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        //getContentPane().setBackground(new Color(250, 250, 252));

        favoritesService = new FavoritesService();

        try {
            controller = new SearchController(this);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Erro ao inicializar: " + e.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException(e);
        }

         // === PAINEL SUPERIOR ===
        searchPanel = new SearchPanel();
        searchPanel.setSearchListener(this::onSearch);
        searchPanel.setIndexListener(this::onIndexRequest);
        add(searchPanel, BorderLayout.NORTH);

        // === PAINEL ESQUERDO ===
        JPanel leftPanel = createLeftPanel();

        // === PAINEL CENTRAL ===
        resultsPanel = new ResultsPanel();
        resultsPanel.setBackgroundColor(new Color(45, 45, 45));

        resultsPanel.setFileItemClickListener(new ResultsPanel.FileItemClickListener() {
            @Override
            public void onFileDoubleClick(File file) {
                try {
                    Desktop.getDesktop().open(file);
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(FileExplorerSwing.this,
                            "Erro ao abrir: " + e.getMessage());
                }
            }

            @Override
            public void onFileRightClick(File file, FileInfo fileInfo, Component source, int x, int y) {
                FileContextMenu menu = new FileContextMenu(file, fileInfo, source);
                menu.show(source, x, y);
            }
        });

        // NOVO: Painel de paginação
        paginationPanel = new PaginationPanel();
        paginationPanel.setPaginationListener(new PaginationPanel.PaginationListener() {
            @Override
            public void onPageChanged(int newPage) {
                currentPage = newPage;
                performCurrentSearch();
            }

            @Override
            public void onPageSizeChanged(int newPageSize) {
                currentPage = 1; // Volta para primeira página
                performCurrentSearch();
            }
        });

        // NOVO: Painel central com resultados e paginação
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(resultsPanel, BorderLayout.CENTER);
        centerPanel.add(paginationPanel, BorderLayout.SOUTH);

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                leftPanel, centerPanel);
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0.2);
        add(splitPane, BorderLayout.CENTER);

        // === PAINEL INFERIOR ===
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        statusLabel = new JLabel("📁 Local de busca: C:\\ | Sistema pronto");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusPanel.add(statusLabel, BorderLayout.WEST);
        add(statusPanel, BorderLayout.SOUTH);

        showWelcomeMessage();

        setVisible(true);
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
        favoritesPanel = new FavoritesPanel(favoritesService);
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

    /**
     * NOVO: Handler para seleção de favorito
     */
    private void onFavoriteSelected(File folder) {
        System.out.println(folder != null);
        System.out.println(folder.exists());
        System.out.println(folder.isDirectory());
        if (folder != null && folder.exists() && folder.isDirectory()) {
            selectedPath = folder.getAbsolutePath();

            // NOVO: Atualiza monitoramento automaticamente
            controller.updateMonitoredFolder(selectedPath);

            statusLabel.setText("⭐ Favorito selecionado: " + selectedPath);
            System.out.println("⭐ Favorito selecionado: " + selectedPath);
        }
    }

    /**
     * Executa busca atual com paginação
     */
    private void performCurrentSearch() {
        String searchTerm = searchPanel.getSearchTerm();
        String filter = searchPanel.getSelectedFilter();
        String sortBy = searchPanel.getSortBy(); // NOVO
        String sortOrder = searchPanel.getSortOrder(); // NOVO
        int pageSize = paginationPanel.getPageSize();

        if (searchTerm.isEmpty()) {
            return;
        }

        onSearchWithPagination(searchTerm, filter, sortBy, sortOrder, currentPage, pageSize);
    }


    /**
     * Handler de busca com ordenação
     * MODIFICADO: Recebe sortBy e sortOrder
     */
    private void onSearch(String searchTerm, String filter, String sortBy, String sortOrder) {
        currentPage = 1; // Reset para primeira página
        currentSortBy = sortBy; // NOVO: Armazena ordenação
        currentSortOrder = sortOrder; // NOVO: Armazena ordem

        onSearchWithPagination(searchTerm, filter, sortBy, sortOrder,
                currentPage, paginationPanel.getPageSize());
    }

    /**
     * Executa busca paginada com ordenação
     * MODIFICADO: Adiciona parâmetros de ordenação
     */
    private void onSearchWithPagination(String searchTerm, String filter,
                                        String sortBy, String sortOrder,
                                        int page, int pageSize) {

        resultsPanel.showMessage("🔍 Buscando: " + searchTerm + "...",
                ResultsPanel.MessageType.LOADING);

        paginationPanel.setEnabled(false);

        controller.performSearchWithPagination(searchTerm, filter, selectedPath,
                sortBy, sortOrder, page, pageSize,
                new SearchController.PaginatedSearchCallback() {
                    @Override
                    public void onSearchStarted() {
                        // Já mostrou loading
                    }

                    @Override
                    public void onSearchCompleted(List<FileInfo> results, PaginationInfo pagination) {
                        if (results.isEmpty()) {
                            resultsPanel.showMessage("Nenhum arquivo encontrado para: " + searchTerm,
                                    ResultsPanel.MessageType.NO_RESULTS);
                            paginationPanel.setEnabled(false);
                        } else {
                            resultsPanel.showResults(results);
                            paginationPanel.updatePagination(pagination);
                        }

                        // NOVO: Mostra critério de ordenação no status
                        String sortInfo = getSortDisplayName(sortBy) + " " +
                                (sortOrder.equals("ASC") ? "↑" : "↓");

                        statusLabel.setText(String.format(
                                "✓ %s | Ordem: %s | Local: %s",
                                pagination, sortInfo, selectedPath
                        ));
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
        selectedPath = folder.getAbsolutePath();

        // NOVO: Atualiza monitoramento automaticamente
        controller.updateMonitoredFolder(selectedPath);

        updateStatusLabel();
        System.out.println("📁 Selecionado: " + selectedPath);
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
     * Mostra mensagem de boas-vindas
     */
    private void showWelcomeMessage() {
        resultsPanel.showMessage(
                "Sistema de Busca Avançada\n" +
                        "Selecione uma pasta e clique em 'Indexar'\n\n" +
                        "💡 Dica: O monitoramento automático será ativado\n" +
                        "   quando você selecionar uma pasta específica",
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