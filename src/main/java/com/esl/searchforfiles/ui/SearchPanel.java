package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.others.ThumbnailSize;

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

    // NOVO ▼
    private final JComboBox<String> ratingFilterCombo;   // "Qualquer", "1+", "2+", …, "5"
    private final JTextField        tagFilterField;       // texto livre de tag
    private final JButton  backButton;
    private final JButton  forwardButton;
    private final JLabel   breadcrumbLabel;   // mostra o caminho atual
    private NavigationListener navigationListener;

    private SearchListener searchListener;
    private IndexListener indexListener;

    private final JComboBox<ThumbnailSize> thumbSizeCombo; // NOVO
    private ThumbnailSizeListener thumbSizeListener;       // NOVO

public SearchPanel() {
    setLayout(new BorderLayout(5, 5));
    setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

    // Campo de busca
    searchField = createSearchField();

    // NOVO: botões de navegação ─────────────────────────────────
    backButton = new JButton("◀");
    backButton.setFont(new Font("SansSerif", Font.BOLD, 13));
    backButton.setToolTipText("Voltar (Alt+←)");
    backButton.setEnabled(false);
    backButton.setPreferredSize(new Dimension(36, 25));
    backButton.addActionListener(e -> {
        if (navigationListener != null) navigationListener.onBack();
    });

    forwardButton = new JButton("▶");
    forwardButton.setFont(new Font("SansSerif", Font.BOLD, 13));
    forwardButton.setToolTipText("Avançar (Alt+→)");
    forwardButton.setEnabled(false);
    forwardButton.setPreferredSize(new Dimension(36, 25));
    forwardButton.addActionListener(e -> {
        if (navigationListener != null) navigationListener.onForward();
    });

    // Breadcrumb — mostra pasta atual abaixo dos controles
    breadcrumbLabel = new JLabel(" ");
    breadcrumbLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
    breadcrumbLabel.setForeground(new Color(150, 150, 150));
    breadcrumbLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 0, 0));

    // Atalhos de teclado Alt+← e Alt+→
    registerKeyboardShortcuts();

    // NOVO: ComboBox de ordenação por
    sortByCombo = new JComboBox<>(SortOption.values());
    sortByCombo.setSelectedItem(SortOption.DATE); // Padrão: Nome
    sortByCombo.setFont(new Font("SansSerif", Font.PLAIN, 12));
    sortByCombo.setToolTipText("Ordenar por");
    sortByCombo.setPreferredSize(new Dimension(160, 25));
    sortByCombo.addActionListener(e -> triggerSearch());

    // NOVO: ComboBox de ordem (crescente/decrescente)
    sortOrderCombo = new JComboBox<>(SortOrder.values());
    sortOrderCombo.setSelectedItem(SortOrder.DESC); // Padrão: Crescente
    sortOrderCombo.setFont(new Font("SansSerif", Font.PLAIN, 12));
    sortOrderCombo.setToolTipText("Ordem de classificação");
    sortOrderCombo.setPreferredSize(new Dimension(120, 25));
    sortOrderCombo.addActionListener(e -> triggerSearch());

    thumbSizeCombo = new JComboBox<>(ThumbnailSize.values());
    thumbSizeCombo.setSelectedItem(ThumbnailSize.MEDIO);
    thumbSizeCombo.setFont(new Font("SansSerif", Font.PLAIN, 12));
    thumbSizeCombo.setToolTipText("Tamanho das miniaturas");
    thumbSizeCombo.setPreferredSize(new Dimension(130, 25));
    thumbSizeCombo.addActionListener(e -> {
        if (thumbSizeListener != null)
            thumbSizeListener.onSizeChanged(getSelectedThumbnailSize());
        // NÃO dispara triggerSearch() — só muda o visual, não a busca
    });


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

    // NOVO: ComboBox de filtro por estrelas ─────────────────────
    ratingFilterCombo = new JComboBox<>(new String[]{
            "★ Qualquer", "★ 1+", "★★ 2+", "★★★ 3+", "★★★★ 4+", "★★★★★ 5"
    });
    ratingFilterCombo.setFont(new Font("SansSerif", Font.PLAIN, 12));
    ratingFilterCombo.setToolTipText("Filtrar por avaliação mínima");
    ratingFilterCombo.setPreferredSize(new Dimension(120, 25));
    ratingFilterCombo.addActionListener(e -> triggerSearch());

    // NOVO: Campo de texto para filtro por tag ──────────────────
    tagFilterField = new JTextField();
    tagFilterField.setFont(new Font("SansSerif", Font.PLAIN, 12));
    tagFilterField.setToolTipText("Filtrar por tag (ex: ferias)");
    tagFilterField.setPreferredSize(new Dimension(110, 25));
    tagFilterField.addActionListener(e -> triggerSearch());


    // NOVO: Layout atualizado com controles de ordenação
    JPanel mainPanel = new JPanel(new BorderLayout(5, 5));

    // Painel central: Campo de busca + Ordenação + Filtro
    JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

    // Botões de nav primeiro
    centerPanel.add(backButton);
    centerPanel.add(forwardButton);
    centerPanel.add(new JSeparator(SwingConstants.VERTICAL) {{
        setPreferredSize(new Dimension(2, 22));
    }});

    // Campo de busca com largura fixa
    searchField.setPreferredSize(new Dimension(360, 25));
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

    // NOVO: bloco de estrelas + tag ─────────────────────────────
    centerPanel.add(new JLabel(" | "));
    centerPanel.add(new JLabel("Rating:"));
    centerPanel.add(ratingFilterCombo);

    centerPanel.add(new JLabel("Tag:"));
    centerPanel.add(tagFilterField);
    //
    centerPanel.add(new JLabel(" | "));
    centerPanel.add(new JLabel("Ícones:"));
    centerPanel.add(thumbSizeCombo);

    mainPanel.add(centerPanel, BorderLayout.CENTER);

    // Painel direito: Botões
    JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
    rightPanel.add(searchButton);
    rightPanel.add(indexButton);
    mainPanel.add(rightPanel, BorderLayout.EAST);

    add(mainPanel, BorderLayout.CENTER);
}

    // ── API pública nova ──────────────────────────────────────────

    /** Atualiza estado visual dos botões e do breadcrumb. */
    public void updateNavigationState(NavigationHistory history) {
        backButton.setEnabled(history.canGoBack());
        forwardButton.setEnabled(history.canGoForward());
        updateBreadcrumb(history.getCurrent());
    }

    /** Atualiza só o breadcrumb (ex: ao selecionar pela árvore). */
    public void updateBreadcrumb(String path) {
        if (path == null) { breadcrumbLabel.setText(" "); return; }

        // Monta breadcrumb estilo "C: › Users › Fotos › Férias"
        String[] parts = path.replace("\\", "/").split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                if (sb.length() > 0) sb.append(" › ");
                sb.append(parts[i].replace(":", ":"));
            }
        }
        breadcrumbLabel.setText(sb.length() > 0 ? sb.toString() : path);
        breadcrumbLabel.setToolTipText(path);
    }

    public void setNavigationListener(NavigationListener l) { this.navigationListener = l; }

    // ── Atalhos de teclado ────────────────────────────────────────
    private void registerKeyboardShortcuts() {
        KeyStroke back    = KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,  InputEvent.ALT_DOWN_MASK);
        KeyStroke forward = KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK);

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(back,    "nav.back");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(forward, "nav.forward");

        getActionMap().put("nav.back",    new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (backButton.isEnabled() && navigationListener != null)
                    navigationListener.onBack();
            }
        });
        getActionMap().put("nav.forward", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (forwardButton.isEnabled() && navigationListener != null)
                    navigationListener.onForward();
            }
        });
    }

    public ThumbnailSize getSelectedThumbnailSize() {
        return (ThumbnailSize) thumbSizeCombo.getSelectedItem();
    }

    public void setThumbnailSizeListener(ThumbnailSizeListener l) {
        this.thumbSizeListener = l;
    }

    public interface ThumbnailSizeListener {
        void onSizeChanged(ThumbnailSize size);
    }

    // ── Interface ─────────────────────────────────────────────────
    public interface NavigationListener {
        void onBack();
        void onForward();
    }


    /** Retorna 0 se "Qualquer", caso contrário o valor mínimo de estrelas. */
    public int getMinRating() {
        int idx = ratingFilterCombo.getSelectedIndex();
        return idx; // índice 0 = qualquer; 1 = 1+; … 5 = 5
    }

    /** Retorna a tag digitada, ou "" se vazia. */
    public String getTagFilter() {
        return tagFilterField.getText().trim();
    }

    // ── disparo de busca ──────────────────────────────────────────
    public void triggerSearch() {
        if (searchListener != null) {
            searchListener.onSearch(
                    getSearchTerm(), getSelectedFilter(),
                    getSortBy(), getSortOrder(),
                    getMinRating(), getTagFilter()
            );
        }
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


private JTextField createSearchField() {
    JTextField field = new JTextField();
    field.setFont(new Font("SansSerif", Font.PLAIN, 14));
    field.setText("Digite o nome do arquivo (ex: foto, *.pdf, relatorio*)");

    // Placeholder behavior
    field.addFocusListener(new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e) {
            if (field.getText().equals("Digite o nome do arquivo (ex: foto, *.pdf, relatorio*)")) {
                field.setText("");
            }
        }

        @Override
        public void focusLost(FocusEvent e) {
            if (field.getText().isEmpty()) {
                field.setText("Digite o nome do arquivo (ex: foto, *.pdf, relatorio*)");
            }
        }
    });

    field.addActionListener(e -> triggerSearch());

    return field;
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
    /**
     * Interface atualizada com parâmetros de ordenação
     * MODIFICADO
     */
//    public interface SearchListener {
//        void onSearch(String searchTerm, String filter, String sortBy, String sortOrder);
//    }
    // ── interface atualizada ──────────────────────────────────────
    public interface SearchListener {
        void onSearch(String searchTerm, String filter,
                      String sortBy, String sortOrder,
                      int minRating, String tag);      // NOVO
    }

    public interface IndexListener {
        void onIndexRequest();
    }
}
