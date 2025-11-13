package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.model.FileInfo;

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
    private final FolderTreePanel treePanel;
    private final ResultsPanel resultsPanel;
    private final JLabel statusLabel;
    private final SearchController controller;

    private String selectedPath = "C:\\";

    public FileExplorerSwing() {
        super("Advanced File Search - Interface Gráfica");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1400, 800);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

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

        // === PAINEL ESQUERDO (Tree) ===
        treePanel = new FolderTreePanel();
        treePanel.setSelectionListener(this::onFolderSelected);

        // === PAINEL CENTRAL (Resultados) ===
        resultsPanel = new ResultsPanel();
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

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                treePanel, resultsPanel);
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

        // Mensagem inicial
        showWelcomeMessage();

        setVisible(true);
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

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Usa padrão
        }

        SwingUtilities.invokeLater(() -> {
            System.out.println("╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║  INTERFACE GRÁFICA - Advanced File Search                     ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");
            new FileExplorerSwing();
        });
    }

}