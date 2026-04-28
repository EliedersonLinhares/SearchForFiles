package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.service.FavoritesService;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Painel com JTree para navegação de drives e pastas
 */
public class FolderTreePanel extends JPanel {

    private final JTree folderTree;
    private final DefaultMutableTreeNode rootNode;
    private final FavoritesService favoritesService;
    private final FileExplorerSwing fileExplorerSwing;
    private FolderSelectionListener selectionListener;
    private File selectedFile;

    public FolderTreePanel(FavoritesService favoritesService, FileExplorerSwing fileExplorerSwing) {
        this.favoritesService = favoritesService;
        this.fileExplorerSwing = fileExplorerSwing;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("📁 Navegação"));

        rootNode = new DefaultMutableTreeNode("Computador");
        folderTree = new JTree(rootNode);
        folderTree.setFont(new Font("SansSerif", Font.PLAIN, 12));

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

        JScrollPane scrollPane = new JScrollPane(folderTree);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * NOVO: Cria menu de contexto com opção de favoritos
     */
    private JPopupMenu createContextMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem addFavoriteItem = new JMenuItem("⭐ Adicionar aos Favoritos");
        addFavoriteItem.addActionListener(e -> {
            DefaultMutableTreeNode selectedNode =
                    (DefaultMutableTreeNode) folderTree.getLastSelectedPathComponent();

            if (selectedNode != null && selectedNode.getUserObject() instanceof File) {
                File selectedFile = (File) selectedNode.getUserObject();

                if (fileExplorerSwing.isDriveRoot(selectedFile.getAbsolutePath())) {
                    JOptionPane.showMessageDialog(this,
                            "Drive raiz não pode ser adicionado aos favoritos!\n" + "Somente pastas.",
                            "Aviso", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                if (favoritesService.isFavorite(selectedFile.getAbsolutePath())) {
                    JOptionPane.showMessageDialog(this,
                            "Esta pasta já está nos favoritos!",
                            "Aviso", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                if (favoritesService.addFavorite(selectedFile.getAbsolutePath())) {
                    JOptionPane.showMessageDialog(this,
                            "Pasta adicionada aos favoritos!\n\n" + selectedFile.getAbsolutePath(),
                            "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        });

        JMenuItem openItem = new JMenuItem("Abrir no Explorer");
        openItem.addActionListener(e -> {
            DefaultMutableTreeNode selectedNode =
                    (DefaultMutableTreeNode) folderTree.getLastSelectedPathComponent();

            if (selectedNode != null && selectedNode.getUserObject() instanceof File) {
                File selectedFile = (File) selectedNode.getUserObject();
                try {
                    Desktop.getDesktop().open(selectedFile);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this,
                            "Erro ao abrir: " + ex.getMessage(),
                            "Erro", JOptionPane.ERROR_MESSAGE);
                }
            }
        });


        JMenuItem defaultFolderItem = new JMenuItem("Definir como pasta padrão");
        defaultFolderItem.addActionListener(e -> defaultFolderListener());


        menu.add(defaultFolderItem);
        menu.add(addFavoriteItem);
        menu.addSeparator();
        menu.add(openItem);

        return menu;
    }

    private void defaultFolderListener() {
        DefaultMutableTreeNode selectedNode =
                (DefaultMutableTreeNode) folderTree.getLastSelectedPathComponent();
        if (selectedNode != null && selectedNode.getUserObject() instanceof File) {
            selectedFile = (File) selectedNode.getUserObject();

            if (!fileExplorerSwing.isDriveRoot(selectedFile.getAbsolutePath())) {
                fileExplorerSwing.getConfigManager().saveDefaulFolder(selectedFile.getAbsolutePath());
                System.out.println("Diretorio padrão salvo: " + selectedFile.getAbsolutePath());

            } else {
                JOptionPane.showMessageDialog(this, "Diretório padrão não pode ser raiz",
                        "Erro", JOptionPane.ERROR_MESSAGE);
                System.out.println("Diretorio padrão não pode ser raiz");
            }
        }
    }

    private void populateDrives() {
        rootNode.removeAllChildren();

        File[] roots = File.listRoots();
        for (File root : roots) {
            DefaultMutableTreeNode driveNode = new DefaultMutableTreeNode(root);
            driveNode.add(new DefaultMutableTreeNode("Carregando..."));
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
                            childNode.add(new DefaultMutableTreeNode("Carregando..."));
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

            if (userObject instanceof File) {
                File file = (File) userObject;
                setText(file.getName().isEmpty() ? file.getAbsolutePath() : file.getName());
                setIcon(fsv.getSystemIcon(file));
            } else if (userObject.equals("Computador")) {
                setIcon(UIManager.getIcon("FileView.computerIcon"));
            }

            return this;
        }
    }
}

