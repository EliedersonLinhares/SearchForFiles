package com.esl.searchforfiles.ui;


import com.esl.searchforfiles.configuration.ConfigManager;
import com.esl.searchforfiles.actions.fileTransfer.FileTransferHandler;
import com.esl.searchforfiles.actions.fileTransfer.TransferDropHelper;
import com.esl.searchforfiles.configuration.UIConfig;
import com.esl.searchforfiles.localization.I18N;
import com.esl.searchforfiles.model.FileInfo;
import com.esl.searchforfiles.model.FileType;
import com.esl.searchforfiles.model.SearchCriteria;
import com.esl.searchforfiles.service.IconService;
import com.esl.searchforfiles.actions.fileTransfer.TransferService;
import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
public class SubFolderPanel extends JPanel implements I18N.LanguageChangeListener {

    private static final int PAGE_SIZE = 100;

    // ── Ordenação ──────────────────────────────────────────────────
    private String sortField = "name";
    private String sortOrder = "ASC";
    private String currentParentPath;
    private SearchController currentController;

    // ── Dados e paginação ──────────────────────────────────────────
    private List<FileInfo> allFolders   = new ArrayList<>();
    private List<FileInfo> filteredList = new ArrayList<>();
    private int            currentPage  = 0;
    private boolean        resetting    = false;

    // ── UI ─────────────────────────────────────────────────────────
    private final JPanel      listPanel;
    private final JScrollPane scrollPane;
    private final JTextField  searchField;
    private final JLabel      pageLabel;
    private final JLabel      emptyLabel;
    private final JButton     btnFirst, btnPrev, btnNext, btnLast;
    private JButton titleButton;
    private TitledBorder titledBorder;
    private JMenuItem asc;
    private JMenuItem desc;

    private FolderClickListener clickListener;
    private TransferService     transferService;
    private final FileExplorerSwing fileExplorerSwing;
    private final ConfigManager     configManager;

    private Icon searchIcon;
    private boolean isDark;

    public SubFolderPanel(FileExplorerSwing fileExplorerSwing, ConfigManager configManager) {
        this.fileExplorerSwing = fileExplorerSwing;
        this.configManager     = configManager;
        this.sortField = configManager.getSubfolderSortField();
        this.sortOrder = configManager.getSubfolderSortOrder();

        isDark = UIManager.getBoolean("laf.dark");

        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createTitledBorder(I18N.get("subFolderPanel.titledBorder")));
        titledBorder = (TitledBorder) this.getBorder();

        // ── Cabeçalho ──────────────────────────────────────────────
        titleButton = UIConfig.makeHighlightedMediumBtn(
                buildTitle(), I18N.get("subFolderPanel.titleButton.tooltip"), 120, 35);
        titleButton.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) showSortMenu(e);
            }
        });

        searchIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/icons/text/search_icon16.png")));

        // ── Campo de pesquisa ───────────────────────────────────────
        searchField = new JTextField();
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                I18N.get("subFolderPanel.searchField.placeholder"));
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                isDark ? fileExplorerSwing.getThemeManager().inverterColorIcon(searchIcon) : searchIcon);

        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(70, 70, 70)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        searchField.setFont(UIConfig.FONT_DEFAULT);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e)  { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        // ── Barra de paginação ──────────────────────────────────────
        btnFirst = makePageBtn("«");
        btnPrev  = makePageBtn("‹");
        btnNext  = makePageBtn("›");
        btnLast  = makePageBtn("»");

        btnFirst.addActionListener(e -> goToPage(0));
        btnPrev .addActionListener(e -> goToPage(currentPage - 1));
        btnNext .addActionListener(e -> goToPage(currentPage + 1));
        btnLast .addActionListener(e -> goToPage(totalPages() - 1));

        pageLabel = new JLabel("", SwingConstants.CENTER);
        pageLabel.setFont(UIConfig.FONT_SMALL);

        JPanel btnGroup = new JPanel(new GridLayout(1, 4, 2, 0));
        btnGroup.add(btnFirst);
        btnGroup.add(btnPrev);
        btnGroup.add(btnNext);
        btnGroup.add(btnLast);

        JPanel pagBar = new JPanel(new BorderLayout(2, 0));
        pagBar.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
        pagBar.add(pageLabel, BorderLayout.CENTER);
        pagBar.add(btnGroup,  BorderLayout.EAST);

        // ── Painel norte ────────────────────────────────────────────
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.setBackground(UIConfig.background());
        northPanel.add(titleButton,  BorderLayout.NORTH);
        northPanel.add(searchField, BorderLayout.CENTER);
        northPanel.add(pagBar,      BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);

        // ── Mensagem de vazio ───────────────────────────────────────
        emptyLabel = new JLabel(I18N.get("subFolderPanel.emptyLabel.default"), SwingConstants.CENTER);
        emptyLabel.setFont(UIConfig.FONT_SMALL);
        emptyLabel.setVisible(false);

        // ── Lista ───────────────────────────────────────────────────
        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.add(emptyLabel, BorderLayout.NORTH);
        centerWrapper.add(listPanel,  BorderLayout.CENTER);

        scrollPane = new JScrollPane(centerWrapper);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);
        add(scrollPane, BorderLayout.CENTER);

        setPreferredSize(new Dimension(200, 0));
        setTransferHandler(new FileTransferHandler());

        fileExplorerSwing.getThemeManager().addThemeChangeListener(() ->
                SwingUtilities.invokeLater(() -> {
                    isDark = UIManager.getBoolean("laf.dark");
                    Icon iconeAtualizado = isDark ?
                            fileExplorerSwing.getThemeManager().inverterColorIcon(searchIcon) :
                            searchIcon;
                    searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, iconeAtualizado);
                    searchField.repaint();
                    searchField.revalidate();
                })
        );

        updateTexts();
        I18N.addLanguageChangeListener(this);
    }

    // ── API pública ────────────────────────────────────────────────
    public void setParentSplit(JSplitPane split) { /* mantido por compatibilidade */ }
    public void setTransferManager(TransferService tm) { this.transferService = tm; }
    public void setFolderClickListener(FolderClickListener l) { this.clickListener = l; }

    public void loadSubfolders(String parentPath, SearchController controller) {
        this.currentParentPath = parentPath;
        this.currentController = controller;

        resetting = true;
        searchField.setText("");
        resetting = false;

        showEmpty(false);

        SwingWorker<List<FileInfo>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<FileInfo> doInBackground() throws Exception {
                SearchCriteria criteria = new SearchCriteria()
                        .withFileType(FileType.FOLDER)
                        .inPath(parentPath, false)
                        .sortBy(sortField, sortOrder)
                        .limit(5000);
                return controller.searchDirect(criteria);
            }

            @Override
            protected void done() {
                try {
                    List<FileInfo> folders = get().stream()
                            .filter(f -> !f.getPath().equalsIgnoreCase(parentPath))
                            .toList();

                    allFolders   = folders;
                    filteredList = folders;
                    currentPage  = 0;
                    renderCurrentPage();

                } catch (Exception ex) {
                    allFolders   = new ArrayList<>();
                    filteredList = new ArrayList<>();
                    renderCurrentPage();
                }
            }
        };
        worker.execute();
    }

    // ── Paginação ──────────────────────────────────────────────────

    private int totalPages() {
        return Math.max(1, (int) Math.ceil(filteredList.size() / (double) PAGE_SIZE));
    }

    private void goToPage(int page) {
        currentPage = Math.max(0, Math.min(page, totalPages() - 1));
        renderCurrentPage();
    }

    private void renderCurrentPage() {
        listPanel.removeAll();

        if (filteredList.isEmpty()) {
            showEmpty(true);
        } else {
            showEmpty(false);
            int from = currentPage * PAGE_SIZE;
            int to   = Math.min(from + PAGE_SIZE, filteredList.size());
            listPanel.add(Box.createVerticalStrut(4));
            for (FileInfo fi : filteredList.subList(from, to)) {
                listPanel.add(createFolderRow(fi));
                listPanel.add(Box.createVerticalStrut(1));
            }
            listPanel.add(Box.createVerticalGlue());
        }

        listPanel.revalidate();
        listPanel.repaint();
        scrollPane.getVerticalScrollBar().setValue(0);
        updateTitle(filteredList.size());
        updatePagination();
    }

    // ── NOVO: usa chave i18n com placeholder para o termo buscado ────
    private void showEmpty(boolean empty) {
        String q = searchField.getText().trim();
        emptyLabel.setText(empty
                ? (q.isEmpty()
                ? I18N.get("subFolderPanel.emptyLabel.default")
                : I18N.get("subFolderPanel.emptyLabel.noResults").replace("{0}", q))
                : "");
        emptyLabel.setVisible(empty);
        listPanel.setVisible(!empty);
    }

    private void updatePagination() {
        int total     = totalPages();
        boolean multi = total > 1;

        pageLabel.setText(multi ? (currentPage + 1) + "/" + total : "");
        btnFirst.setEnabled(multi && currentPage > 0);
        btnPrev .setEnabled(multi && currentPage > 0);
        btnNext .setEnabled(multi && currentPage < total - 1);
        btnLast .setEnabled(multi && currentPage < total - 1);
        btnFirst.setVisible(multi);
        btnPrev .setVisible(multi);
        btnNext .setVisible(multi);
        btnLast .setVisible(multi);
        pageLabel.setVisible(multi);
    }

    // ── Filtro ─────────────────────────────────────────────────────

    private void applyFilter() {
        if (resetting) return;
        String q = searchField.getText().trim().toLowerCase();
        filteredList = allFolders.stream()
                .filter(f -> f.getName().toLowerCase().contains(q))
                .toList();
        currentPage = 0;
        renderCurrentPage();
        SwingUtilities.invokeLater(searchField::requestFocusInWindow);
    }

    // ── Ordenação ──────────────────────────────────────────────────
    // NOVO: menu recriado a cada clique, então basta usar I18N.get() aqui —
    // não é necessário guardar referência para o language change.
    private void showSortMenu(MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();
        menu.setFont(UIConfig.FONT_DEFAULT);

        String[][] fields = {
                {I18N.get("subFolderPanel.sortField.name"), "name"},
                {I18N.get("subFolderPanel.sortField.date"), "last_modified"},
                {I18N.get("subFolderPanel.sortField.size"), "size"}
        };
        for (String[] f : fields) {
            boolean active = sortField.equals(f[1]);
            JMenuItem item = new JMenuItem((active ? "✓ " : "   ") + f[0]);
            String key = f[1];
            item.addActionListener(ev -> { sortField = key; configManager.saveSubfolderSortField(key); reload(); });
            menu.add(item);
        }

        menu.addSeparator();

        asc  = new JMenuItem((sortOrder.equals("ASC")  ? "✓ " : "   ") + I18N.get("subFolderPanel.sortOrder.asc"));
        desc = new JMenuItem((sortOrder.equals("DESC") ? "✓ " : "   ") + I18N.get("subFolderPanel.sortOrder.desc"));
        styleMenuItem(asc,  sortOrder.equals("ASC"));
        styleMenuItem(desc, sortOrder.equals("DESC"));
        asc .addActionListener(ev -> { sortOrder = "ASC";  configManager.saveSubfolderSortOrder("ASC");  reload(); });
        desc.addActionListener(ev -> { sortOrder = "DESC"; configManager.saveSubfolderSortOrder("DESC"); reload(); });
        menu.add(asc);
        menu.add(desc);

        menu.show(titleButton, 115, titleButton.getHeight());
    }

    private void styleMenuItem(JMenuItem item, boolean active) {
        item.setBackground(new Color(50, 50, 50));
        item.setForeground(active ? UIConfig.accent() : UIConfig.foreground());
    }

    public void reload() {
        if (currentParentPath != null && currentController != null)
            loadSubfolders(currentParentPath, currentController);
    }

    // ── Helpers ────────────────────────────────────────────────────

    private void updateTitle(int total) {
        String suffix = filteredList.isEmpty() ? "" : " (" + total + ")";
        titleButton.setText(buildTitle() + suffix);
    }

    // NOVO: usa chaves i18n para o prefixo e os nomes de campo
    private String buildTitle() {
        String arrow = sortOrder.equals("ASC") ? "↑" : "↓";
        String field = switch (sortField) {
            case "last_modified" -> I18N.get("subFolderPanel.sortField.date");
            case "size"          -> I18N.get("subFolderPanel.sortField.size");
            default              -> I18N.get("subFolderPanel.sortField.name");
        };
        return I18N.get("subFolderPanel.buildTitle.prefix") + field + " " + arrow;
    }

    private JButton makePageBtn(String text) {
        JButton btn = new JButton(text);
        btn.setFont(UIConfig.FONT_SMALL);
        btn.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setEnabled(false);
        return btn;
    }

    // ── Row ────────────────────────────────────────────────────────

    private JPanel createFolderRow(FileInfo fi) {
        File file = new File(fi.getPath());

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel iconLabel = new JLabel();
        iconLabel.setIcon(IconService.getIcon(file, "", FileType.FOLDER, 32));
        iconLabel.setOpaque(false);
        iconLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        String displayName = fi.getName().isEmpty() ? fi.getPath() : fi.getName();
        JLabel nameLabel = new JLabel(displayName);
        nameLabel.setFont(UIConfig.FONT_SMALL);
        nameLabel.setToolTipText(fi.getPath());
        nameLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        row.add(iconLabel, BorderLayout.WEST);
        row.add(nameLabel, BorderLayout.CENTER);

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) {
                Component dest = SwingUtilities.getDeepestComponentAt(
                        row.getParent(),
                        e.getXOnScreen() - row.getParent().getLocationOnScreen().x,
                        e.getYOnScreen() - row.getParent().getLocationOnScreen().y);
                if (dest != null && SwingUtilities.isDescendingFrom(dest, row)) return;
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1 && clickListener != null)
                    clickListener.onFolderClicked(file);
            }
        };
        row.addMouseListener(ma);
        iconLabel.addMouseListener(ma);
        nameLabel.addMouseListener(ma);

        Supplier<File> fs = () -> file;
        new DragAction(fs, row,       transferService);
        new DragAction(fs, iconLabel, transferService);
        new DragAction(fs, nameLabel, transferService);

        DropTargetAdapter dta = new DropTargetAdapter() {
            @Override public void dragEnter(DropTargetDragEvent dtde) {
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
                    row.setBorder(BorderFactory.createLineBorder(new Color(33, 150, 243), 2));
                } else dtde.rejectDrag();
            }
            @Override public void dragExit(DropTargetEvent dte) {
                row.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            }
            @Override public void drop(DropTargetDropEvent dtde) {
                row.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>)
                            dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    dtde.dropComplete(true);
                    if (fileExplorerSwing.getTransferService().isTransferModeActive()) {
                        if (files.get(0).getName().equalsIgnoreCase(file.getName())) return;
                        TransferDropHelper.showDropMenu(row, files, file, transferService, () -> {});
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    dtde.rejectDrop();
                }
            }
        };
        new DropTarget(row,       DnDConstants.ACTION_COPY_OR_MOVE, dta);
        new DropTarget(iconLabel, DnDConstants.ACTION_COPY_OR_MOVE, dta);
        new DropTarget(nameLabel, DnDConstants.ACTION_COPY_OR_MOVE, dta);

        return row;
    }

    // ── i18n ───────────────────────────────────────────────────────
    protected void updateTexts() {
        if (titledBorder != null) {
            titledBorder.setTitle(I18N.get("subFolderPanel.titledBorder"));
        }

        // NOVO: tooltip do botão de ordenação
        titleButton.setToolTipText(I18N.get("subFolderPanel.titleButton.tooltip"));

        // NOVO: placeholder do campo de busca
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                I18N.get("subFolderPanel.searchField.placeholder"));

        // NOVO: reconstrói o texto do titleButton (usa buildTitle(), que já traduz)
        updateTitle(filteredList.size());

        // NOVO: reconstrói o texto do emptyLabel mantendo o estado de visibilidade atual
        showEmpty(emptyLabel.isVisible());
    }

    @Override
    public void onLanguageChanged(Locale newLocale) {
        updateTexts();
        repaint();
        revalidate();
    }

    // ── Interface ──────────────────────────────────────────────────
    public interface FolderClickListener {
        void onFolderClicked(File folder);
    }
}