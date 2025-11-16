package com.esl.searchforfiles.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Painel de busca com campo de texto, ordenação, filtro e botões
 * ATUALIZADO: Controles de ordenação adicionados
 */
public class SearchPanel extends JPanel {

    private final JTextField searchField;
    private final JComboBox<SortOption> sortByCombo; // NOVO
    private final JComboBox<SortOrder> sortOrderCombo; // NOVO
    private final JComboBox<String> filterBox;
    private final JButton searchButton;
    private final JButton indexButton;

    private SearchListener searchListener;
    private IndexListener indexListener;

//    public SearchPanel() {
//        setLayout(new BorderLayout(5, 5));
//        setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
//
//        // Campo de busca
//        searchField = createSearchField();
//
//        // ComboBox de filtro
//        filterBox = new JComboBox<>(new String[]{
//                "TODOS", "AUDIO", "VIDEO", "IMAGE", "DOCUMENT", "COMPRESSED", "EXECUTABLE", "FOLDER"
//        });
//        filterBox.setFont(new Font("SansSerif", Font.PLAIN, 14));
//
//
//        // Botão de busca
//        searchButton = new JButton("🔍 Buscar");
//        searchButton.setFont(new Font("SansSerif", Font.BOLD, 14));
//        searchButton.addActionListener(e -> triggerSearch());
//
//        // Botão de indexar
//        indexButton = new JButton("📊 Indexar");
//        indexButton.setFont(new Font("SansSerif", Font.BOLD, 14));
//        indexButton.setToolTipText("Indexar pasta selecionada");
//        indexButton.addActionListener(e -> triggerIndex());
//
//        // Layout
//        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
//        searchPanel.add(searchField, BorderLayout.CENTER);
//
//        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
//        rightPanel.add(filterBox);
//        rightPanel.add(searchButton);
//        rightPanel.add(indexButton);
//        searchPanel.add(rightPanel, BorderLayout.EAST);
//
//        add(searchPanel, BorderLayout.CENTER);
//    }
public SearchPanel() {
    setLayout(new BorderLayout(5, 5));
    setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

    // Campo de busca
    searchField = createSearchField();

    // NOVO: ComboBox de ordenação por
    sortByCombo = new JComboBox<>(SortOption.values());
    sortByCombo.setSelectedItem(SortOption.NAME); // Padrão: Nome
    sortByCombo.setFont(new Font("SansSerif", Font.PLAIN, 12));
    sortByCombo.setToolTipText("Ordenar por");
    sortByCombo.setPreferredSize(new Dimension(160, 25));
    sortByCombo.addActionListener(e -> triggerSearch());

    // NOVO: ComboBox de ordem (crescente/decrescente)
    sortOrderCombo = new JComboBox<>(SortOrder.values());
    sortOrderCombo.setSelectedItem(SortOrder.ASC); // Padrão: Crescente
    sortOrderCombo.setFont(new Font("SansSerif", Font.PLAIN, 12));
    sortOrderCombo.setToolTipText("Ordem de classificação");
    sortOrderCombo.setPreferredSize(new Dimension(120, 25));
    sortOrderCombo.addActionListener(e -> triggerSearch());

    // ComboBox de filtro por tipo
    filterBox = new JComboBox<>(new String[]{
            "TODOS", "AUDIO", "VIDEO", "IMAGE", "DOCUMENT", "COMPRESSED", "EXECUTABLE", "FOLDER"
    });
    filterBox.setFont(new Font("SansSerif", Font.PLAIN, 14));
    filterBox.setToolTipText("Filtrar por tipo");
    filterBox.setPreferredSize(new Dimension(120, 25));
    filterBox.addActionListener(e -> triggerSearch());

    // Botão de busca
    searchButton = new JButton("🔍 Buscar");
    searchButton.setFont(new Font("SansSerif", Font.BOLD, 14));
    searchButton.addActionListener(e -> triggerSearch());

    // Botão de indexar
    indexButton = new JButton("📊 Indexar");
    indexButton.setFont(new Font("SansSerif", Font.BOLD, 14));
    indexButton.setToolTipText("Indexar pasta selecionada");
    indexButton.addActionListener(e -> triggerIndex());

    // NOVO: Layout atualizado com controles de ordenação
    JPanel mainPanel = new JPanel(new BorderLayout(5, 5));

    // Painel central: Campo de busca + Ordenação + Filtro
    JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

    // Campo de busca com largura fixa
    searchField.setPreferredSize(new Dimension(500, 25));
    centerPanel.add(searchField);

    // Separador visual
    centerPanel.add(new JLabel(" | "));

    // NOVO: Controles de ordenação
    JLabel sortLabel = new JLabel("Ordenar:");
    sortLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
    centerPanel.add(sortLabel);
    centerPanel.add(sortByCombo);
    centerPanel.add(sortOrderCombo);

    // Separador visual
    centerPanel.add(new JLabel(" | "));

    // Filtro por tipo
    JLabel filterLabel = new JLabel("Tipo:");
    filterLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
    centerPanel.add(filterLabel);
    centerPanel.add(filterBox);

    mainPanel.add(centerPanel, BorderLayout.CENTER);

    // Painel direito: Botões
    JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
    rightPanel.add(searchButton);
    rightPanel.add(indexButton);
    mainPanel.add(rightPanel, BorderLayout.EAST);

    add(mainPanel, BorderLayout.CENTER);
}
    // NOVO: Opções de ordenação
    public enum SortOption {
        NAME("Nome", "name"),
        DATE("Data de Modificação", "last_modified"),
        SIZE("Tamanho", "size"),
        TYPE("Tipo", "file_type"),
        PATH("Caminho", "path");

        private final String displayName;
        private final String fieldName;

        SortOption(String displayName, String fieldName) {
            this.displayName = displayName;
            this.fieldName = fieldName;
        }

        public String getFieldName() {
            return fieldName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    // NOVO: ordenação
    public enum SortOrder {
        ASC("↑ Crescente", "ASC"),
        DESC("↓ Decrescente", "DESC");

        private final String displayName;
        private final String sqlOrder;

        SortOrder(String displayName, String sqlOrder) {
            this.displayName = displayName;
            this.sqlOrder = sqlOrder;
        }

        public String getSqlOrder() {
            return sqlOrder;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }


//    private JTextField createSearchField() {
//        JTextField field = new JTextField();
//        field.setFont(new Font("SansSerif", Font.PLAIN, 14));
//        field.setText("Digite o nome do arquivo (ex: foto, *.pdf, relatorio*)");
//        field.setForeground(Color.WHITE);
//
//        // Placeholder behavior
//        field.addFocusListener(new FocusAdapter() {
//            @Override
//            public void focusGained(FocusEvent e) {
//                if (field.getText().equals("Digite o nome do arquivo (ex: foto, *.pdf, relatorio*)")) {
//                    field.setText("");
//                }
//            }
//
//            @Override
//            public void focusLost(FocusEvent e) {
//                if (field.getText().isEmpty()) {
//                    field.setText("Digite o nome do arquivo (ex: foto, *.pdf, relatorio*)");
//                }
//            }
//        });
//
//        field.addActionListener(e -> triggerSearch());
//
//        return field;
//    }
private JTextField createSearchField() {
    JTextField field = new JTextField();
    field.setFont(new Font("SansSerif", Font.PLAIN, 14));
    field.setText("Digite o nome do arquivo (ex: foto, *.pdf, relatorio*)");
    field.setForeground(Color.WHITE);

    // Placeholder behavior
    field.addFocusListener(new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e) {
            if (field.getText().equals("Digite o nome do arquivo (ex: foto, *.pdf, relatorio*)")) {
                field.setText("");
                field.setForeground(Color.WHITE);
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
//    private void triggerSearch() {
//        if (searchListener != null) {
//            String term = getSearchTerm();
//            String filter = getSelectedFilter();
//            searchListener.onSearch(term, filter);
//        }
//    }
private void triggerSearch() {
    if (searchListener != null) {
        String term = getSearchTerm();
        String filter = getSelectedFilter();
        String sortBy = getSortBy();
        String sortOrder = getSortOrder();

        searchListener.onSearch(term, filter, sortBy, sortOrder);
    }
}
    private void triggerIndex() {
        if (indexListener != null) {
            indexListener.onIndexRequest();
        }
    }
    /**
     * Retorna campo de ordenação selecionado
     * NOVO MÉTODO
     */
    public String getSortBy() {
        SortOption selected = (SortOption) sortByCombo.getSelectedItem();
        return selected != null ? selected.getFieldName() : "name";
    }

    /**
     * Retorna ordem de ordenação selecionada
     * NOVO MÉTODO
     */
    public String getSortOrder() {
        SortOrder selected = (SortOrder) sortOrderCombo.getSelectedItem();
        return selected != null ? selected.getSqlOrder() : "ASC";
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

//    public interface SearchListener {
//        void onSearch(String searchTerm, String filter);
//    }
    /**
     * Interface atualizada com parâmetros de ordenação
     * MODIFICADO
     */
    public interface SearchListener {
        void onSearch(String searchTerm, String filter, String sortBy, String sortOrder);
    }
    public interface IndexListener {
        void onIndexRequest();
    }
}
