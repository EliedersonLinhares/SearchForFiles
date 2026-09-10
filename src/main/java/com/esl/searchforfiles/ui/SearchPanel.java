package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.actions.fileTransfer.TransferService;
import com.esl.searchforfiles.configuration.UIConfig;
import com.esl.searchforfiles.configuration.WrapLayout;
import com.esl.searchforfiles.model.OrderBy;
import com.esl.searchforfiles.model.SortOption;
import com.esl.searchforfiles.others.ThumbnailSize;
import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
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
    private final JButton compressModeBtn;
    private final JButton videoConverterModeBtn;
    private final JButton renameFilesBtn;
    private final JButton renameFoldersBtn;
    private final JButton configurationBtn;
    private final JButton newFilesBtn;

    private final JCheckBox globalSearchCheckbox; // NOVO
    private final JCheckBox showSubfolderContentCheckbox; // NOVO
    private final JCheckBox showRatingCheckbox;         // NOVO
    private final JCheckBox showExtensionTypeCheckbox;  // NOVO
    private final JCheckBox showAnimatedGifCheckbox;     // NOVO

    private JPopupMenu displayMenu;   // NOVO — vira campo
    private JPopupMenu toolsMenu;     // NOVO — vira campo

    // NOVO ▼
    private final JComboBox<String> ratingFilterCombo;   // "Qualquer", "1+", "2+", …, "5"
    private final JTextField tagFilterField;       // texto livre de tag
    private final JButton backButton;
    private final JButton forwardButton;
    private final JComboBox<ThumbnailSize> thumbSizeCombo; // NOVO
    private final FileExplorerSwing fileExplorerSwing;
    private final TransferService transferService = new TransferService();
    private GlobalSearchListener globalSearchListener; // NOVO
    private showSubFolderContentListener showSubfolderContentListener; // NOVO
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
                "ALL", "AUDIO", "VIDEO", "IMAGE", "DOCUMENT", "COMPRESSED", "EXECUTABLE", "CONFIGURATION", "FOLDER"
        });

        // Renderizador para traduzir o que aparece na tela
        filterBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                if (value != null) {
                    String texto = value.toString();
                    // Traduz "ALL" para "Tudo". Você pode adicionar outras traduções aqui se quiser.
                    if ("ALL".equals(texto)) {
                        setText("Tudo");
                    } else if ("AUDIO".equals(texto)) {
                        setText("Audio");
                    } else if ("VIDEO".equals(texto)) {
                        setText("Video");
                    } else if ("IMAGE".equals(texto)) {
                        setText("Imagem");
                    } else if ("DOCUMENT".equals(texto)) {
                        setText("Documento");
                    } else if ("COMPRESSED".equals(texto)) {
                        setText("Compactado");
                    } else if ("EXECUTABLE".equals(texto)) {
                        setText("Executável");
                    } else if ("CONFIGURATION".equals(texto)) {
                        setText("Configuração");
                    } else if ("FOLDER".equals(texto)) {
                        setText("Pasta");
                    } else {
                        setText(texto);
                    }
                }
                return this;
            }
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
        searchButton = makeTextBtn("🔍 Buscar",
                "Confirmar busca",
                "Slider.trackColor",
                "Component.accentColor");
        searchButton.addActionListener(e -> triggerSearch());



        indexButton = makeHighlightedBtn("\uD83D\uDCCA Indexar Pasta", "Indexar pasta selecionada");
        indexButton.addActionListener(e -> triggerIndex());

        // NOVO: ComboBox de filtro por estrelas ─────────────────────
        ratingFilterCombo = new JComboBox<>(new String[]{
                "★ Qualquer", "★ 1+", "★★ 2+", "★★★ 3+", "★★★★ 4+", "★★★★★ 5"
        });

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


        transferButton = makeTextBtn("✂️ Selecionar(S)",
                "Ativar modo de transferência de arquivos",
                "Slider.trackColor",
                "Component.accentColor");
        transferButton.addActionListener(e -> fileExplorerSwing.toggleTransferMode());

        editModeBtn = makeTextBtn("🖼 Editar imagens(E)",
                "Ativar modo de edição de Imagens",
                "Slider.trackColor",
                "Component.accentColor");
        editModeBtn.addActionListener(e -> fileExplorerSwing.toggleEditMode());

        compressModeBtn = makeTextBtn("🖼 Compressão de Arquivos(C)",
                "Ativar modo de compressão de arquivos",
                "Slider.trackColor",
                "Component.accentColor");
        compressModeBtn.addActionListener(e -> fileExplorerSwing.toggleCompressMode());

        videoConverterModeBtn = makeTextBtn("🖼 Conversão de Videos(V)",
                "Ativar modo de conversão de videos",
                "Slider.trackColor",
                "Component.accentColor");
        videoConverterModeBtn.addActionListener(e -> fileExplorerSwing.toggleVideoConverterMode());


        renameFilesBtn = makeTextBtn("🗒 Renomear arquivos(R)",
                "Ativar modo para renomear arquivos",
                "Slider.trackColor",
                "Component.accentColor");
        renameFilesBtn.addActionListener(e -> fileExplorerSwing.toggleRenameModeFiles());


        renameFoldersBtn = makeTextBtn("📁 Renomear pastas(F)",
                "Ativar modo para renomear arquivos",
                "Slider.trackColor",
                "Component.accentColor");
        renameFoldersBtn.addActionListener(e -> fileExplorerSwing.toggleRenameModeFolders());


        configurationBtn = makeTextBtn("\uD83D\uDD27 Configurações",
                "Painel de configurações do aplicativo",
                "Slider.trackColor",
                "Component.accentColor");
        configurationBtn.addActionListener(e -> fileExplorerSwing.toggleConfigurationMode());

        newFilesBtn = makeTextBtn("📁 Nova pasta",
                "Crie uma nova pasta no destino atual",
                "Slider.trackColor",
                "Component.accentColor");
        newFilesBtn.addActionListener(e -> fileExplorerSwing.createNewFolder());


        // NOVO: Checkbox de busca global ──────────────────────────────
        globalSearchCheckbox = new JCheckBox("Buscar em todas as pastas");
        globalSearchCheckbox.setFont(UIConfig.FONT_DEFAULT);
        globalSearchCheckbox.setToolTipText(
                "Quando ativo, busca em TODAS as pastas indexadas, " +
                        "não apenas na pasta atualmente selecionada");
        globalSearchCheckbox.setSelected(fileExplorerSwing.getConfigManager().getSavedShowGlobalItens());
        globalSearchCheckbox.addActionListener(e -> {
            boolean value = globalSearchCheckbox.isSelected();
            fileExplorerSwing.getConfigManager().saveShowGlobalItens(value);
            if (globalSearchListener != null) {
                globalSearchListener.onGlobalSearchChanged(globalSearchCheckbox.isSelected());
            }
        });

        // NOVO: Checkbox de busca global ──────────────────────────────
        showSubfolderContentCheckbox = new JCheckBox("📂 Mostrar conteúdo de sub pastas");
        showSubfolderContentCheckbox.setFont(UIConfig.FONT_DEFAULT);
        showSubfolderContentCheckbox.setSelected(fileExplorerSwing.getConfigManager().getSavedSubfolderItems());
        showSubfolderContentCheckbox.addActionListener(e -> {
            boolean value = showSubfolderContentCheckbox.isSelected();
            fileExplorerSwing.getConfigManager().saveSubfolderItems(value);
            if (showSubfolderContentListener != null) {
                showSubfolderContentListener.onShowSubFolderContentChanged(showSubfolderContentCheckbox.isSelected());
            }

        });

        // NOVO: Checkboxes de exibição (movidos do menu de contexto) ──
        showRatingCheckbox = new JCheckBox("⭐ Avaliação nos ícones");
        showRatingCheckbox.setFont(UIConfig.FONT_DEFAULT);
        showRatingCheckbox.setToolTipText("Mostrar estrelas de avaliação sobre as miniaturas");
        showRatingCheckbox.setSelected(fileExplorerSwing.getConfigManager().getSavedShowStarRating());
        showRatingCheckbox.addActionListener(e ->
                fileExplorerSwing.setShowRatingOverlay(showRatingCheckbox.isSelected()));

        showExtensionTypeCheckbox = new JCheckBox("Tipo de arquivo nos ícones");
        showExtensionTypeCheckbox.setFont(UIConfig.FONT_DEFAULT);
        showExtensionTypeCheckbox.setToolTipText("Mostrar selo do tipo de arquivo sobre as miniaturas");
        showExtensionTypeCheckbox.setSelected(fileExplorerSwing.getConfigManager().getSavedShowTypeFile());
        showExtensionTypeCheckbox.addActionListener(e ->
                fileExplorerSwing.setShowExtensionTypeOverlay(showExtensionTypeCheckbox.isSelected()));

        showAnimatedGifCheckbox = new JCheckBox("Gifs animados");
        showAnimatedGifCheckbox.setFont(UIConfig.FONT_DEFAULT);
        showAnimatedGifCheckbox.setToolTipText("Reproduzir GIFs animados nas miniaturas (em vez de estáticos)");
        showAnimatedGifCheckbox.setSelected(fileExplorerSwing.getConfigManager().getSavedShowAnimatedGif());
        showAnimatedGifCheckbox.addActionListener(e ->
                fileExplorerSwing.setShowAnimatedGif(showAnimatedGifCheckbox.isSelected()));



        registerKeyboardShortcuts();

        // ── NOVO: monta em 3 linhas, cada uma com seu próprio WrapLayout ──
        JPanel row1 = buildNavigationAndSearchRow();
        JPanel row2 = buildFiltersRow();
        //JPanel row3 = buildDisplayAndActionsRow();

        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.add(row1);
        rows.add(row2);
        rows.add(buildDisplayAndActionsRowWithoutIndex());

        JPanel outer = new JPanel(new BorderLayout(10, 2));
        outer.add(rows, BorderLayout.CENTER);
        outer.add(buildIndexButtonPanel(), BorderLayout.EAST);

//        JPanel outer = new JPanel(new BorderLayout(0, 2));
//        outer.add(rows, BorderLayout.CENTER);
        add(outer, BorderLayout.CENTER);



//// Envolve todo o conteúdo num painel com WrapLayout
//        JPanel wrapPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 5, 4));
//
//// ── Grupo 1: Navegação ────────────────────────────────────────────────────────
//        wrapPanel.add(backButton);
//        wrapPanel.add(forwardButton);
//        wrapPanel.add(makeSeparator());
//
//// ── Grupo 2: Busca ────────────────────────────────────────────────────────────
//        searchField.setPreferredSize(new Dimension(280, 28));   // largura mínima razoável
//        wrapPanel.add(searchField);
//        wrapPanel.add(searchButton);
//        wrapPanel.add(globalSearchCheckbox); // NOVO
//        wrapPanel.add(showSubfolderContentCheckbox); // NOVO
//        wrapPanel.add(makeSeparator());
//
//        Font font = UIConfig.FONT_DEFAULT;
//
//
//// ── Grupo 6b: Exibição ────────────────────────────────────────────────────────
//        JLabel viewLabel = new JLabel("Exibição:");
//        viewLabel.setFont(font);
//        wrapPanel.add(viewLabel);
//        wrapPanel.add(showRatingCheckbox);
//        wrapPanel.add(showExtensionTypeCheckbox);
//        wrapPanel.add(showAnimatedGifCheckbox);
//        wrapPanel.add(makeSeparator());
//
//// ── Grupo 3: Ordenação ────────────────────────────────────────────────────────
//        JLabel typeLabel = new JLabel("Ordenar:");
//        typeLabel.setFont(font);
//        wrapPanel.add(typeLabel);
//        wrapPanel.add(sortByCombo);
//        wrapPanel.add(sortOrderCombo);
//        wrapPanel.add(makeSeparator());
//
//// ── Grupo 4: Tipo ─────────────────────────────────────────────────────────────
//        JLabel filterLabel = new JLabel("Tipo:");
//        filterLabel.setFont(font);
//        wrapPanel.add(filterLabel);
//        wrapPanel.add(filterBox);
//        wrapPanel.add(makeSeparator());
//
//// ── Grupo 5: Rating + Tag ─────────────────────────────────────────────────────
//        JLabel ratingLabel = new JLabel("Rating:");
//        JLabel tagLabel = new JLabel("Tag:");
//        ratingLabel.setFont(font);
//        tagLabel.setFont(font);
//        wrapPanel.add(ratingLabel);
//        wrapPanel.add(ratingFilterCombo);
//        wrapPanel.add(tagLabel);
//        wrapPanel.add(tagFilterField);
//        wrapPanel.add(makeSeparator());
//
//// ── Grupo 6: Ícones ───────────────────────────────────────────────────────────
//        JLabel iconLabel = new JLabel("Ícones:");
//        iconLabel.setFont(font);
//        wrapPanel.add(iconLabel);
//        wrapPanel.add(thumbSizeCombo);
//        wrapPanel.add(makeSeparator());
//
//// ── Grupo 7: Ações ────────────────────────────────────────────────────────────
//        wrapPanel.add(transferButton);
//        wrapPanel.add(editModeBtn);
//        wrapPanel.add(renameFilesBtn);
//        wrapPanel.add(renameFoldersBtn);
//        wrapPanel.add(compressModeBtn);
//        wrapPanel.add(videoConverterModeBtn);
//
//        wrapPanel.add(indexButton);
//        wrapPanel.add(configurationBtn);
//        wrapPanel.add(newFilesBtn);
//
//// ── Monta o painel principal com scroll vertical ──────────────────────────────
//
//// ── Monta sem scroll: o WrapLayout recalcula a altura e o BorderLayout
//// do pai (FileExplorerSwing) se encarrega de expandir o NORTH automaticamente.
//        JPanel outer = new JPanel(new BorderLayout(0, 2));
//        outer.add(wrapPanel, BorderLayout.CENTER);
//        //  outer.add(breadcrumbLabel, BorderLayout.SOUTH);
//
//        add(outer, BorderLayout.CENTER);

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


                    // NOVO: atualiza os popups, que ficam fora da árvore do frame
                    if (displayMenu != null) SwingUtilities.updateComponentTreeUI(displayMenu);
                    if (toolsMenu != null) SwingUtilities.updateComponentTreeUI(toolsMenu);
                })
        );
    }

    private JPanel buildIndexButtonPanel() {
        JPanel panel = new JPanel(new GridBagLayout()); // centraliza verticalmente
        panel.add(indexButton);
        return panel;
    }

    // Linha 3 sem o indexButton — remove essa linha do buildDisplayAndActionsRow():
    private JPanel buildDisplayAndActionsRowWithoutIndex() {
        JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, 5, 4));
        Font font = UIConfig.FONT_DEFAULT;

        // indexButton REMOVIDO daqui — agora fica fixo em outer.EAST
        row.add(configurationBtn);
        row.add(newFilesBtn);
        row.add(makeSeparator());
        row.add(createToolsMenuButton());


        return row;
    }

    // ── Linha 1: Navegação + Busca ──────────────────────────────────────
    private JPanel buildNavigationAndSearchRow() {
        JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, 5, 4));

        row.add(backButton);
        row.add(forwardButton);
        row.add(makeSeparator());

        searchField.setPreferredSize(new Dimension(500, 28));
        row.add(searchField);
        row.add(searchButton);
        row.add(globalSearchCheckbox);
        row.add(showSubfolderContentCheckbox);



        return row;
    }

    // ── Linha 2: Filtros e ordenação ────────────────────────────────────
    private JPanel buildFiltersRow() {
        JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, 5, 4));
        Font font = UIConfig.FONT_DEFAULT;

        JLabel typeLabel = new JLabel("Ordenar:");
        typeLabel.setFont(font);
        row.add(typeLabel);
        sortByCombo.setPreferredSize(new Dimension(160, 28));
        row.add(sortByCombo);
        sortOrderCombo.setPreferredSize(new Dimension(160, 28));
        row.add(sortOrderCombo);
        row.add(makeSeparator());

        JLabel filterLabel = new JLabel("Tipo:");
        filterLabel.setFont(font);
        row.add(filterLabel);
        row.add(filterBox);
        row.add(makeSeparator());

        JLabel ratingLabel = new JLabel("Rating:");
        JLabel tagLabel = new JLabel("Tag:");
        ratingLabel.setFont(font);
        tagLabel.setFont(font);
        row.add(ratingLabel);
        row.add(ratingFilterCombo);
        row.add(tagLabel);
        row.add(tagFilterField);
        row.add(makeSeparator());

        JLabel iconLabel = new JLabel("Ícones:");
        iconLabel.setFont(UIConfig.FONT_DEFAULT);
        row.add(iconLabel);
        row.add(thumbSizeCombo);
        row.add(createDisplayMenuButton());

        return row;
    }

    // ── Linha 3: Exibição + Ferramentas + Ações principais ──────────────
//    private JPanel buildDisplayAndActionsRow() {
//        JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, 5, 4));
//        Font font = UIConfig.FONT_DEFAULT;
//
//        JLabel iconLabel = new JLabel("Ícones:");
//        iconLabel.setFont(font);
//        row.add(iconLabel);
//        row.add(thumbSizeCombo);
//        row.add(makeSeparator());
//
//        row.add(createDisplayMenuButton());
//        row.add(createToolsMenuButton());
//        row.add(makeSeparator());
//
//        //row.add(indexButton);
//        row.add(configurationBtn);
//        row.add(newFilesBtn);
//
//        return row;
//    }

    /**
     * Habilita ou desabilita o botão de indexar conforme a pasta atual
     * já esteja ou não indexada.
     */
    public void setIndexButtonEnabled(boolean enabled) {
        indexButton.setEnabled(enabled);
        indexButton.setToolTipText(enabled
                ? "Indexar pasta selecionada"
                : "Esta pasta já está indexada");
    }


    // ── Dropdown "Exibição ▾" — agrupa os toggles de aparência ──────────
    private JButton createDisplayMenuButton() {
        JButton btn = makeHighlightedMediumBtn("Exibição ▾", "Opções de exibição das miniaturas",120,35);

        displayMenu = new JPopupMenu(); // NOVO: atribui ao campo
        displayMenu.add(wrapAsMenuItem(showRatingCheckbox));
        displayMenu.add(wrapAsMenuItem(showExtensionTypeCheckbox));
        displayMenu.add(wrapAsMenuItem(showAnimatedGifCheckbox));

        btn.addActionListener(e -> displayMenu.show(btn, 0, btn.getHeight()));
        return btn;
    }

    private JButton createToolsMenuButton() {
        JButton btn = makeHighlightedMediumBtn("Ferramentas ▾", "Modos de edição e transferência",150,40);

        toolsMenu = new JPopupMenu(); // NOVO: atribui ao campo
        toolsMenu.add(wrapAsMenuItem(transferButton, "✂️ Selecionar(S)"));
        toolsMenu.add(wrapAsMenuItem(editModeBtn, "🖼 Editar imagens(E)"));
        toolsMenu.add(wrapAsMenuItem(renameFilesBtn, "🗒 Renomear arquivos(R)"));
        toolsMenu.add(wrapAsMenuItem(renameFoldersBtn, "📁 Renomear pastas(F)"));
        toolsMenu.add(wrapAsMenuItem(compressModeBtn, "🖼 Compressão de Arquivos(C)"));
        toolsMenu.add(wrapAsMenuItem(videoConverterModeBtn, "🖼 Conversão de Vídeos(V)"));

        btn.addActionListener(e -> toolsMenu.show(btn, 0, btn.getHeight()));
        return btn;
    }

    /**
     * Converte um JCheckBox já existente (com seu ActionListener já
     * configurado) em um JCheckBoxMenuItem equivalente dentro do popup,
     * mantendo os dois sincronizados.
     */
    private JCheckBoxMenuItem wrapAsMenuItem(JCheckBox source) {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(source.getText(), source.isSelected());
        item.setFont(UIConfig.FONT_DEFAULT);
        item.setToolTipText(source.getToolTipText());
        item.addActionListener(e -> {
            source.setSelected(item.isSelected());
            // Dispara os ActionListeners já registrados no checkbox original
            for (ActionListener al : source.getActionListeners()) {
                al.actionPerformed(e);
            }
        });
        return item;
    }

    /**
     * Converte um JButton de "modo" (toggle) existente em um item de menu,
     * reaproveitando o mesmo ActionListener — sem duplicar lógica de toggle.
     */
    private JMenuItem wrapAsMenuItem(JButton source, String label) {
        JMenuItem item = new JMenuItem(label);
        item.setFont(UIConfig.FONT_DEFAULT);
        item.setToolTipText(source.getToolTipText());
        item.addActionListener(e -> {
            for (ActionListener al : source.getActionListeners()) {
                al.actionPerformed(e);
            }
        });
        return item;
    }



    private JButton makeTextBtn(String text, String toolTipText, String borderColor, String borderHoverColor) {
        Map<String, Object> styleButton = Map.of(
                "borderWidth", 2,
                "borderColor", UIManager.getColor(borderColor), // Cor normal
                "hoverBorderColor", UIManager.getColor(borderHoverColor), // Cor ao passar o mouse
                "focusedBorderColor", UIManager.getColor("Slider.trackColor") // Cor se focado (opcional)
        );
        JButton btn = new JButton(text);
        btn.setFont(UIConfig.FONT_DEFAULT_BOLD);
        btn.putClientProperty(FlatClientProperties.STYLE, styleButton);
        btn.setToolTipText(toolTipText);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton makeHighlightedBtn(String text, String toolTipText) {
        Map<String, Object> styleButton = Map.of(
                "borderWidth", 3,
                "borderColor", UIManager.getColor("Component.accentColor"),
                "hoverBorderColor", UIManager.getColor("Component.accentColor").brighter(),
                "focusedBorderColor", UIManager.getColor("Component.accentColor"),
                "background", UIManager.getColor("Component.accentColor"),
                "foreground", Color.WHITE
        );
        JButton btn = new JButton(text);
        btn.setFont(UIConfig.FONT_TITLE);
        btn.putClientProperty(FlatClientProperties.STYLE, styleButton);
        btn.setToolTipText(toolTipText);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(280, 40));
        return btn;
    }

    private JButton makeHighlightedMediumBtn(String text, String toolTipText, int width,int height) {
        Map<String, Object> styleButton = Map.of(
                "borderWidth", 3,
                "borderColor", UIManager.getColor("Component.accentColor"),
                "hoverBorderColor", UIManager.getColor("Component.accentColor").brighter(),
                "focusedBorderColor", UIManager.getColor("Component.accentColor")
        );
        JButton btn = new JButton(text);
        btn.setFont(UIConfig.FONT_DEFAULT_BOLD);
        btn.putClientProperty(FlatClientProperties.STYLE, styleButton);
        btn.setToolTipText(toolTipText);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(width, height));
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
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, isDark ? fileExplorerSwing.getThemeManager().inverterColorIcon(searchIcon) : searchIcon);
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

    public void setGlobalSearchListener(GlobalSearchListener listener) {
        this.globalSearchListener = listener;
    }

    public void setShowSubfolderContentListener(showSubFolderContentListener showSubfolderContentListener) {
        this.showSubfolderContentListener = showSubfolderContentListener;
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

    /**
     * Interface para notificar mudança no estado da busca global.
     * NOVA INTERFACE
     */
    public interface GlobalSearchListener {
        void onGlobalSearchChanged(boolean globalSearch);
    }

    public interface showSubFolderContentListener {
        void onShowSubFolderContentChanged(boolean globalSearch);
    }


    private void toggleAnimatedGif(boolean visible) {
        AnimatedGifThumb.setShowAnimatedGif(visible); // ← flag atualizada AQUI
        propagateAnimatedGifVisibility(fileExplorerSwing);        // ← painéis recriam usando a flag já correta
        // NÃO chame FileItemPanel.showAnimatedGif = visible — remova se existir
    }
    private void propagateAnimatedGifVisibility(Component origin) {
        // Sobe até o JFrame para depois descer até o gridPanel
        Container root = SwingUtilities.getAncestorOfClass(JFrame.class, origin);
        if (root == null) return;
        applyToAllAnimatedGif(root);
    }
    private void applyToAllAnimatedGif(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof FileItemPanel fip) {
                fip.applyAnimatedGifVisibility();
            } else if (c instanceof Container inner) {
                applyToAllAnimatedGif(inner);
            }
        }
    }
}
