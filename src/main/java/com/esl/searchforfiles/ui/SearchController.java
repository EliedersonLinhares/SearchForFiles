package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.test.AdvancedFileSearch;
import com.esl.searchforfiles.model.FileInfo;
import com.esl.searchforfiles.model.FileType;
import com.esl.searchforfiles.model.SearchCriteria;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;

/**
 * Controlador responsável pela lógica de busca e indexação
 */
public class SearchController {

    private final AdvancedFileSearch searchSystem;
    private final JFrame parentFrame;

    public SearchController(JFrame parentFrame) throws SQLException {
        this.parentFrame = parentFrame;
        this.searchSystem = new AdvancedFileSearch();
        System.out.println("✓ Sistema de busca inicializado");
    }

    /**
     * Executa busca de forma assíncrona
     */
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
                    System.out.println("✓ Encontrados: " + results.size() + " arquivos");
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
}