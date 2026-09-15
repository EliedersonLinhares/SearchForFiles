package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.actions.fileTransfer.TransferService;
import com.esl.searchforfiles.configuration.UIConfig;
import com.esl.searchforfiles.configuration.WrapLayout;
import com.esl.searchforfiles.localization.I18N;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Painel de busca com campo de texto, ordenação, filtro e botões
 * ATUALIZADO: Controles de ordenação adicionados
 */
public class SearchPanel extends JPanel implements I18N.LanguageChangeListener {

    // ── Campos existentes ────────────────────────────────────────
    private final FileExplorerSwing fileExplorerSwing;
    private boolean isDark;
    private ImageIcon searchIcon;
    private JTextField searchField;
    private JButton backButton, forwardButton;
    private JComboBox<SortOption> sortByCombo;
    private JComboBox<OrderBy> sortOrderCombo;
    private JComboBox<ThumbnailSize> thumbSizeCombo;
    private JComboBox<String> filterBox;
    private JButton searchButton, indexButton;
    private JComboBox<String> ratingFilterCombo;
    private JTextField tagFilterField;
    private JButton transferButton, editModeBtn, compressModeBtn,
            videoConverterModeBtn, renameFilesBtn, renameFoldersBtn,
            configurationBtn, newFilesBtn;
    private JCheckBox globalSearchCheckbox, showSubfolderContentCheckbox,
            showRatingCheckbox, showExtensionTypeCheckbox, showAnimatedGifCheckbox;
    private JPopupMenu displayMenu, toolsMenu;

    private NavigationListener navigationListener;
    private ThumbnailSizeListener thumbSizeListener;
    private SearchListener searchListener;
    private IndexListener indexListener;
    private GlobalSearchListener globalSearchListener;
    private showSubFolderContentListener showSubfolderContentListener;

    // ── NOVO: campos para suportar i18n em tempo real ──────────────
    private JLabel sortLabel, typeLabel, ratingLabel, tagLabel, iconLabel;
    private JButton displayMenuBtn, toolsMenuBtn;
    private JCheckBoxMenuItem showRatingMenuItem, showExtensionTypeMenuItem, showAnimatedGifMenuItem;
    private JMenuItem transferMenuItem, editModeMenuItem, renameFilesMenuItem,
            renameFoldersMenuItem, compressModeMenuItem, videoConverterMenuItem;

    private JCheckBox subPanelVisibility;
    private JCheckBoxMenuItem showSubfolderContentMenuItem;

    private JCheckBox leftPanellVisibility;
    private JCheckBoxMenuItem showLeftPanelContentMenuItem;

    private int lastDividerLocation = -1;
    private int lastMainDividerLocation = -1;

    ;

    public SearchPanel(FileExplorerSwing fileExplorerSwing) {
        this.fileExplorerSwing = fileExplorerSwing;



        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

        isDark = UIManager.getBoolean("laf.dark");

        // Campo de busca
        searchIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/icons/text/search_icon16.png")));
        searchField = createSearchField();

        // Botões de navegação
        backButton = new JButton("◀");
        backButton.setFont(UIConfig.FONT_DEFAULT_BOLD);
        backButton.setToolTipText(I18N.get("searchPanel.backButton.tooltip"));
        backButton.setEnabled(false);
        backButton.setPreferredSize(new Dimension(36, 25));
        backButton.addActionListener(e -> {
            if (navigationListener != null) navigationListener.onBack();
        });

        forwardButton = new JButton("▶");
        forwardButton.setFont(UIConfig.FONT_DEFAULT_BOLD);
        forwardButton.setToolTipText(I18N.get("searchPanel.forwardButton.tooltip"));
        forwardButton.setEnabled(false);
        forwardButton.setPreferredSize(new Dimension(36, 25));
        forwardButton.addActionListener(e -> {
            if (navigationListener != null) navigationListener.onForward();
        });

        registerKeyboardShortcuts();

        // ComboBox de ordenação
        sortByCombo = new JComboBox<>(SortOption.values());
        sortByCombo.setToolTipText(I18N.get("searchPanel.sortByCombo.setToolTipText"));
        sortByCombo.setSelectedItem(SortOption.fromDisplayName(fileExplorerSwing.getConfigManager().getSavedSortBy()));
        sortByCombo.setFont(UIConfig.FONT_DEFAULT);
        sortByCombo.setPreferredSize(new Dimension(160, 25));
        sortByCombo.addActionListener(e -> {
            fileExplorerSwing.getConfigManager().saveSortBy(getSortBy());
            triggerSearch();
        });

        // ComboBox de ordem
        sortOrderCombo = new JComboBox<>(OrderBy.values());
        sortOrderCombo.setSelectedItem(OrderBy.fromDisplayName(fileExplorerSwing.getConfigManager().getSavedOrderBy()));
        sortOrderCombo.setFont(UIConfig.FONT_DEFAULT);
        sortOrderCombo.setToolTipText(I18N.get("searchPanel.sortOrderCombo.tooltip"));
        sortOrderCombo.setPreferredSize(new Dimension(120, 25));
        sortOrderCombo.addActionListener(e -> {
            fileExplorerSwing.getConfigManager().saveOrderBy(getSortOrder());
            triggerSearch();
        });

        thumbSizeCombo = new JComboBox<>(ThumbnailSize.values());
        thumbSizeCombo.setSelectedItem(
                ThumbnailSize.fromKey(fileExplorerSwing.getConfigManager().getSavedThumbnailsSize()));

        thumbSizeCombo.setFont(UIConfig.FONT_DEFAULT);
        thumbSizeCombo.setToolTipText(I18N.get("searchPanel.thumbSizeCombo.tooltip"));
        thumbSizeCombo.setPreferredSize(new Dimension(130, 25));
        thumbSizeCombo.addActionListener(e -> {
            if (thumbSizeListener != null)
                System.out.println(getSelectedThumbnailSize());
            fileExplorerSwing.getConfigManager().saveThumbnailsSize(getSelectedThumbnailSize().name());
            thumbSizeListener.onSizeChanged(getSelectedThumbnailSize());
        });

        // ComboBox de filtro por tipo (valores internos continuam em inglês/fixos)
        filterBox = new JComboBox<>(new String[]{
                "ALL", "AUDIO", "VIDEO", "IMAGE", "DOCUMENT", "COMPRESSED", "EXECUTABLE", "CONFIGURATION", "FOLDER"
        });

        // Renderizador traduz o texto exibido via I18N, sem alterar o valor interno
        filterBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                   boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value != null) {
                    String key = "searchPanel.filterBox.item." + value.toString().toLowerCase();
                    setText(I18N.get(key));
                }
                return this;
            }
        });
        filterBox.setSelectedItem(fileExplorerSwing.getConfigManager().getSavedFileType());
        filterBox.setFont(UIConfig.FONT_DEFAULT);
        filterBox.setToolTipText(I18N.get("searchPanel.filterBox.tooltip"));
        filterBox.setPreferredSize(new Dimension(120, 25));
        filterBox.addActionListener(e -> {
            fileExplorerSwing.getConfigManager().saveFiletype(Objects.requireNonNull(filterBox.getSelectedItem()).toString());
            triggerSearch();
        });

        // Botão de busca
        searchButton = makeTextBtn("🔍 " + I18N.get("searchPanel.searchButton.text"),
                I18N.get("searchPanel.searchButton.tooltip"),
                "Slider.trackColor",
                "Component.accentColor");
        searchButton.addActionListener(e -> triggerSearch());

        indexButton = makeHighlightedBtn(I18N.get("searchPanel.indexButton.text"),
                I18N.get("searchPanel.indexButton.tooltip"));
        indexButton.addActionListener(e -> triggerIndex());

        // ComboBox de filtro por estrelas (valor interno = índice, texto traduzido no refreshRatingCombo)
        ratingFilterCombo = new JComboBox<>();
        refreshRatingCombo(); // popula os itens já traduzidos
        ratingFilterCombo.setSelectedIndex(fileExplorerSwing.getConfigManager().getSavedStarRating());
        ratingFilterCombo.setFont(UIConfig.FONT_DEFAULT);
        ratingFilterCombo.setToolTipText(I18N.get("searchPanel.ratingFilterCombo.tooltip"));
        ratingFilterCombo.setPreferredSize(new Dimension(120, 25));
        ratingFilterCombo.addActionListener(e -> {
            fileExplorerSwing.getConfigManager().saveStarRating(getMinRating());
            triggerSearch();
        });

        // Campo de texto para filtro por tag
        tagFilterField = new JTextField();
        tagFilterField.setFont(UIConfig.FONT_DEFAULT);
        tagFilterField.setToolTipText(I18N.get("searchPanel.tagFilterField.tooltip"));
        tagFilterField.setPreferredSize(new Dimension(110, 25));
        tagFilterField.addActionListener(e -> triggerSearch());

        transferButton = makeTextBtn(I18N.get("searchPanel.transferButton.text"),
                I18N.get("searchPanel.transferButton.tooltip"),
                "Slider.trackColor",
                "Component.accentColor");
        transferButton.addActionListener(e -> fileExplorerSwing.toggleTransferMode());

        editModeBtn = makeTextBtn(I18N.get("searchPanel.editModeBtn.text"),
                I18N.get("searchPanel.editModeBtn.tooltip"),
                "Slider.trackColor",
                "Component.accentColor");
        editModeBtn.addActionListener(e -> fileExplorerSwing.toggleEditMode());

        compressModeBtn = makeTextBtn(I18N.get("searchPanel.compressModeBtn.text"),
                I18N.get("searchPanel.compressModeBtn.tooltip"),
                "Slider.trackColor",
                "Component.accentColor");
        compressModeBtn.addActionListener(e -> fileExplorerSwing.toggleCompressMode());

        videoConverterModeBtn = makeTextBtn(I18N.get("searchPanel.videoConverterModeBtn.text"),
                I18N.get("searchPanel.videoConverterModeBtn.tooltip"),
                "Slider.trackColor",
                "Component.accentColor");
        videoConverterModeBtn.addActionListener(e -> fileExplorerSwing.toggleVideoConverterMode());

        renameFilesBtn = makeTextBtn(I18N.get("searchPanel.renameFilesBtn.text"),
                I18N.get("searchPanel.renameFilesBtn.tooltip"),
                "Slider.trackColor",
                "Component.accentColor");
        renameFilesBtn.addActionListener(e -> fileExplorerSwing.toggleRenameModeFiles());

        renameFoldersBtn = makeTextBtn(I18N.get("searchPanel.renameFoldersBtn.text"),
                I18N.get("searchPanel.renameFoldersBtn.tooltip"),
                "Slider.trackColor",
                "Component.accentColor");
        renameFoldersBtn.addActionListener(e -> fileExplorerSwing.toggleRenameModeFolders());

        configurationBtn = makeTextBtn(I18N.get("searchPanel.configurationBtn.text"),
                I18N.get("searchPanel.configurationBtn.tooltip"),
                "Slider.trackColor",
                "Component.accentColor");
        configurationBtn.addActionListener(e -> fileExplorerSwing.toggleConfigurationMode());

        newFilesBtn = makeTextBtn(I18N.get("searchPanel.newFilesBtn.text"),
                I18N.get("searchPanel.newFilesBtn.tooltip"),
                "Slider.trackColor",
                "Component.accentColor");
        newFilesBtn.addActionListener(e -> fileExplorerSwing.createNewFolder());

        // Checkbox de busca global
        globalSearchCheckbox = new JCheckBox(I18N.get("searchPanel.globalSearchCheckbox.text"));
        globalSearchCheckbox.setFont(UIConfig.FONT_DEFAULT);
        globalSearchCheckbox.setToolTipText(I18N.get("searchPanel.globalSearchCheckbox.tooltip"));
        globalSearchCheckbox.setSelected(fileExplorerSwing.getConfigManager().getSavedShowGlobalItens());
        globalSearchCheckbox.addActionListener(e -> {
            boolean value = globalSearchCheckbox.isSelected();
            fileExplorerSwing.getConfigManager().saveShowGlobalItens(value);
            if (globalSearchListener != null) {
                globalSearchListener.onGlobalSearchChanged(globalSearchCheckbox.isSelected());
            }
        });

        // Checkbox de conteúdo de subpastas
        showSubfolderContentCheckbox = new JCheckBox(I18N.get("searchPanel.showSubfolderContentCheckbox.text"));
        showSubfolderContentCheckbox.setFont(UIConfig.FONT_DEFAULT);
        showSubfolderContentCheckbox.setSelected(fileExplorerSwing.getConfigManager().getSavedSubfolderItems());
        showSubfolderContentCheckbox.addActionListener(e -> {
            boolean value = showSubfolderContentCheckbox.isSelected();
            fileExplorerSwing.getConfigManager().saveSubfolderItems(value);
            if (showSubfolderContentListener != null) {
                showSubfolderContentListener.onShowSubFolderContentChanged(showSubfolderContentCheckbox.isSelected());
            }
        });

        // Checkboxes de exibição
        showRatingCheckbox = new JCheckBox(I18N.get("searchPanel.showRatingCheckbox.text"));
        showRatingCheckbox.setFont(UIConfig.FONT_DEFAULT);
        showRatingCheckbox.setToolTipText(I18N.get("searchPanel.showRatingCheckbox.tooltip"));
        showRatingCheckbox.setSelected(fileExplorerSwing.getConfigManager().getSavedShowStarRating());
        showRatingCheckbox.addActionListener(e ->
                fileExplorerSwing.setShowRatingOverlay(showRatingCheckbox.isSelected()));

        showExtensionTypeCheckbox = new JCheckBox(I18N.get("searchPanel.showExtensionTypeCheckbox.text"));
        showExtensionTypeCheckbox.setFont(UIConfig.FONT_DEFAULT);
        showExtensionTypeCheckbox.setToolTipText(I18N.get("searchPanel.showExtensionTypeCheckbox.tooltip"));
        showExtensionTypeCheckbox.setSelected(fileExplorerSwing.getConfigManager().getSavedShowTypeFile());
        showExtensionTypeCheckbox.addActionListener(e ->
                fileExplorerSwing.setShowExtensionTypeOverlay(showExtensionTypeCheckbox.isSelected()));

        showAnimatedGifCheckbox = new JCheckBox(I18N.get("searchPanel.showAnimatedGifCheckbox.text"));
        showAnimatedGifCheckbox.setFont(UIConfig.FONT_DEFAULT);
        showAnimatedGifCheckbox.setToolTipText(I18N.get("searchPanel.showAnimatedGifCheckbox.tooltip"));
        showAnimatedGifCheckbox.setSelected(fileExplorerSwing.getConfigManager().getSavedShowAnimatedGif());
        showAnimatedGifCheckbox.addActionListener(e ->
                fileExplorerSwing.setShowAnimatedGif(showAnimatedGifCheckbox.isSelected()));

        subPanelVisibility = new JCheckBox(I18N.get("searchPanel.subPanelVisibility"));
        subPanelVisibility.setFont(UIConfig.FONT_DEFAULT);
        subPanelVisibility.setToolTipText(I18N.get("searchPanel.subPanelVisibility.setToolTipText"));
        subPanelVisibility.setSelected(fileExplorerSwing.getConfigManager().getSavedShowRightPanel());
        subPanelVisibility.addActionListener(e -> {
            boolean show = subPanelVisibility.isSelected();
            JSplitPane rightSplit = fileExplorerSwing.getRightSplit();
            if (!show) {
                // Esconder: salva a posição atual antes, empurra o divider pro fim
                fileExplorerSwing.setProgrammaticDividerChange(true);
                try {
                    lastDividerLocation = rightSplit.getDividerLocation();
                    fileExplorerSwing.getSubFolderPanel().setVisible(false);
                    rightSplit.setDividerLocation(rightSplit.getWidth());
                    rightSplit.revalidate();
                    rightSplit.repaint();
                } finally {
                    SwingUtilities.invokeLater(() -> fileExplorerSwing.setProgrammaticDividerChange(false));
                }
            } else {
                // Mostrar: precisa aguardar o layout ter largura real antes de setar o divider
                fileExplorerSwing.getSubFolderPanel().setVisible(true);
                rightSplit.revalidate();
                rightSplit.repaint();

                SwingUtilities.invokeLater(this::restoreRightDividerLocation);
            }
            fileExplorerSwing.getConfigManager().saveShowShowRightPanel(show);

        });

        leftPanellVisibility = new JCheckBox(I18N.get("searchPanel.leftPanellVisibility"));
        leftPanellVisibility.setFont(UIConfig.FONT_DEFAULT);
        leftPanellVisibility.setToolTipText(I18N.get("searchPanel.leftPanellVisibility.setToolTipText"));
        leftPanellVisibility.setSelected(fileExplorerSwing.getConfigManager().getSavedShowLeftPanels());
        leftPanellVisibility.addActionListener(e -> {
            boolean show = leftPanellVisibility.isSelected();

            if (!show) {
                // Esconder: salva a posição atual antes, empurra o divider pro início
                fileExplorerSwing.setProgrammaticMainDividerChange(true);
                try {
                    lastMainDividerLocation =  fileExplorerSwing.getMainSplit().getDividerLocation();
                    fileExplorerSwing.getLeftPanel().setVisible(false);
                    fileExplorerSwing.getMainSplit().setDividerLocation(0);
                    fileExplorerSwing.getMainSplit().revalidate();
                    fileExplorerSwing.getMainSplit().repaint();
                } finally {
                    SwingUtilities.invokeLater(() ->fileExplorerSwing.setProgrammaticMainDividerChange(false));
                }
            } else {
                // Mostrar: precisa aguardar o layout ter largura real antes de setar o divider
                fileExplorerSwing.getLeftPanel().setVisible(true);
                fileExplorerSwing.getMainSplit().revalidate();
                fileExplorerSwing.getMainSplit().repaint();

                SwingUtilities.invokeLater(this::restoreLeftDividerLocation);
            }
            fileExplorerSwing.getConfigManager().saveShowShowLeftPanels(show);
        });

        registerKeyboardShortcuts();

        // Monta em linhas
        JPanel row1 = buildNavigationAndSearchRow();
        JPanel row2 = buildFiltersRow();

        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.add(row1);
        rows.add(row2);
        rows.add(buildDisplayAndActionsRowWithoutIndex());

        JPanel outer = new JPanel(new BorderLayout(10, 2));
        outer.add(rows, BorderLayout.CENTER);
        outer.add(buildIndexButtonPanel(), BorderLayout.EAST);

        add(outer, BorderLayout.CENTER);

        updateTexts();
        I18N.addLanguageChangeListener(this);

        fileExplorerSwing.getThemeManager().addThemeChangeListener(() ->
                SwingUtilities.invokeLater(() -> {
                    isDark = UIManager.getBoolean("laf.dark");

                    Icon iconeAtualizado = isDark ?
                            fileExplorerSwing.getThemeManager().inverterColorIcon(searchIcon) :
                            searchIcon;

                    searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, iconeAtualizado);
                    searchField.repaint();
                    searchField.revalidate();

                    if (displayMenu != null) SwingUtilities.updateComponentTreeUI(displayMenu);
                    if (toolsMenu != null) SwingUtilities.updateComponentTreeUI(toolsMenu);
                })
        );
    }

    private void restoreRightDividerLocation() {
        fileExplorerSwing.setProgrammaticDividerChange(true);
        JSplitPane rightSplit = fileExplorerSwing.getRightSplit();
        try {
            int totalW = rightSplit.getWidth();
            if (totalW <= 0) {
                // layout ainda não computado — tenta de novo no próximo ciclo do EDT
                SwingUtilities.invokeLater(this::restoreRightDividerLocation);
                return;
            }

            int subFolderWidth = lastDividerLocation > 0
                    ? totalW - lastDividerLocation - rightSplit.getDividerSize()
                    :  fileExplorerSwing.getSafeRightSplitPos();

            if (subFolderWidth <= 0) subFolderWidth = 200; // fallback final de segurança

            int divPos = totalW - subFolderWidth - rightSplit.getDividerSize();
            rightSplit.setDividerLocation(Math.max(0, divPos));
        } finally {
            SwingUtilities.invokeLater(() -> fileExplorerSwing.setProgrammaticDividerChange(false));
        }
    }
    private void restoreLeftDividerLocation() {
        fileExplorerSwing.setProgrammaticMainDividerChange(true);
        try {
            if ( fileExplorerSwing.getMainSplit().getWidth() <= 0) {
                SwingUtilities.invokeLater(this::restoreLeftDividerLocation);
                return;
            }

            int restore = lastMainDividerLocation > 0
                    ? lastMainDividerLocation
                    :  fileExplorerSwing.getSafeLeftSplitPos();

            fileExplorerSwing.getMainSplit().setDividerLocation(restore);
        } finally {
            SwingUtilities.invokeLater(() ->fileExplorerSwing.setProgrammaticMainDividerChange(false));
        }
    }

    private JPanel buildIndexButtonPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.add(indexButton);
        return panel;
    }

    private JPanel buildDisplayAndActionsRowWithoutIndex() {
        JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, 5, 4));

        row.add(configurationBtn);
        row.add(newFilesBtn);
        row.add(makeSeparator());
        row.add(createToolsMenuButton());

        return row;
    }

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

    private JPanel buildFiltersRow() {
        JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, 5, 4));
        Font font = UIConfig.FONT_DEFAULT;

        sortLabel = new JLabel(I18N.get("searchPanel.sortLabel.text"));
        sortLabel.setFont(font);
        row.add(sortLabel);
        sortByCombo.setPreferredSize(new Dimension(160, 28));
        row.add(sortByCombo);
        sortOrderCombo.setPreferredSize(new Dimension(160, 28));
        row.add(sortOrderCombo);
        row.add(makeSeparator());

        typeLabel = new JLabel(I18N.get("searchPanel.typeLabel.text"));
        typeLabel.setFont(font);
        row.add(typeLabel);
        row.add(filterBox);
        row.add(makeSeparator());

        ratingLabel = new JLabel(I18N.get("searchPanel.ratingLabel.text"));
        tagLabel = new JLabel(I18N.get("searchPanel.tagLabel.text"));
        ratingLabel.setFont(font);
        tagLabel.setFont(font);
        row.add(ratingLabel);
        row.add(ratingFilterCombo);
        row.add(tagLabel);
        row.add(tagFilterField);
        row.add(makeSeparator());

        iconLabel = new JLabel(I18N.get("searchPanel.iconLabel.text"));
        iconLabel.setFont(UIConfig.FONT_DEFAULT);
        row.add(iconLabel);
        row.add(thumbSizeCombo);
        row.add(createDisplayMenuButton());

        return row;
    }

    public void setIndexButtonEnabled(boolean enabled) {
        indexButton.setEnabled(enabled);
        indexButton.setToolTipText(enabled
                ? I18N.get("searchPanel.indexButton.tooltip")
                : I18N.get("searchPanel.indexButton.tooltip.alreadyIndexed"));
    }

    private JButton createDisplayMenuButton() {
        displayMenuBtn = makeHighlightedMediumBtn(I18N.get("searchPanel.displayMenuBtn.text"),
                I18N.get("searchPanel.displayMenuBtn.tooltip"), 145, 35);

        displayMenu = new JPopupMenu();
        showRatingMenuItem = wrapAsMenuItem(showRatingCheckbox);
        showExtensionTypeMenuItem = wrapAsMenuItem(showExtensionTypeCheckbox);
        showAnimatedGifMenuItem = wrapAsMenuItem(showAnimatedGifCheckbox);

        showSubfolderContentMenuItem = wrapAsMenuItem(subPanelVisibility);
        showLeftPanelContentMenuItem = wrapAsMenuItem(leftPanellVisibility);

        displayMenu.add(showRatingMenuItem);
        displayMenu.add(showExtensionTypeMenuItem);
        displayMenu.add(showAnimatedGifMenuItem);
        displayMenu.add(showSubfolderContentMenuItem);
        displayMenu.add(showLeftPanelContentMenuItem);

        displayMenuBtn.addActionListener(e -> displayMenu.show(displayMenuBtn, 0, displayMenuBtn.getHeight()));
        return displayMenuBtn;
    }

    private JButton createToolsMenuButton() {
        toolsMenuBtn = makeHighlightedMediumBtn(I18N.get("searchPanel.toolsMenuBtn.text"),
                I18N.get("searchPanel.toolsMenuBtn.tooltip"), 155, 40);

        toolsMenu = new JPopupMenu();
        transferMenuItem = wrapAsMenuItem(transferButton, I18N.get("searchPanel.transferButton.text"));
        editModeMenuItem = wrapAsMenuItem(editModeBtn, I18N.get("searchPanel.editModeBtn.text"));
        renameFilesMenuItem = wrapAsMenuItem(renameFilesBtn, I18N.get("searchPanel.renameFilesBtn.text"));
        renameFoldersMenuItem = wrapAsMenuItem(renameFoldersBtn, I18N.get("searchPanel.renameFoldersBtn.text"));
        compressModeMenuItem = wrapAsMenuItem(compressModeBtn, I18N.get("searchPanel.compressModeBtn.text"));
        videoConverterMenuItem = wrapAsMenuItem(videoConverterModeBtn, I18N.get("searchPanel.videoConverterModeBtn.text"));

        toolsMenu.add(transferMenuItem);
        toolsMenu.add(editModeMenuItem);
        toolsMenu.add(renameFilesMenuItem);
        toolsMenu.add(renameFoldersMenuItem);
        toolsMenu.add(compressModeMenuItem);
        toolsMenu.add(videoConverterMenuItem);

        toolsMenuBtn.addActionListener(e -> toolsMenu.show(toolsMenuBtn, 0, toolsMenuBtn.getHeight()));
        return toolsMenuBtn;
    }

    private JCheckBoxMenuItem wrapAsMenuItem(JCheckBox source) {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(source.getText(), source.isSelected());
        item.setFont(UIConfig.FONT_DEFAULT);
        item.setToolTipText(source.getToolTipText());
        item.addActionListener(e -> {
            source.setSelected(item.isSelected());
            for (ActionListener al : source.getActionListeners()) {
                al.actionPerformed(e);
            }
        });
        return item;
    }

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
                "borderColor", UIManager.getColor(borderColor),
                "hoverBorderColor", UIManager.getColor(borderHoverColor),
                "focusedBorderColor", UIManager.getColor("Slider.trackColor")
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

    private JButton makeHighlightedMediumBtn(String text, String toolTipText, int width, int height) {
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

    /**
     * Recria o modelo do combo de estrelas com os textos traduzidos,
     * preservando o índice selecionado (getMinRating() depende do índice, não do texto).
     */
    private void refreshRatingCombo() {
        int selectedIndex = ratingFilterCombo.getSelectedIndex();
        String[] items = {
                I18N.get("searchPanel.rating.any"),
                I18N.get("searchPanel.rating.1"),
                I18N.get("searchPanel.rating.2"),
                I18N.get("searchPanel.rating.3"),
                I18N.get("searchPanel.rating.4"),
                I18N.get("searchPanel.rating.5"),
        };
        ratingFilterCombo.setModel(new DefaultComboBoxModel<>(items));
        if (selectedIndex >= 0) ratingFilterCombo.setSelectedIndex(selectedIndex);
    }

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

    public void updateNavigationState(NavigationHistory history) {
        backButton.setEnabled(history.canGoBack());
        forwardButton.setEnabled(history.canGoForward());
    }

    public void setNavigationListener(NavigationListener l) {
        this.navigationListener = l;
    }

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

    public int getMinRating() {
        return ratingFilterCombo.getSelectedIndex(); // índice 0 = qualquer; 1..5 = estrelas
    }

    public String getTagFilter() {
        return tagFilterField.getText().trim();
    }

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
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                I18N.get("searchPanel.searchField.placeholder"));
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                isDark ? fileExplorerSwing.getThemeManager().inverterColorIcon(searchIcon) : searchIcon);
        field.addActionListener(e -> triggerSearch());

        return field;
    }

    public void clearSearchTerm() {
        searchField.setText("");
    }

    public void triggerIndex() {
        if (indexListener != null) {
            indexListener.onIndexRequest();
        }
    }

    public String getSortBy() {
        SortOption selected = (SortOption) sortByCombo.getSelectedItem();
        return selected != null ? selected.getFieldName() : "name";
    }

    public String getSortOrder() {
        OrderBy selected = (OrderBy) sortOrderCombo.getSelectedItem();
        return selected != null ? selected.getSqlOrder() : "ASC";
    }

    public String getSearchTerm() {
        return searchField.getText().trim();
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

    public interface NavigationListener {
        void onBack();
        void onForward();
    }

    public interface SearchListener {
        void onSearch(String searchTerm, String filter,
                      String sortBy, String sortOrder,
                      int minRating, String tag);
    }

    public interface IndexListener {
        void onIndexRequest();
    }

    public interface GlobalSearchListener {
        void onGlobalSearchChanged(boolean globalSearch);
    }

    public interface showSubFolderContentListener {
        void onShowSubFolderContentChanged(boolean globalSearch);
    }

    private void toggleAnimatedGif(boolean visible) {
        AnimatedGifThumb.setShowAnimatedGif(visible);
        propagateAnimatedGifVisibility(fileExplorerSwing);
    }

    private void propagateAnimatedGifVisibility(Component origin) {
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

    // ── i18n ─────────────────────────────────────────────────────
    private void updateTexts() {

        // Botões de navegação
        backButton.setToolTipText(I18N.get("searchPanel.backButton.tooltip"));
        forwardButton.setToolTipText(I18N.get("searchPanel.forwardButton.tooltip"));

        // Campo de busca
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                I18N.get("searchPanel.searchField.placeholder"));

        // Combos
        sortByCombo.setToolTipText(I18N.get("searchPanel.sortByCombo.setToolTipText"));
        sortOrderCombo.setToolTipText(I18N.get("searchPanel.sortOrderCombo.tooltip"));
        thumbSizeCombo.setToolTipText(I18N.get("searchPanel.thumbSizeCombo.tooltip"));
        filterBox.setToolTipText(I18N.get("searchPanel.filterBox.tooltip"));
        filterBox.repaint(); // reaplica o renderer com os textos traduzidos

        ratingFilterCombo.setToolTipText(I18N.get("searchPanel.ratingFilterCombo.tooltip"));
        refreshRatingCombo();

        tagFilterField.setToolTipText(I18N.get("searchPanel.tagFilterField.tooltip"));

        // Botões de texto
        searchButton.setText(I18N.get("searchPanel.searchButton.text"));
        searchButton.setToolTipText(I18N.get("searchPanel.searchButton.tooltip"));

        indexButton.setText(I18N.get("searchPanel.indexButton.text"));

        transferButton.setText(I18N.get("searchPanel.transferButton.text"));
        transferButton.setToolTipText(I18N.get("searchPanel.transferButton.tooltip"));

        editModeBtn.setText(I18N.get("searchPanel.editModeBtn.text"));
        editModeBtn.setToolTipText(I18N.get("searchPanel.editModeBtn.tooltip"));

        compressModeBtn.setText(I18N.get("searchPanel.compressModeBtn.text"));
        compressModeBtn.setToolTipText(I18N.get("searchPanel.compressModeBtn.tooltip"));

        videoConverterModeBtn.setText(I18N.get("searchPanel.videoConverterModeBtn.text"));
        videoConverterModeBtn.setToolTipText(I18N.get("searchPanel.videoConverterModeBtn.tooltip"));

        renameFilesBtn.setText(I18N.get("searchPanel.renameFilesBtn.text"));
        renameFilesBtn.setToolTipText(I18N.get("searchPanel.renameFilesBtn.tooltip"));

        renameFoldersBtn.setText(I18N.get("searchPanel.renameFoldersBtn.text"));
        renameFoldersBtn.setToolTipText(I18N.get("searchPanel.renameFoldersBtn.tooltip"));

        configurationBtn.setText(I18N.get("searchPanel.configurationBtn.text"));
        configurationBtn.setToolTipText(I18N.get("searchPanel.configurationBtn.tooltip"));

        newFilesBtn.setText(I18N.get("searchPanel.newFilesBtn.text"));
        newFilesBtn.setToolTipText(I18N.get("searchPanel.newFilesBtn.tooltip"));

        // Checkboxes
        globalSearchCheckbox.setText(I18N.get("searchPanel.globalSearchCheckbox.text"));
        globalSearchCheckbox.setToolTipText(I18N.get("searchPanel.globalSearchCheckbox.tooltip"));

        showSubfolderContentCheckbox.setText(I18N.get("searchPanel.showSubfolderContentCheckbox.text"));

        showRatingCheckbox.setText(I18N.get("searchPanel.showRatingCheckbox.text"));
        showRatingCheckbox.setToolTipText(I18N.get("searchPanel.showRatingCheckbox.tooltip"));

        showExtensionTypeCheckbox.setText(I18N.get("searchPanel.showExtensionTypeCheckbox.text"));
        showExtensionTypeCheckbox.setToolTipText(I18N.get("searchPanel.showExtensionTypeCheckbox.tooltip"));

        showAnimatedGifCheckbox.setText(I18N.get("searchPanel.showAnimatedGifCheckbox.text"));
        showAnimatedGifCheckbox.setToolTipText(I18N.get("searchPanel.showAnimatedGifCheckbox.tooltip"));

        subPanelVisibility.setText(I18N.get("searchPanel.subPanelVisibility"));
        subPanelVisibility.setToolTipText(I18N.get("searchPanel.subPanelVisibility.setToolTipText"));

        leftPanellVisibility.setText(I18N.get("searchPanel.leftPanellVisibility"));
        leftPanellVisibility.setToolTipText(I18N.get("searchPanel.leftPanellVisibility.setToolTipText"));

        // Labels soltos
        sortLabel.setText(I18N.get("searchPanel.sortLabel.text"));
        typeLabel.setText(I18N.get("searchPanel.typeLabel.text"));
        ratingLabel.setText(I18N.get("searchPanel.ratingLabel.text"));
        tagLabel.setText(I18N.get("searchPanel.tagLabel.text"));
        iconLabel.setText(I18N.get("searchPanel.iconLabel.text"));

        // Botões que abrem popup
        displayMenuBtn.setText(I18N.get("searchPanel.displayMenuBtn.text"));
        displayMenuBtn.setToolTipText(I18N.get("searchPanel.displayMenuBtn.tooltip"));

        toolsMenuBtn.setText(I18N.get("searchPanel.toolsMenuBtn.text"));
        toolsMenuBtn.setToolTipText(I18N.get("searchPanel.toolsMenuBtn.tooltip"));

        // Itens do displayMenu (copiam o texto já atualizado dos checkboxes)
        showRatingMenuItem.setText(showRatingCheckbox.getText());
        showExtensionTypeMenuItem.setText(showExtensionTypeCheckbox.getText());
        showAnimatedGifMenuItem.setText(showAnimatedGifCheckbox.getText());
        showSubfolderContentMenuItem.setText(subPanelVisibility.getText());
        showLeftPanelContentMenuItem.setText(leftPanellVisibility.getText());


        // Itens do toolsMenu
        transferMenuItem.setText(I18N.get("searchPanel.transferButton.text"));
        editModeMenuItem.setText(I18N.get("searchPanel.editModeBtn.text"));
        renameFilesMenuItem.setText(I18N.get("searchPanel.renameFilesBtn.text"));
        renameFoldersMenuItem.setText(I18N.get("searchPanel.renameFoldersBtn.text"));
        compressModeMenuItem.setText(I18N.get("searchPanel.compressModeBtn.text"));
        videoConverterMenuItem.setText(I18N.get("searchPanel.videoConverterModeBtn.text"));
    }

    @Override
    public void onLanguageChanged(Locale newLocale) {
        updateTexts();
        revalidate();
        repaint();
    }
}
