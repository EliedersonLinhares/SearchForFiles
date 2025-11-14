package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.model.FileInfo;
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

    private String selectedPath = "C:\\";

//public FileExplorerSwing() {
//    super("Advanced File Search - Interface Gráfica");
//    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
//    setSize(1400, 800);
//    setLocationRelativeTo(null);
//    setLayout(new BorderLayout(10, 10));
//
//    // NOVO: Define cor de fundo da janela
//    //getContentPane().setBackground(new Color(250, 250, 252));
//
//    // Inicializa controlador
//    try {
//        controller = new SearchController(this);
//    } catch (SQLException e) {
//        JOptionPane.showMessageDialog(this,
//                "Erro ao inicializar: " + e.getMessage(),
//                "Erro", JOptionPane.ERROR_MESSAGE);
//        throw new RuntimeException(e);
//    }
//
//    // === PAINEL SUPERIOR ===
//    searchPanel = new SearchPanel();
//    searchPanel.setSearchListener(this::onSearch);
//    searchPanel.setIndexListener(this::onIndexRequest);
//    add(searchPanel, BorderLayout.NORTH);
//
//    // === PAINEL ESQUERDO (Tree) ===
//    treePanel = new FolderTreePanel();
//    treePanel.setSelectionListener(this::onFolderSelected);
//    treePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 200, 0));
//
//    // === PAINEL CENTRAL (Resultados) ===
//    resultsPanel = new ResultsPanel();
//
//    // NOVO: Define cor de fundo customizada
//  //  resultsPanel.setBackgroundColor(new Color(245, 245, 250)); // Cinza azulado claro
//    // Outras opções de cores:
//    // resultsPanel.setBackgroundColor(new Color(250, 250, 250)); // Cinza quase branco
//     resultsPanel.setBackgroundColor(new Color(45, 45, 45)); // Alice Blue
//    // resultsPanel.setBackgroundColor(new Color(248, 248, 255)); // Ghost White
//    // resultsPanel.setBackgroundColor(new Color(245, 255, 250)); // Mint Cream
//
//    resultsPanel.setFileItemClickListener(new ResultsPanel.FileItemClickListener() {
//        @Override
//        public void onFileDoubleClick(File file) {
//            try {
//                Desktop.getDesktop().open(file);
//            } catch (IOException e) {
//                JOptionPane.showMessageDialog(FileExplorerSwing.this,
//                        "Erro ao abrir: " + e.getMessage());
//            }
//        }
//
//        @Override
//        public void onFileRightClick(File file, FileInfo fileInfo, Component source, int x, int y) {
//            FileContextMenu menu = new FileContextMenu(file, fileInfo, source);
//            menu.show(source, x, y);
//        }
//    });
//
//    // Split pane
//    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
//            treePanel, resultsPanel);
//    splitPane.setDividerLocation(300);
//    splitPane.setResizeWeight(0.2);
//    add(splitPane, BorderLayout.CENTER);
//
//    // === PAINEL INFERIOR (Status) ===
//    JPanel statusPanel = new JPanel(new BorderLayout());
//    statusPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
//    statusLabel = new JLabel("📁 Local de busca: C:\\ | Sistema pronto");
//    statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
//    statusPanel.add(statusLabel, BorderLayout.WEST);
//    add(statusPanel, BorderLayout.SOUTH);
//
//    // Mensagem inicial
//    showWelcomeMessage();
//
//    setVisible(true);
//}
public FileExplorerSwing() {
    super("Advanced File Search - Interface Gráfica");
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    setSize(1400, 800);
    setLocationRelativeTo(null);
    setLayout(new BorderLayout(10, 10));

  //  getContentPane().setBackground(new Color(250, 250, 252));

    // NOVO: Inicializa gerenciador de favoritos
    favoritesService = new FavoritesService();

    // Inicializa controlador
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

    // === PAINEL ESQUERDO (Tree + Favoritos) ===
    JPanel leftPanel = createLeftPanel();

    // === PAINEL CENTRAL (Resultados) ===
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

    // Split pane horizontal
    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            leftPanel, resultsPanel);
    splitPane.setDividerLocation(300);
    splitPane.setResizeWeight(0.2);
    add(splitPane, BorderLayout.CENTER);

    // === PAINEL INFERIOR (Status) ===
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
            statusLabel.setText("⭐ Favorito selecionado: " + selectedPath);
            System.out.println("⭐ Favorito selecionado: " + selectedPath);
        }
    }


    private void onSearch(String searchTerm, String filter) {
        resultsPanel.showMessage("🔍 Buscando: " + searchTerm + "...",
                ResultsPanel.MessageType.LOADING);

        controller.performSearch(searchTerm, filter, selectedPath,
                new SearchController.SearchCallback() {
                    @Override
                    public void onSearchStarted() {
                        // Já mostrou loading acima
                    }

                    @Override
                    public void onSearchCompleted(List<FileInfo> results) {
                        if (results.isEmpty()) {
                            resultsPanel.showMessage("Nenhum arquivo encontrado para: " + searchTerm,
                                    ResultsPanel.MessageType.NO_RESULTS);
                        } else {
                            resultsPanel.showResults(results);
                        }

                        statusLabel.setText(String.format(
                                "✓ Encontrados: %d arquivo(s) | Local: %s",
                                results.size(), selectedPath
                        ));
                    }

                    @Override
                    public void onSearchError(Exception e) {
                        resultsPanel.showMessage("Erro: " + e.getMessage(),
                                ResultsPanel.MessageType.ERROR);
                    }
                });
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
        statusLabel.setText("📁 Pasta selecionada: " + selectedPath);
        System.out.println("📁 Selecionado: " + selectedPath);
    }

    private void showWelcomeMessage() {
        resultsPanel.showMessage("Sistema de Busca Avançada\n" +
                        "Selecione uma pasta e clique em 'Indexar'",
                ResultsPanel.MessageType.WELCOME);
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

//    public static void main(String[] args) {
//        try {
//            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
//        } catch (Exception e) {
//            // Usa padrão
//        }
//
//        SwingUtilities.invokeLater(() -> {
//            System.out.println("╔════════════════════════════════════════════════════════════════╗");
//            System.out.println("║  INTERFACE GRÁFICA - Advanced File Search                     ║");
//            System.out.println("╚════════════════════════════════════════════════════════════════╝");
//            FlatDarkFlatIJTheme.setup();
//            new FileExplorerSwing();
//        });
//    }

}