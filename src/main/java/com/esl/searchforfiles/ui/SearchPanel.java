package com.esl.searchforfiles.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Painel de busca com campo de texto, filtro e botões
 */
public class SearchPanel extends JPanel {

    private final JTextField searchField;
    private final JComboBox<String> filterBox;
    private final JButton searchButton;
    private final JButton indexButton;

    private SearchListener searchListener;
    private IndexListener indexListener;

    public SearchPanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

        // Campo de busca
        searchField = createSearchField();

        // ComboBox de filtro
        filterBox = new JComboBox<>(new String[]{
                "TODOS", "AUDIO", "VIDEO", "IMAGE", "DOCUMENT", "COMPRESSED", "EXECUTABLE", "FOLDER"
        });
        filterBox.setFont(new Font("SansSerif", Font.PLAIN, 14));

        // Botão de busca
        searchButton = new JButton("🔍 Buscar");
        searchButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        searchButton.addActionListener(e -> triggerSearch());

        // Botão de indexar
        indexButton = new JButton("📊 Indexar");
        indexButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        indexButton.setToolTipText("Indexar pasta selecionada");
        indexButton.addActionListener(e -> triggerIndex());

        // Layout
        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        searchPanel.add(searchField, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        rightPanel.add(filterBox);
        rightPanel.add(searchButton);
        rightPanel.add(indexButton);
        searchPanel.add(rightPanel, BorderLayout.EAST);

        add(searchPanel, BorderLayout.CENTER);
    }

    private JTextField createSearchField() {
        JTextField field = new JTextField();
        field.setFont(new Font("SansSerif", Font.PLAIN, 14));
        field.setText("Digite o nome do arquivo (ex: foto, *.pdf, relatorio*)");
        field.setForeground(Color.GRAY);

        // Placeholder behavior
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (field.getText().equals("Digite o nome do arquivo (ex: foto, *.pdf, relatorio*)")) {
                    field.setText("");
                    field.setForeground(Color.BLACK);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (field.getText().isEmpty()) {
                    field.setText("Digite o nome do arquivo (ex: foto, *.pdf, relatorio*)");
                    field.setForeground(Color.GRAY);
                }
            }
        });

        field.addActionListener(e -> triggerSearch());

        return field;
    }

    private void triggerSearch() {
        if (searchListener != null) {
            String term = getSearchTerm();
            String filter = getSelectedFilter();
            searchListener.onSearch(term, filter);
        }
    }

    private void triggerIndex() {
        if (indexListener != null) {
            indexListener.onIndexRequest();
        }
    }

    public String getSearchTerm() {
        String text = searchField.getText().trim();
        if (text.equals("Digite o nome do arquivo (ex: foto, *.pdf, relatorio*)")) {
            return "";
        }
        return text;
    }

    public String getSelectedFilter() {
        return (String) filterBox.getSelectedItem();
    }

    public void setSearchListener(SearchListener listener) {
        this.searchListener = listener;
    }

    public void setIndexListener(IndexListener listener) {
        this.indexListener = listener;
    }

    public interface SearchListener {
        void onSearch(String searchTerm, String filter);
    }

    public interface IndexListener {
        void onIndexRequest();
    }
}
