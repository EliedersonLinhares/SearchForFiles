package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.actions.fileTransfer.TransferDropHelper;
import com.esl.searchforfiles.configuration.UIConfig;
import com.esl.searchforfiles.localization.I18N;
import com.esl.searchforfiles.model.FileType;
import com.esl.searchforfiles.service.FavoritesService;
import com.esl.searchforfiles.service.IconService;
import com.esl.searchforfiles.actions.fileTransfer.TransferService;
import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Painel com JList de pastas favoritas
 */
public class FavoritesPanel extends JPanel implements I18N.LanguageChangeListener{
    private final JList<String> favoritesList;
    private final DefaultListModel<String> listModel;
    private final FavoritesService favoritesService;
    private FavoriteSelectionListener selectionListener;
    private FileExplorerSwing fileExplorerSwing;
    private TransferService transferService;
    private TitledBorder titledBorder;
    private JMenuItem removeItem;
    private JMenuItem openItem;
    private JMenuItem defaultFolderItem;
    private String selected;
    private JButton clearButton;

    public FavoritesPanel(FavoritesService favoritesManager, FileExplorerSwing fileExplorerSwing) {
        this.favoritesService = favoritesManager;
        this.fileExplorerSwing = fileExplorerSwing;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder(I18N.get("favoritesPanel.titledBorder")));
        titledBorder = (TitledBorder) this.getBorder();
        setPreferredSize(new Dimension(300, 200));

        // Modelo da lista
        listModel = new DefaultListModel<>();

        // JList
        favoritesList = new JList<>(listModel);
        favoritesList.setFont(UIConfig.FONT_SMALL);
        favoritesList.setCellRenderer(new FavoritesCellRenderer());
        favoritesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // CORREÇÃO: MouseListener em vez de ListSelectionListener
        // Isso garante que SEMPRE dispara ao clicar, mesmo no item já selecionado
        favoritesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Ignora clique direito (será tratado pelo menu contexto)
                if (SwingUtilities.isRightMouseButton(e)) {
                    return;
                }

                // Obtém o índice clicado
                int index = favoritesList.locationToIndex(e.getPoint());

                if (index >= 0 && index < listModel.getSize()) {
                    selected = listModel.getElementAt(index);

                    // Verifica se não é o item placeholder
                    if (selected != null &&
                            !selected.equals("(Nenhum favorito)") &&
                            selectionListener != null) {

                        // Força seleção visual
                        favoritesList.setSelectedIndex(index);

                        // Dispara evento
                        File folder = new File(selected);
                        if (folder.exists() && folder.isDirectory()) {
                            selectionListener.onFavoriteSelected(folder);
                            fileExplorerSwing.performCurrentSearch();
                        } else {
                            JOptionPane.showMessageDialog(FavoritesPanel.this,
                                    I18N.get("favoritesPanel.folder.exists.showMessageDialog") + "\n" + selected,
                                    I18N.get("favoritesPanel.folder.exists.showMessageDialog.title"), JOptionPane.WARNING_MESSAGE);
                        }
                    }
                }
            }
        });



        // Menu de contexto (botão direito)
        JPopupMenu contextMenu = createContextMenu();

        favoritesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            private void showContextMenu(MouseEvent e) {
                int index = favoritesList.locationToIndex(e.getPoint());
                if (index >= 0) {
                    favoritesList.setSelectedIndex(index);
                    String selected = listModel.getElementAt(index);

                    // Só mostra menu se não for o placeholder
                    if (!selected.equals("(Nenhum favorito)")) {
                        contextMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(favoritesList);
        add(scrollPane, BorderLayout.CENTER);

        // Painel inferior com botão limpar
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
//        JButton clearButton = new JButton("Limpar Todos");
//        clearButton.setFont(UIConfig.FONT_DEFAULT);
         clearButton = makeHighlightedMediumBtn(I18N.get("favoritesPanel.clearButton"),
                I18N.get("favoritesPanel.clearButton.tooltiptext"), 145,40);
        clearButton.addActionListener(e -> clearAllFavorites());
        bottomPanel.add(clearButton);
        add(bottomPanel, BorderLayout.SOUTH);

        // Carrega favoritos iniciais
        refreshFavorites();

        // Listener para mudanças nos favoritos
        favoritesService.addListener(this::refreshFavorites);

        setupDropTarget();

        updateTexts();
        I18N.addLanguageChangeListener(this);
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


    /**
     * Atualiza lista de favoritos
     */
    private void refreshFavorites() {
        listModel.clear();
        List<String> favorites = favoritesService.getFavorites();

        if (favorites.isEmpty()) {
            listModel.addElement("(Nenhum favorito)");
            favoritesList.setEnabled(false);
        } else {
            for (String favorite : favorites) {
                listModel.addElement(favorite);
            }
            favoritesList.setEnabled(true);
        }
    }

    /**
     * Cria menu de contexto
     */
    private JPopupMenu createContextMenu() {
        JPopupMenu menu = new JPopupMenu();


        defaultFolderItem = new JMenuItem(I18N.get("favoritesPanel.defaultFolderItem"));
        defaultFolderItem.addActionListener(e -> {
            String selected = favoritesList.getSelectedValue();
            if (selected != null && !selected.equals("(Nenhum favorito)")) {
                fileExplorerSwing.getConfigManager().saveDefaulFolder(selected);
                System.out.println("Diretorio padrão salvo: " + selected);
            }
        });

        openItem = new JMenuItem(I18N.get("favoritesPanel.openItem"));
        openItem.addActionListener(e -> {
            String selected = favoritesList.getSelectedValue();
            if (selected != null && !selected.equals("(Nenhum favorito)")) {
                try {
                    Desktop.getDesktop().open(new File(selected));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this,
                            "Erro ao abrir: " + ex.getMessage(),
                            "Erro", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        removeItem = new JMenuItem(I18N.get("favoritesPanel.removeItem"));
        removeItem.addActionListener(e -> {
            String selected = favoritesList.getSelectedValue();
            if (selected != null && !selected.equals("(Nenhum favorito)")) {
                int confirm = JOptionPane.showConfirmDialog(this,
                         I18N.get("favoritesPanel.removeItem.showMessageDialog") + "\n\n" + selected,
                        I18N.get("favoritesPanel.removeItem.showMessageDialog.title"),
                        JOptionPane.YES_NO_OPTION);

                if (confirm == JOptionPane.YES_OPTION) {
                    favoritesService.removeFavorite(selected);
                }
            }
        });

        menu.add(defaultFolderItem);
        menu.add(openItem);
        menu.addSeparator();
        menu.add(removeItem);

        return menu;
    }

    /**
     * Limpa todos os favoritos
     */
    private void clearAllFavorites() {
        if (favoritesService.getFavorites().isEmpty()) {
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                I18N.get("favoritesPanel.clearAllFavorites.showMessageDialog"),
                I18N.get("favoritesPanel.clearAllFavorites.showMessageDialog.title"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            favoritesService.clearFavorites();
        }
    }

    public void setSelectionListener(FavoriteSelectionListener listener) {
        this.selectionListener = listener;
    }

    public interface FavoriteSelectionListener {
        void onFavoriteSelected(File folder);
    }

    public void setTransferManager(TransferService tm) {
        this.transferService = tm;
    }

    // Substitua setupDropTarget() por:
    private void setupDropTarget() {
        DropTargetListener dtl = new DropTargetAdapter() {

            // Guarda se o drag em curso é de pasta(s) puras (sem arquivos)
            // — determinado no dragEnter e reutilizado no drop.
            private boolean isDirOnlyDrag = false;

            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                if (!dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.rejectDrag();
                    return;
                }
                dtde.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
                setBorder(BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(33, 150, 243), 2),
                        I18N.get("favoritesPanel.titledBorder")));

                // BUG 2 FIX — tenta inspecionar o conteúdo já no dragEnter
                // para saber se é drag de pasta pura (adicionar favorito)
                // ou de arquivos (transferir). Se não conseguir ler, assume false.
                try {
                    @SuppressWarnings("unchecked")
                    List<File> preview = (List<File>)
                            dtde.getTransferable()
                                    .getTransferData(DataFlavor.javaFileListFlavor);
                    isDirOnlyDrag = preview != null
                            && !preview.isEmpty()
                            && preview.stream().allMatch(File::isDirectory);
                } catch (Exception ignored) {
                    isDirOnlyDrag = false;
                }
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                // Só destaca item se for drag de arquivos (não de pasta→favorito)
                if (!isDirOnlyDrag) {
                    Point p = SwingUtilities.convertPoint(
                            dtde.getDropTargetContext().getComponent(),
                            dtde.getLocation(), favoritesList);
                    int idx = favoritesList.locationToIndex(p);
                    if (idx >= 0) favoritesList.setSelectedIndex(idx);
                }
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
                setBorder(BorderFactory.createTitledBorder(I18N.get("favoritesPanel.titledBorder")));
                favoritesList.clearSelection();
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                setBorder(BorderFactory.createTitledBorder( I18N.get("favoritesPanel.titledBorder")));

                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);

                    @SuppressWarnings("unchecked")
                    List<File> incoming = (List<File>)
                            dtde.getTransferable()
                                    .getTransferData(DataFlavor.javaFileListFlavor);

                    dtde.dropComplete(true);
                    if (incoming == null || incoming.isEmpty()) return;

                    List<File> dirs  = incoming.stream().filter(File::isDirectory).toList();
                    List<File> files = incoming.stream().filter(f -> !f.isDirectory()).toList();

                    // BUG 2 FIX — drag é só de pastas: comportamento original (adicionar favorito)
                    if (!dirs.isEmpty() && files.isEmpty()) {
                        dirs.forEach(this::tryAddFavorite);
                        favoritesList.clearSelection();
                        return;
                    }

                    // Drag contém arquivos (ou misto): resolve destino pelo item selecionado
                    String selectedFav = favoritesList.getSelectedValue();
                    favoritesList.clearSelection();

                    if (selectedFav == null || selectedFav.equals("(Nenhum favorito)")) {
                        JOptionPane.showMessageDialog(FavoritesPanel.this,
                                I18N.get("favoritesPanel.drop.showMessageDialog"),
                                I18N.get("favoritesPanel.drop.showMessageDialog.title"), JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    File dest = new File(selectedFav);
                    if (!dest.isDirectory()) return;

                    // Inclui as pastas do drag misto junto com os arquivos
                    List<File> toTransfer = new java.util.ArrayList<>(files);
                    toTransfer.addAll(dirs);

                    TransferDropHelper.showDropMenu(
                            FavoritesPanel.this, toTransfer, dest, transferService,
                            () -> fileExplorerSwing.performCurrentSearch());

                } catch (Exception ex) {
                    ex.printStackTrace();
                    dtde.dropComplete(false);
                }
            }

            private void tryAddFavorite(File dir) {
                if (fileExplorerSwing.isDriveRoot(dir.getAbsolutePath())) {
                    JOptionPane.showMessageDialog(FavoritesPanel.this,
                            I18N.get("favoritesPanel.tryAddFavorite.isDriveRoot.showMessageDialog"),
                            I18N.get("favoritesPanel.tryAddFavorite.isDriveRoot.showMessageDialog.title"), JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (favoritesService.isFavorite(dir.getAbsolutePath())) {
                    JOptionPane.showMessageDialog(FavoritesPanel.this,
                             I18N.get("favoritesPanel.tryAddFavorit.isFavorite.showMessageDialog") + "\n\n" + dir.getName(),
                            I18N.get("favoritesPanel.tryAddFavorite.isDriveRoot.showMessageDialog.title"), JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                favoritesService.addFavorite(dir.getAbsolutePath());
            }
        };

        new DropTarget(favoritesList, DnDConstants.ACTION_COPY_OR_MOVE, dtl);
        new DropTarget(this,          DnDConstants.ACTION_COPY_OR_MOVE, dtl);
    }



    /**
     * Renderizador customizado para células
     */
    private static class FavoritesCellRenderer extends DefaultListCellRenderer {
    private final FileSystemView fsv = FileSystemView.getFileSystemView();

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            String path = (String) value;

            if (path.equals("(Nenhum favorito)")) {
                setIcon(null);
                setForeground(Color.GRAY);
                setFont(getFont().deriveFont(Font.ITALIC));
            } else {
                File file = new File(path);

                List<String> exclusion = List.of("Desktop","Downloads","Pictures","3D Objects","Documents"
                , "Favorites", "Meus Documentos", "Music", "OneDrive", "Recent" ,"Saved Games", "Search", "Videos");

                if(exclusion.stream().anyMatch( e -> e.equalsIgnoreCase(file.getName()))){
                    setIcon(fsv.getSystemIcon(file,32,32));
                }else {
                    setIcon(IconService.getIcon(file, "", FileType.FOLDER, 32));
                }

                // Mostra apenas o nome da pasta
                String displayName = file.getName();
                if (displayName.isEmpty()) {
                    displayName = path; // Raiz de drive
                }
                setText(displayName);
                setToolTipText(path);
            }

            return this;
        }
    }

    public JList<String> getFavoritesList() {
        return favoritesList;
    }

    protected void updateTexts() {
        if (titledBorder != null) {
            titledBorder.setTitle(I18N.get("favoritesPanel.titledBorder"));
        }
        if (removeItem != null) {
            removeItem.setText(I18N.get("favoritesPanel.removeItem"));
        }
        if (openItem != null) {
            openItem.setText(I18N.get("favoritesPanel.openItem"));
        }
        if (defaultFolderItem != null) {
            defaultFolderItem.setText(I18N.get("favoritesPanel.defaultFolderItem"));
        }
//        if (selected != null) {
//            selected = (I18N.get("folderTreePanel.defaultFolderItem"));
//        }
        if (clearButton != null) {
            clearButton.setText(I18N.get("favoritesPanel.clearButton"));
        }

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
    @Override
    public void onLanguageChanged(Locale newLocale) {
        updateTexts();
        repaint();
        revalidate();
    }
}
