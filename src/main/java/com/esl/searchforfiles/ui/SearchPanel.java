package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.configuration.UIConfig;
import com.esl.searchforfiles.configuration.WrapLayout;
import com.esl.searchforfiles.model.OrderBy;
import com.esl.searchforfiles.model.SortOption;
import com.esl.searchforfiles.others.ThumbnailSize;
import com.esl.searchforfiles.actions.fileTransfer.TransferService;
import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Map;
import java.util.Objects;

/**
 * Painel de busca com campo de texto, ordenação, filtro e botões
 * ATUALIZADO: Controles de ordenação adicionados
 */
public class SearchPanel extends JPanel {

    private final JTextField searchField;
    private final JComboBox<SortOption> sortByCombo; // NOVO
    private final JComboBox<OrderBy> sortOrderCombo; // NOVO
    private final JComboBox<String> filterBox;
    private final JButton searchButton;
    private final JButton indexButton;
    private final JButton transferButton;
    private final JButton editModeBtn;
    private final JButton renameFilesBtn;
    private final JButton renameFoldersBtn;
    private final JButton configurationBtn;

    // NOVO ▼
    private final JComboBox<String> ratingFilterCombo;   // "Qualquer", "1+", "2+", …, "5"
    private final JTextField tagFilterField;       // texto livre de tag
    private final JButton backButton;
    private final JButton forwardButton;
    private final JComboBox<ThumbnailSize> thumbSizeCombo; // NOVO
    private final FileExplorerSwing fileExplorerSwing;
    private final TransferService transferService = new TransferService();
    private NavigationListener navigationListener;
    private SearchListener searchListener;
    private IndexListener indexListener;
    private ThumbnailSizeListener thumbSizeListener;
    private boolean isDark;// NOVO
    private Icon searchIcon;

    public SearchPanel(FileExplorerSwing fileExplorerSwing) {
        this.fileExplorerSwing = fileExplorerSwing;

        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

        isDark = UIManager.getBoolean("laf.dark");
        // Campo de busca
        searchIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/icons/text/search_icon16.png")));
        searchField = createSearchField();

        // NOVO: botões de navegação ─────────────────────────────────
        backButton = new JButton("◀");
        backButton.setFont(UIConfig.FONT_DEFAULT_BOLD);
        backButton.setToolTipText("Voltar (Alt+←)");
        backButton.setEnabled(false);
        backButton.setPreferredSize(new Dimension(36, 25));
        backButton.addActionListener(e -> {
            if (navigationListener != null) navigationListener.onBack();
        });

        forwardButton = new JButton("▶");
        forwardButton.setFont(UIConfig.FONT_DEFAULT_BOLD);
        forwardButton.setToolTipText("Avançar (Alt+→)");
        forwardButton.setEnabled(false);
        forwardButton.setPreferredSize(new Dimension(36, 25));
        forwardButton.addActionListener(e -> {
            if (navigationListener != null) navigationListener.onForward();
        });

        // Atalhos de teclado Alt+← e Alt+→
        registerKeyboardShortcuts();

        // NOVO: ComboBox de ordenação
        sortByCombo = new JComboBox<>(SortOption.values());
        //  sortByCombo.setSelectedItem(SortOption.DATE); // Padrão: Nome
        sortByCombo.setSelectedItem(SortOption.fromDisplayName(fileExplorerSwing.getConfigManager().getSavedSortBy())); // Padrão: Nome
        sortByCombo.setFont(UIConfig.FONT_DEFAULT);
        sortByCombo.setToolTipText("Ordenar por");
        sortByCombo.setPreferredSize(new Dimension(160, 25));
        sortByCombo.addActionListener(e -> {
            fileExplorerSwing.getConfigManager().saveSortBy(getSortBy());
            triggerSearch();
        });

        // NOVO: ComboBox de ordem (crescente/decrescente)
        sortOrderCombo = new JComboBox<>(OrderBy.values());
        sortOrderCombo.setSelectedItem(OrderBy.fromDisplayName(fileExplorerSwing.getConfigManager().getSavedOrderBy())); // Padrão: Crescente
        sortOrderCombo.setFont(UIConfig.FONT_DEFAULT);
        sortOrderCombo.setToolTipText("Ordem de classificação");
        sortOrderCombo.setPreferredSize(new Dimension(120, 25));
        sortOrderCombo.addActionListener(e -> {
            fileExplorerSwing.getConfigManager().saveOrderBy(getSortOrder());
            triggerSearch();
        });

        thumbSizeCombo = new JComboBox<>(ThumbnailSize.values());
        thumbSizeCombo.setSelectedItem(ThumbnailSize.fromLabel(fileExplorerSwing.getConfigManager().getSavedThumbnailsSize()));
        thumbSizeCombo.setFont(UIConfig.FONT_DEFAULT);
        thumbSizeCombo.setToolTipText("Tamanho das miniaturas");
        thumbSizeCombo.setPreferredSize(new Dimension(130, 25));
        thumbSizeCombo.addActionListener(e -> {
            if (thumbSizeListener != null)
                System.out.println(getSelectedThumbnailSize());
            fileExplorerSwing.getConfigManager().saveThumbnailsSize(getSelectedThumbnailSize().toString());
            thumbSizeListener.onSizeChanged(getSelectedThumbnailSize());
            // NÃO dispara triggerSearch() — só muda o visual, não a busca
        });


        // ComboBox de filtro por tipo


        filterBox = new JComboBox<>(new String[]{
                "TODOS", "AUDIO", "VIDEO", "IMAGE", "DOCUMENT", "COMPRESSED", "EXECUTABLE", "CONFIGURATION", "FOLDER"
        });
        filterBox.setSelectedItem(fileExplorerSwing.getConfigManager().getSavedFileType());
        filterBox.setFont(UIConfig.FONT_DEFAULT);
        filterBox.setToolTipText("Filtrar por tipo");
        filterBox.setPreferredSize(new Dimension(120, 25));
        filterBox.addActionListener(e -> {
            fileExplorerSwing.getConfigManager().saveFiletype(Objects.requireNonNull(filterBox.getSelectedItem()).toString());
            triggerSearch();

        });

        // Botão de busca
//        searchButton = new JButton("🔍 Buscar");
//        searchButton.setFont(UIConfig.FONT_DEFAULT_BOLD);
        searchButton = makeTextBtn("🔍 Buscar",
                "Confirmar busca",
                "Slider.trackColor",
                "Component.accentColor");
        searchButton.addActionListener(e -> triggerSearch());

        // Botão de indexar
//        indexButton = new JButton("📊 Indexar");
//        indexButton.setFont(UIConfig.FONT_DEFAULT_BOLD);
//        indexButton.setToolTipText("Indexar pasta selecionada");
        indexButton = makeTextBtn("\uD83D\uDCCA Indexar",
                "Indexar pasta selecionada",
                "Slider.trackColor",
                "Component.accentColor");
        indexButton.addActionListener(e -> triggerIndex());

        // NOVO: ComboBox de filtro por estrelas ─────────────────────
        ratingFilterCombo = new JComboBox<>(new String[]{
                "★ Qualquer", "★ 1+", "★★ 2+", "★★★ 3+", "★★★★ 4+", "★★★★★ 5"
        });

//        ratingFilterCombo.setSelectedItem(fileExplorerSwing.getConfigManager().getSavedStarRating());
        ratingFilterCombo.setSelectedItem(
                ratingToComboItem(fileExplorerSwing.getConfigManager().getSavedStarRating())
        );
        ratingFilterCombo.setFont(UIConfig.FONT_DEFAULT);
        ratingFilterCombo.setToolTipText("Filtrar por avaliação mínima");
        ratingFilterCombo.setPreferredSize(new Dimension(120, 25));
        ratingFilterCombo.addActionListener(e -> {
            fileExplorerSwing.getConfigManager().saveStarRating(getMinRating());
            triggerSearch();
        });

        // NOVO: Campo de texto para filtro por tag ──────────────────
        tagFilterField = new JTextField();
        tagFilterField.setFont(UIConfig.FONT_DEFAULT);
        tagFilterField.setToolTipText("Filtrar por tag (ex: ferias)");
        tagFilterField.setPreferredSize(new Dimension(110, 25));
        tagFilterField.addActionListener(e -> triggerSearch());


        transferButton = makeTextBtn("✂️ Selecionar",
                "Ativar modo de transferência de arquivos",
                "Slider.trackColor",
                "Component.accentColor");
        transferButton.addActionListener(e -> fileExplorerSwing.toggleTransferMode());
//        transferButton = new JButton("✂️ Selecionar");
//        transferButton.setFont(UIConfig.FONT_DEFAULT_BOLD);
//        transferButton.setToolTipText("Ativar modo de transferência de arquivos");
//        transferButton.addActionListener(e -> fileExplorerSwing.toggleTransferMode());

//        editModeBtn = new JButton("🖼 Editar imagens");
//        editModeBtn.setFont(UIConfig.FONT_DEFAULT_BOLD);
//        editModeBtn.setToolTipText("Ativar modo de edição de Imagens");
        editModeBtn = makeTextBtn("🖼 Editar imagens",
                "Ativar modo de edição de Imagens",
                "Slider.trackColor",
                "Component.accentColor");
        editModeBtn.addActionListener(e -> fileExplorerSwing.toggleEditMode());


//        renameFilesBtn = new JButton("🗒 Renomear arquivos");
//        renameFilesBtn.setFont(UIConfig.FONT_DEFAULT_BOLD);
//        renameFilesBtn.setToolTipText("Ativar modo para renomear arquivos");
        renameFilesBtn = makeTextBtn("🗒 Renomear arquivos",
                "Ativar modo para renomear arquivos",
                "Slider.trackColor",
                "Component.accentColor");
        renameFilesBtn.addActionListener(e -> fileExplorerSwing.toggleRenameModeFiles());

//        renameFoldersBtn = new JButton("📁 Renomear pastas");
//        renameFoldersBtn.setFont(UIConfig.FONT_DEFAULT_BOLD);
//        renameFoldersBtn.setToolTipText("Ativar modo para renomear pastas");
        renameFoldersBtn = makeTextBtn("📁 Renomear pastas",
                "Ativar modo para renomear arquivos",
                "Slider.trackColor",
                "Component.accentColor");
        renameFoldersBtn.addActionListener(e -> fileExplorerSwing.toggleRenameModeFolders());

//        configurationBtn = new JButton("Configurações");
//        configurationBtn.setFont(UIConfig.FONT_DEFAULT_BOLD);
//        configurationBtn.setToolTipText("Painel de configurações do aplicativo");
        configurationBtn = makeTextBtn("\uD83D\uDD27 Configurações",
                "Painel de configurações do aplicativo",
                "Slider.trackColor",
                "Component.accentColor");
        configurationBtn.addActionListener(e -> fileExplorerSwing.toggleConfigurationMode());


// Envolve todo o conteúdo num painel com WrapLayout
        JPanel wrapPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 5, 4));

// ── Grupo 1: Navegação ────────────────────────────────────────────────────────
        wrapPanel.add(backButton);
        wrapPanel.add(forwardButton);
        wrapPanel.add(makeSeparator());

// ── Grupo 2: Busca ────────────────────────────────────────────────────────────
        searchField.setPreferredSize(new Dimension(280, 28));   // largura mínima razoável
        wrapPanel.add(searchField);
        wrapPanel.add(makeSeparator());

        Font font = UIConfig.FONT_DEFAULT;

// ── Grupo 3: Ordenação ────────────────────────────────────────────────────────
        JLabel typeLabel = new JLabel("Ordenar:");
        typeLabel.setFont(font);
        wrapPanel.add(typeLabel);
        wrapPanel.add(sortByCombo);
        wrapPanel.add(sortOrderCombo);
        wrapPanel.add(makeSeparator());

// ── Grupo 4: Tipo ─────────────────────────────────────────────────────────────
        JLabel filterLabel = new JLabel("Tipo:");
        filterLabel.setFont(font);
        wrapPanel.add(filterLabel);
        wrapPanel.add(filterBox);
        wrapPanel.add(makeSeparator());

// ── Grupo 5: Rating + Tag ─────────────────────────────────────────────────────
        JLabel ratingLabel = new JLabel("Rating:");
        JLabel tagLabel = new JLabel("Tag:");
        ratingLabel.setFont(font);
        tagLabel.setFont(font);
        wrapPanel.add(ratingLabel);
        wrapPanel.add(ratingFilterCombo);
        wrapPanel.add(tagLabel);
        wrapPanel.add(tagFilterField);
        wrapPanel.add(makeSeparator());

// ── Grupo 6: Ícones ───────────────────────────────────────────────────────────
        JLabel iconLabel = new JLabel("Ícones:");
        iconLabel.setFont(font);
        wrapPanel.add(iconLabel);
        wrapPanel.add(thumbSizeCombo);
        wrapPanel.add(makeSeparator());

// ── Grupo 7: Ações ────────────────────────────────────────────────────────────
        wrapPanel.add(transferButton);
        wrapPanel.add(editModeBtn);
        wrapPanel.add(renameFilesBtn);
        wrapPanel.add(renameFoldersBtn);
        wrapPanel.add(searchButton);
        wrapPanel.add(indexButton);
        wrapPanel.add(configurationBtn);

// ── Monta o painel principal com scroll vertical ──────────────────────────────

// ── Monta sem scroll: o WrapLayout recalcula a altura e o BorderLayout
// do pai (FileExplorerSwing) se encarrega de expandir o NORTH automaticamente.
        JPanel outer = new JPanel(new BorderLayout(0, 2));
        outer.add(wrapPanel, BorderLayout.CENTER);
        //  outer.add(breadcrumbLabel, BorderLayout.SOUTH);

        add(outer, BorderLayout.CENTER);

        fileExplorerSwing.getThemeManager().addThemeChangeListener(() ->
                SwingUtilities.invokeLater(() -> {
                    // 1. Atualiza a variável com o estado do novo tema ativo
                    isDark = UIManager.getBoolean("laf.dark");

                    // 2. Cria ou recupera o ícone correto baseado no novo estado
                    Icon iconeAtualizado = isDark ?
                            fileExplorerSwing.getThemeManager().inverterColorIcon(searchIcon) :
                            searchIcon;

                    // 3. Reaplica a propriedade do cliente para atualizar o FlatLaf
                    searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, iconeAtualizado);

                    // 4. Redesenha o componente para garantir que a mudança visual apareça na hora
                    searchField.repaint();
                    searchField.revalidate();
                })
        );
    }

    private JButton makeTextBtn(String text, String toolTipText, String borderColor, String borderHoverColor) {
        Map<String, Object> estiloBotao = Map.of(
                "borderWidth", 2,
                "borderColor",UIManager.getColor(borderColor), // Cor normal
                "hoverBorderColor", UIManager.getColor(borderHoverColor), // Cor ao passar o mouse
                "focusedBorderColor", UIManager.getColor("Slider.trackColor") // Cor se focado (opcional)
        );
        JButton btn = new JButton(text);
        btn.setFont(UIConfig.FONT_DEFAULT_BOLD);
        btn.putClientProperty(FlatClientProperties.STYLE, estiloBotao);
        btn.setToolTipText(toolTipText);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // Mapeamento int → String do combo
    private String ratingToComboItem(int rating) {
        return switch (rating) {
            case 1 -> "★ 1+";
            case 2 -> "★★ 2+";
            case 3 -> "★★★ 3+";
            case 4 -> "★★★★ 4+";
            case 5 -> "★★★★★ 5";
            default -> "★ Qualquer";
        };
    }
    // ── Helpers (adicione como métodos privados na classe) ────────────────────────
    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UIConfig.FONT_XS_SMALL);
        return l;
    }

    private JSeparator makeSeparator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(2, 24));
        return sep;
    }


    // ── API pública nova ──────────────────────────────────────────

    /**
     * Atualiza estado visual dos botões e do breadcrumb.
     */
    public void updateNavigationState(NavigationHistory history) {
        backButton.setEnabled(history.canGoBack());
        forwardButton.setEnabled(history.canGoForward());
    }


    public void setNavigationListener(NavigationListener l) {
        this.navigationListener = l;
    }

    // ── Atalhos de teclado ────────────────────────────────────────
    private void registerKeyboardShortcuts() {
        KeyStroke back = KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK);
        KeyStroke forward = KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK);

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(back, "nav.back");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(forward, "nav.forward");

        getActionMap().put("nav.back", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (backButton.isEnabled() && navigationListener != null)
                    navigationListener.onBack();
            }
        });
        getActionMap().put("nav.forward", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
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

    /**
     * Retorna 0 se "Qualquer", caso contrário o valor mínimo de estrelas.
     */
    public int getMinRating() {
        int idx = ratingFilterCombo.getSelectedIndex();
        return idx; // índice 0 = qualquer; 1 = 1+; … 5 = 5
    }

    /**
     * Retorna a tag digitada, ou "" se vazia.
     */
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

    private JTextField createSearchField() {
        JTextField field = new JTextField();
        field.setFont(UIConfig.FONT_DEFAULT);
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Digite para pesquisar...");
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, isDark ?  fileExplorerSwing.getThemeManager().inverterColorIcon(searchIcon) : searchIcon);
//        String placeholderText = "Digite um nome para pesquisa...";
//        field.setText(placeholderText);
//
//        // Placeholder behavior
//        field.addFocusListener(new FocusAdapter() {
//            @Override
//            public void focusGained(FocusEvent e) {
//                if (field.getText().equals(placeholderText)) {
//                    field.setText("");
//                }
//            }
//
//            @Override
//            public void focusLost(FocusEvent e) {
//                if (field.getText().isEmpty()) {
//                    field.setText(placeholderText);
//                }
//            }
//        });

        field.addActionListener(e -> triggerSearch());

        return field;
    }

    public void clearSearchTerm() {
        searchField.setText("");  // ajuste para o nome real do seu campo
    }

    public void triggerIndex() {
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
        OrderBy selected = (OrderBy) sortOrderCombo.getSelectedItem();
        return selected != null ? selected.getSqlOrder() : "ASC";
    }

    public String getSearchTerm() {
        String text = searchField.getText().trim();
//        if (text.equals("Digite um nome para pesquisa...")) {
//            return "";
//        }
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

    public interface ThumbnailSizeListener {
        void onSizeChanged(ThumbnailSize size);
    }

    // ── Interface ─────────────────────────────────────────────────
    public interface NavigationListener {
        void onBack();

        void onForward();
    }

    /**
     * Interface atualizada com parâmetros de ordenação
     */
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
