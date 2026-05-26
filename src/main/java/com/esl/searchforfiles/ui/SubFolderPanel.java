package com.esl.searchforfiles.ui;


import com.esl.searchforfiles.configuration.ConfigManager;
import com.esl.searchforfiles.actions.fileTransfer.FileTransferHandler;
import com.esl.searchforfiles.actions.fileTransfer.TransferDropHelper;
import com.esl.searchforfiles.configuration.UIConfig;
import com.esl.searchforfiles.model.FileInfo;
import com.esl.searchforfiles.model.FileType;
import com.esl.searchforfiles.model.SearchCriteria;
import com.esl.searchforfiles.service.IconService;
import com.esl.searchforfiles.actions.fileTransfer.TransferService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SubFolderPanel extends JPanel {

    private static final Color BG_COLOR     = new Color(45, 45, 45);
    private static final Color HOVER_COLOR  = new Color(60, 60, 60);
    private static final Color HEADER_COLOR = new Color(33, 150, 243);
    private static final Color BTN_COLOR    = new Color(55, 55, 55);
    private static final Color BTN_DISABLED = new Color(70, 70, 70);
    private static final int   PAGE_SIZE    = 100;

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
    private final JLabel      titleLabel;
    private final JTextField  searchField;
    private final JLabel      pageLabel;
    private final JLabel      emptyLabel;
    private final JButton     btnFirst, btnPrev, btnNext, btnLast;

    private FolderClickListener clickListener;
    private TransferService     transferService;
    private final FileExplorerSwing fileExplorerSwing;
    private final ConfigManager     configManager;

    public SubFolderPanel(FileExplorerSwing fileExplorerSwing, ConfigManager configManager) {
        this.fileExplorerSwing = fileExplorerSwing;
        this.configManager     = configManager;
        this.sortField = configManager.getSubfolderSortField();
        this.sortOrder = configManager.getSubfolderSortOrder();

        setLayout(new BorderLayout(0, 0));
        setBackground(BG_COLOR);
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(70, 70, 70)));

        // ── Cabeçalho ──────────────────────────────────────────────
        titleLabel = new JLabel(buildTitle());
        titleLabel.setFont(UIConfig.FONT_SMALL);
        titleLabel.setForeground(HEADER_COLOR);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 8));
        titleLabel.setBackground(new Color(38, 38, 38));
        titleLabel.setOpaque(true);
        titleLabel.setToolTipText("Clique com botão direito para ordenar");
        titleLabel.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) showSortMenu(e);
            }
        });

        // ── Campo de pesquisa ───────────────────────────────────────
        searchField = new JTextField();
        searchField.setBackground(new Color(55, 55, 55));
        searchField.setForeground(Color.WHITE);
        searchField.setCaretColor(Color.WHITE);
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
        pageLabel.setForeground(new Color(180, 180, 180));
        pageLabel.setFont(UIConfig.FONT_SMALL);

        JPanel btnGroup = new JPanel(new GridLayout(1, 4, 2, 0));
        btnGroup.setBackground(new Color(38, 38, 38));
        btnGroup.add(btnFirst);
        btnGroup.add(btnPrev);
        btnGroup.add(btnNext);
        btnGroup.add(btnLast);

        JPanel pagBar = new JPanel(new BorderLayout(2, 0));
        pagBar.setBackground(new Color(38, 38, 38));
        pagBar.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
        pagBar.add(pageLabel, BorderLayout.CENTER);
        pagBar.add(btnGroup,  BorderLayout.EAST);

        // ── Painel norte ────────────────────────────────────────────
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.setBackground(new Color(38, 38, 38));
        northPanel.add(titleLabel,  BorderLayout.NORTH);
        northPanel.add(searchField, BorderLayout.CENTER);
        northPanel.add(pagBar,      BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);

        // ── Mensagem de vazio ───────────────────────────────────────
        emptyLabel = new JLabel("Nenhuma subpasta", SwingConstants.CENTER);
        emptyLabel.setForeground(new Color(120, 120, 120));
        emptyLabel.setFont(UIConfig.FONT_SMALL);
        emptyLabel.setVisible(false);

        // ── Lista ───────────────────────────────────────────────────
        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(BG_COLOR);

        // Wrapper que contém tanto a lista quanto o emptyLabel
        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.setBackground(BG_COLOR);
        centerWrapper.add(emptyLabel, BorderLayout.NORTH);
        centerWrapper.add(listPanel,  BorderLayout.CENTER);

        scrollPane = new JScrollPane(centerWrapper);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);
        scrollPane.setBackground(BG_COLOR);
        scrollPane.getViewport().setBackground(BG_COLOR);
        add(scrollPane, BorderLayout.CENTER);

        setPreferredSize(new Dimension(200, 0));
        setTransferHandler(new FileTransferHandler());
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

        showEmpty(false); // limpa estado enquanto carrega

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

    private void showEmpty(boolean empty) {
        String q = searchField.getText().trim();
        emptyLabel.setText(empty
                ? (q.isEmpty() ? "Nenhuma subpasta" : "Nenhum resultado para \"" + q + "\"")
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

    private void showSortMenu(MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(new Color(50, 50, 50));

        String[][] fields = {{"Nome", "name"}, {"Data", "last_modified"}, {"Tamanho", "size"}};
        for (String[] f : fields) {
            boolean active = sortField.equals(f[1]);
            JMenuItem item = new JMenuItem((active ? "✓ " : "   ") + f[0]);
            item.setBackground(new Color(50, 50, 50));
            item.setForeground(active ? HEADER_COLOR : Color.WHITE);
            String key = f[1];
            item.addActionListener(ev -> { sortField = key; configManager.saveSubfolderSortField(key); reload(); });
            menu.add(item);
        }

        menu.addSeparator();

        JMenuItem asc  = new JMenuItem((sortOrder.equals("ASC")  ? "✓ " : "   ") + "Crescente ↑");
        JMenuItem desc = new JMenuItem((sortOrder.equals("DESC") ? "✓ " : "   ") + "Decrescente ↓");
        styleMenuItem(asc,  sortOrder.equals("ASC"));
        styleMenuItem(desc, sortOrder.equals("DESC"));
        asc .addActionListener(ev -> { sortOrder = "ASC";  configManager.saveSubfolderSortOrder("ASC");  reload(); });
        desc.addActionListener(ev -> { sortOrder = "DESC"; configManager.saveSubfolderSortOrder("DESC"); reload(); });
        menu.add(asc);
        menu.add(desc);

        menu.show(titleLabel, e.getX(), e.getY());
    }

    private void styleMenuItem(JMenuItem item, boolean active) {
        item.setBackground(new Color(50, 50, 50));
        item.setForeground(active ? HEADER_COLOR : Color.WHITE);
    }

    private void reload() {
        if (currentParentPath != null && currentController != null)
            loadSubfolders(currentParentPath, currentController);
    }

    // ── Helpers ────────────────────────────────────────────────────

    private void updateTitle(int total) {
        String suffix = filteredList.isEmpty() ? "" : " (" + total + ")";
        titleLabel.setText(buildTitle() + suffix);
    }

    private String buildTitle() {
        String arrow = sortOrder.equals("ASC") ? "↑" : "↓";
        String field = switch (sortField) {
            case "last_modified" -> "Data";
            case "size"          -> "Tamanho";
            default              -> "Nome";
        };
        return "📁 Subpastas · " + field + " " + arrow;
    }

    private JButton makePageBtn(String text) {
        JButton btn = new JButton(text);
        btn.setFont(UIConfig.FONT_SMALL);
        btn.setForeground(Color.WHITE);
        btn.setBackground(BTN_COLOR);
        btn.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setEnabled(false);
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(HEADER_COLOR);
            }
            @Override public void mouseExited(MouseEvent e) {
                btn.setBackground(btn.isEnabled() ? BTN_COLOR : BTN_DISABLED);
            }
        });
        return btn;
    }

    // ── Row ────────────────────────────────────────────────────────

    private JPanel createFolderRow(FileInfo fi) {
        File file = new File(fi.getPath());

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(BG_COLOR);
        row.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel iconLabel = new JLabel();
        iconLabel.setIcon(IconService.getIcon(file, "", FileType.FOLDER, 32));
        iconLabel.setOpaque(false);
        iconLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        String displayName = fi.getName().isEmpty() ? fi.getPath() : fi.getName();
        JLabel nameLabel = new JLabel(displayName);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(UIConfig.FONT_SMALL);
        nameLabel.setToolTipText(fi.getPath());
        nameLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        row.add(iconLabel, BorderLayout.WEST);
        row.add(nameLabel, BorderLayout.CENTER);

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { row.setBackground(HOVER_COLOR); }
            @Override public void mouseExited(MouseEvent e) {
                Component dest = SwingUtilities.getDeepestComponentAt(
                        row.getParent(),
                        e.getXOnScreen() - row.getParent().getLocationOnScreen().x,
                        e.getYOnScreen() - row.getParent().getLocationOnScreen().y);
                if (dest != null && SwingUtilities.isDescendingFrom(dest, row)) return;
                row.setBackground(BG_COLOR);
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

    // ── Interface ──────────────────────────────────────────────────
    public interface FolderClickListener {
        void onFolderClicked(File folder);
    }
}