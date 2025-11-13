package com.esl.searchforfiles.ui;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.*;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Painel com JTree para navegação de drives e pastas
 */
public class FolderTreePanel extends JPanel {

    private final JTree folderTree;
    private final DefaultMutableTreeNode rootNode;
    private FolderSelectionListener selectionListener;

    public FolderTreePanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Navegação"));
        setPreferredSize(new Dimension(300, 600));

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

