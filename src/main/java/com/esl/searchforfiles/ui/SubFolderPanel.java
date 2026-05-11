package com.esl.searchforfiles.ui;


import com.esl.searchforfiles.configuration.FileTransferHandler;
import com.esl.searchforfiles.configuration.TransferDropHelper;
import com.esl.searchforfiles.model.FileInfo;
import com.esl.searchforfiles.model.FileType;
import com.esl.searchforfiles.model.SearchCriteria;
import com.esl.searchforfiles.service.IconService;
import com.esl.searchforfiles.service.TransferService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileSystemView;
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

    private static final Color BG_COLOR    = new Color(45, 45, 45);
    private static final Color HOVER_COLOR = new Color(60, 60, 60);
    private static final Color HEADER_COLOR = new Color(33, 150, 243);
    private static final FileSystemView FSV = FileSystemView.getFileSystemView();

    // ── Ordenação ─────────────────────────────────────────────────
    private String  sortField = "name";   // "name" | "date_modified" | "size"
    private String  sortOrder = "ASC";    // "ASC"  | "DESC"
    private String  currentParentPath;
    private SearchController currentController;

    // ── Lista completa (sem filtro de texto) ──────────────────────
    private List<FileInfo> allFolders = new ArrayList<>();

    private final JPanel      listPanel;
    private final JScrollPane scrollPane;
    private final JLabel      titleLabel;
    private final JTextField  searchField;

    private FolderClickListener clickListener;
    private TransferService     transferService;
    private final FileExplorerSwing fileExplorerSwing;

    public SubFolderPanel(FileExplorerSwing fileExplorerSwing) {
        this.fileExplorerSwing = fileExplorerSwing;
        setLayout(new BorderLayout(0, 0));
        setBackground(BG_COLOR);
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(70, 70, 70)));

        // ── Cabeçalho ──────────────────────────────────────────────
        titleLabel = new JLabel(buildTitle());
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        titleLabel.setForeground(HEADER_COLOR);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 8));
        titleLabel.setBackground(new Color(38, 38, 38));
        titleLabel.setOpaque(true);
        titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        titleLabel.setToolTipText("Clique com o botão direito para ordenar");
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
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 12));
      //  PlaceholderUtil.setPlaceholder(searchField, "Pesquisar pasta..."); // veja nota abaixo
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e)  { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        // Painel norte: título + pesquisa
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.setBackground(new Color(38, 38, 38));
        northPanel.add(titleLabel,  BorderLayout.NORTH);
        northPanel.add(searchField, BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);

        // ── Lista de pastas ─────────────────────────────────────────
        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(BG_COLOR);

        scrollPane = new JScrollPane(listPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);
        scrollPane.setBackground(BG_COLOR);
        scrollPane.getViewport().setBackground(BG_COLOR);
        add(scrollPane, BorderLayout.CENTER);

        setPreferredSize(new Dimension(200, 0));
        setVisible(false);
        setTransferHandler(new FileTransferHandler());
    }

    // ── API pública ────────────────────────────────────────────────

    public void setTransferManager(TransferService tm) { this.transferService = tm; }

    public void setFolderClickListener(FolderClickListener l) { this.clickListener = l; }

    public void loadSubfolders(String parentPath, SearchController controller) {
        this.currentParentPath  = parentPath;
        this.currentController  = controller;
        listPanel.removeAll();
        searchField.setText("");

        SwingWorker<List<FileInfo>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<FileInfo> doInBackground() throws Exception {
                SearchCriteria criteria = new SearchCriteria()
                        .withFileType(FileType.FOLDER)
                        .inPath(parentPath, false)
                        .sortBy(sortField, sortOrder)
                        .limit(500);
                return controller.searchDirect(criteria);
            }

            @Override
            protected void done() {
                try {
                    List<FileInfo> folders = get().stream()
                            .filter(f -> !f.getPath().equalsIgnoreCase(parentPath))
                            .toList();

                    allFolders = folders;

                    if (folders.isEmpty()) {
                        SwingUtilities.invokeLater(() -> {
                            setVisible(false);
                            revalidateParent();
                        });
                    } else {
                        populateList(folders);
                        updateTitle(folders.size());
                        SwingUtilities.invokeLater(() -> {
                            setVisible(true);
                            revalidateParent();
                        });
                    }
                } catch (Exception ex) {
                    setVisible(false);
                }
            }
        };
        worker.execute();
    }

    @Override
    public void hide() {
        if (!isVisible()) return;
        SwingUtilities.invokeLater(() -> { setVisible(false); revalidateParent(); });
    }

    // ── Ordenação ──────────────────────────────────────────────────

    private void showSortMenu(MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(new Color(50, 50, 50));

        // Campos
        String[][] fields = {{"Nome", "name"}, {"Data", "last_modified"}, {"Tamanho", "size"}};
        for (String[] f : fields) {
            boolean active = sortField.equals(f[1]);
            JMenuItem item = new JMenuItem((active ? "✓ " : "   ") + f[0]);
            item.setBackground(new Color(50, 50, 50));
            item.setForeground(active ? HEADER_COLOR : Color.WHITE);
            String fieldKey = f[1];
            item.addActionListener(ev -> {
                sortField = fieldKey;
                reload();
            });
            menu.add(item);
        }

        menu.addSeparator();

        // Direção
        JMenuItem asc  = new JMenuItem((sortOrder.equals("ASC")  ? "✓ " : "   ") + "Crescente ↑");
        JMenuItem desc = new JMenuItem((sortOrder.equals("DESC") ? "✓ " : "   ") + "Decrescente ↓");
        styleMenuItem(asc,  sortOrder.equals("ASC"));
        styleMenuItem(desc, sortOrder.equals("DESC"));
        asc .addActionListener(ev -> { sortOrder = "ASC";  reload(); });
        desc.addActionListener(ev -> { sortOrder = "DESC"; reload(); });
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

    // ── Filtro de texto ────────────────────────────────────────────

    private void applyFilter() {
        String query = searchField.getText().trim().toLowerCase();
        List<FileInfo> filtered = allFolders.stream()
                .filter(f -> f.getName().toLowerCase().contains(query))
                .toList();
        populateList(filtered);
        updateTitle(filtered.size());
    }

    // ── Renderização ───────────────────────────────────────────────

    private void populateList(List<FileInfo> folders) {
        SwingUtilities.invokeLater(() -> {
            listPanel.removeAll();
            listPanel.add(Box.createVerticalStrut(4));
            for (FileInfo fi : folders) {
                listPanel.add(createFolderRow(fi));
                listPanel.add(Box.createVerticalStrut(1));
            }
            listPanel.add(Box.createVerticalGlue());
            listPanel.revalidate();
            listPanel.repaint();
        });
    }

    private void updateTitle(int count) {
        SwingUtilities.invokeLater(() ->
                titleLabel.setText(buildTitle() + " (" + count + ")"));
    }

    /** Gera texto do título refletindo a ordenação atual. */
    private String buildTitle() {
        String arrow = sortOrder.equals("ASC") ? " ↑" : " ↓";
        String field = switch (sortField) {
            case "last_modified" -> "Data";
            case "size"          -> "Tamanho";
            default              -> "Nome";
        };
        return "📁 Subpastas · " + field + arrow;
    }

    private void revalidateParent() {
        Container p = getParent();
        if (p != null) p.revalidate();
    }

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
        nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        nameLabel.setToolTipText(fi.getPath());
        nameLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        row.add(iconLabel, BorderLayout.WEST);
        row.add(nameLabel, BorderLayout.CENTER);

        MouseAdapter rowListener = new MouseAdapter() {
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
        row.addMouseListener(rowListener);
        iconLabel.addMouseListener(rowListener);
        nameLabel.addMouseListener(rowListener);

        Supplier<File> fileSupplier = () -> file;
        new DragAction(fileSupplier, row,       transferService);
        new DragAction(fileSupplier, iconLabel, transferService);
        new DragAction(fileSupplier, nameLabel, transferService);

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
                        if (files.getFirst().getName().equalsIgnoreCase(file.getName())) return;
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