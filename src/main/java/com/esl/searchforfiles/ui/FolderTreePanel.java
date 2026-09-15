package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.actions.fileTransfer.FileTransferHandler;
import com.esl.searchforfiles.actions.fileTransfer.TransferDropHelper;
import com.esl.searchforfiles.configuration.UIConfig;
import com.esl.searchforfiles.localization.I18N;
import com.esl.searchforfiles.model.FileType;
import com.esl.searchforfiles.service.FavoritesService;
import com.esl.searchforfiles.service.IconService;
import com.esl.searchforfiles.actions.fileTransfer.TransferService;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Painel com JTree para navegação de drives e pastas
 */
public class FolderTreePanel extends JPanel implements I18N.LanguageChangeListener{

    private final JTree folderTree;
    private final DefaultMutableTreeNode rootNode;
    private final FavoritesService favoritesService;
    private final FileExplorerSwing fileExplorerSwing;
    private FolderSelectionListener selectionListener;
    private File selectedFile;
    private DragAction dragAction;
    private TransferService transferService;
    private TitledBorder titledBorder;
    private JMenuItem addFavoriteItem;
    private JMenuItem openItem;
    private JMenuItem defaultFolderItem;

    public FolderTreePanel(FavoritesService favoritesService, FileExplorerSwing fileExplorerSwing) {
        this.favoritesService = favoritesService;
        this.fileExplorerSwing = fileExplorerSwing;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("📁 " + I18N.get("folderTreePanel.titledBorder")));
        titledBorder = (TitledBorder) this.getBorder();

        rootNode = new DefaultMutableTreeNode(I18N.get("folderTreePanel.rootNode"));
        folderTree = new JTree(rootNode);
        folderTree.setFont(UIConfig.FONT_SMALL);

        // Renderizador customizado
        folderTree.setCellRenderer(new FolderTreeCellRenderer());

        // Listener para seleção
        folderTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode selectedNode =
                    (DefaultMutableTreeNode) folderTree.getLastSelectedPathComponent();

            if (selectedNode != null && selectedNode.getUserObject() instanceof File) {
                File selectedFile = (File) selectedNode.getUserObject();
                if (selectedFile.isDirectory() && selectionListener != null) {
                    selectionListener.onFolderSelected(selectedFile);
                }
            }
        });



        // NOVO: Menu de contexto (botão direito)
        JPopupMenu contextMenu = createContextMenu();

        folderTree.addMouseListener(new MouseAdapter() {
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
                TreePath path = folderTree.getPathForLocation(e.getX(), e.getY());
                if (path != null) {
                    folderTree.setSelectionPath(path);
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();

                    if (node.getUserObject() instanceof File) {
                        contextMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });
        // Listener para expandir (lazy loading)
        folderTree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
                DefaultMutableTreeNode node =
                        (DefaultMutableTreeNode) event.getPath().getLastPathComponent();

                if (node.getChildCount() > 0) {
                    Object firstChild = ((DefaultMutableTreeNode) node.getFirstChild()).getUserObject();
                    if (!(firstChild instanceof String)) {
                        return;
                    }
                }

                if (node.getUserObject() instanceof File) {
                    File dir = (File) node.getUserObject();
                    loadSubfolders(node, dir);
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
                // Não faz nada
            }
        });

        populateDrives();

        setupTransferDropTarget();

        JScrollPane scrollPane = new JScrollPane(folderTree);
        add(scrollPane, BorderLayout.CENTER);

        // Drag & Drop — só pastas podem ser arrastadas para os favoritos
        // arquivos comuns também podem ser arrastados (para uso futuro)
        setTransferHandler(new FileTransferHandler());

        dragAction = new DragAction(
                () -> {
                    // Executado no momento do drag, não na construção
                    DefaultMutableTreeNode node =
                            (DefaultMutableTreeNode) folderTree.getLastSelectedPathComponent();

                    if (node != null && node.getUserObject() instanceof File file) {
                        return file;
                    }
                    return null; // DragAction ignora null
                },
                folderTree  // ← drag reconhecido na JTree, não no painel
                ,transferService
        );

        updateTexts();
        I18N.addLanguageChangeListener(this);
    }

    /**
     * NOVO: Cria menu de contexto com opção de favoritos
     */
    private JPopupMenu createContextMenu() {
        JPopupMenu menu = new JPopupMenu();

        addFavoriteItem = new JMenuItem("⭐ " + I18N.get("folderTreePanel.addFavoriteItem"));
        addFavoriteItem.setFont(UIConfig.FONT_DEFAULT);
        addFavoriteItem.addActionListener(e -> {

            DefaultMutableTreeNode selectedNode =
                    (DefaultMutableTreeNode) folderTree.getLastSelectedPathComponent();

            if (selectedNode != null && selectedNode.getUserObject() instanceof File) {
                File selectedFile = (File) selectedNode.getUserObject();

                confirmFavoriteAdd(selectedFile);
            }
        });

        openItem = new JMenuItem(I18N.get("folderTreePanel.openItem"));
        openItem.setFont(UIConfig.FONT_DEFAULT);
        openItem.addActionListener(e -> {
            DefaultMutableTreeNode selectedNode =
                    (DefaultMutableTreeNode) folderTree.getLastSelectedPathComponent();

            if (selectedNode != null && selectedNode.getUserObject() instanceof File) {
                File selectedFile = (File) selectedNode.getUserObject();
                try {
                    Desktop.getDesktop().open(selectedFile);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this,
                            I18N.get("folderTreePanel.openItem.showMessageDialog") + " " + ex.getMessage(),
                            I18N.get("folderTreePanel.openItem.showMessageDialog.title"), JOptionPane.ERROR_MESSAGE);
                }
            }
        });


        defaultFolderItem = new JMenuItem(I18N.get("folderTreePanel.defaultFolderItem"));
        defaultFolderItem.setFont(UIConfig.FONT_DEFAULT);
        defaultFolderItem.addActionListener(e -> defaultFolderListener());


        menu.add(defaultFolderItem);
        menu.add(addFavoriteItem);
        menu.addSeparator();
        menu.add(openItem);

        return menu;
    }


    public void setTransferManager(TransferService tm) {
        this.transferService = tm;
    }

    private void setupTransferDropTarget() {
        new DropTarget(folderTree, new DropTargetAdapter() {

            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
                    folderTree.setBorder(BorderFactory.createLineBorder(
                            new Color(33, 150, 243), 2));
                } else {
                    dtde.rejectDrag();
                }
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
                folderTree.setBorder(null);
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                // Destaca o nó sob o cursor durante o drag
                TreePath path = folderTree.getPathForLocation(
                        dtde.getLocation().x, dtde.getLocation().y);
                if (path != null) folderTree.setSelectionPath(path);
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                folderTree.setBorder(null);

                // Resolve pasta de destino a partir do nó selecionado
                DefaultMutableTreeNode node =
                        (DefaultMutableTreeNode) folderTree.getLastSelectedPathComponent();
                if (node == null || !(node.getUserObject() instanceof File dest)
                        || !dest.isDirectory()) {
                    dtde.rejectDrop();
                    return;
                }

                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>)
                            dtde.getTransferable()
                                    .getTransferData(DataFlavor.javaFileListFlavor);

                    dtde.dropComplete(true);

                    // Mostra popup Mover / Copiar / Cancelar
                    TransferDropHelper.showDropMenu(
                            folderTree, files, dest, transferService,
                            fileExplorerSwing::performCurrentSearch);

                } catch (Exception ex) {
                    ex.printStackTrace();
                    dtde.rejectDrop();
                }
            }
        });
    }

    private void confirmFavoriteAdd(File selectedFile) {
        if (fileExplorerSwing.isDriveRoot(selectedFile.getAbsolutePath())) {
            JOptionPane.showMessageDialog(this,
                    I18N.get("folderTreePanel.confirmFavoriteAdd.isDriveRoot.showMessageDialog"),
                    I18N.get("folderTreePanel.confirmFavoriteAdd.isDriveRoot.showMessageDialog.title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (favoritesService.isFavorite(selectedFile.getAbsolutePath())) {
            JOptionPane.showMessageDialog(this,
                    I18N.get("folderTreePanel.confirmFavoriteAdd.isFavorite.showMessageDialog"),
                    I18N.get("folderTreePanel.confirmFavoriteAdd.isFavorite.showMessageDialog.title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (favoritesService.addFavorite(selectedFile.getAbsolutePath())) {
            JOptionPane.showMessageDialog(this,
                     I18N.get("folderTreePanel.confirmFavoriteAdd.addFavorite.showMessageDialog") + "\n\n" + selectedFile.getAbsolutePath(),
                    I18N.get("folderTreePanel.confirmFavoriteAdd.addFavorite.showMessageDialog.title"), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void defaultFolderListener() {
        DefaultMutableTreeNode selectedNode =
                (DefaultMutableTreeNode) folderTree.getLastSelectedPathComponent();
        if (selectedNode != null && selectedNode.getUserObject() instanceof File) {
            selectedFile = (File) selectedNode.getUserObject();

            if (!fileExplorerSwing.isDriveRoot(selectedFile.getAbsolutePath())) {
                fileExplorerSwing.getConfigManager().saveDefaulFolder(selectedFile.getAbsolutePath());

            } else {
                JOptionPane.showMessageDialog(this, I18N.get("folderTreePanel.defaultFolderListener.isDriveRoot.showMessageDialog"),
                        I18N.get("folderTreePanel.defaultFolderListener.isDriveRoot.showMessageDialog.title"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void populateDrives() {
        rootNode.removeAllChildren();

        File[] roots = File.listRoots();
        for (File root : roots) {
            DefaultMutableTreeNode driveNode = new DefaultMutableTreeNode(root);
            driveNode.add(new DefaultMutableTreeNode(I18N.get("folderTreePanel.rootNode.state")));
            rootNode.add(driveNode);
        }

        ((DefaultTreeModel) folderTree.getModel()).reload();
        folderTree.expandRow(0);
    }

    private void loadSubfolders(DefaultMutableTreeNode parent, File dir) {
        parent.removeAllChildren();

        File[] files = dir.listFiles();
        if (files == null) return;

        Arrays.sort(files, Comparator.comparing(File::getName));

        for (File file : files) {
            if (file.isDirectory() && !file.isHidden()) {
                try {
                    DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(file);

                    File[] subFiles = file.listFiles();
                    if (subFiles != null) {
                        boolean hasSubdirs = false;
                        for (File sub : subFiles) {
                            if (sub.isDirectory() && !sub.isHidden()) {
                                hasSubdirs = true;
                                break;
                            }
                        }

                        if (hasSubdirs) {
                            childNode.add(new DefaultMutableTreeNode(I18N.get("folderTreePanel.rootNode.state")));
                        }
                    }

                    parent.add(childNode);
                } catch (Exception e) {
                    // Ignora pastas sem permissão
                }
            }
        }

        ((DefaultTreeModel) folderTree.getModel()).reload(parent);
    }

    public void setSelectionListener(FolderSelectionListener listener) {
        this.selectionListener = listener;
    }

    public interface FolderSelectionListener {
        void onFolderSelected(File folder);
    }

    /**
     * Renderizador customizado para ícones
     */
    private static class FolderTreeCellRenderer extends DefaultTreeCellRenderer {
        private final FileSystemView fsv = FileSystemView.getFileSystemView();

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                      boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObject = node.getUserObject();

            if (userObject instanceof File file) {
                setText(file.getName().isEmpty() ? file.getAbsolutePath() : file.getName());

           //     setIcon(fsv.getSystemIcon(file));

                java.util.List<String> exclusion = List.of("Desktop","Downloads","Pictures","3D Objects","Documents"
                        , "Favorites", "Meus Documentos", "Music", "OneDrive", "Recent" ,"Saved Games", "Search", "Videos");

                if(file.getParent() == null || exclusion.stream().anyMatch( e -> e.equalsIgnoreCase(file.getName()))){
                    setIcon(fsv.getSystemIcon(file,32,32));
                }else {
                    setIcon(IconService.getIcon(file, "", FileType.FOLDER, 32));
                }

            } else if (userObject.equals(I18N.get("folderTreePanel.rootNode"))) {
                setIcon(UIManager.getIcon("FileView.computerIcon"));
            }

            return this;
        }
    }

    public JTree getFolderTree() {
        return folderTree;
    }

    protected void updateTexts() {
        if (titledBorder != null) {
            titledBorder.setTitle(I18N.get("folderTreePanel.titledBorder"));
        }

        if (rootNode != null) {
            rootNode.setUserObject(I18N.get("folderTreePanel.rootNode"));
        }
        if (addFavoriteItem != null) {
            addFavoriteItem.setText(I18N.get("folderTreePanel.addFavoriteItem"));
        }
        if (openItem != null) {
            openItem.setText(I18N.get("folderTreePanel.openItem"));
        }
        if (defaultFolderItem != null) {
            defaultFolderItem.setText(I18N.get("folderTreePanel.defaultFolderItem"));
        }


    }

    @Override
    public void onLanguageChanged(Locale newLocale) {
        updateTexts();
        repaint();
        revalidate();
    }
}

