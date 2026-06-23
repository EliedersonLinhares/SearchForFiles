package com.esl.searchforfiles.actions.fileTransfer;


import com.esl.searchforfiles.configuration.UIConfig;
import com.esl.searchforfiles.model.FileType;
import com.esl.searchforfiles.service.IconService;
import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.*;
import java.awt.*;
import java.io.File;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Consumer;

public class DestinationChooserDialog extends JDialog {

    private File selectedDestination;
    private final Consumer<File> onConfirm;
    private final TransferMode mode;
    private final int fileCount;
    private JLabel selectedLabel;

    public DestinationChooserDialog(Window owner, TransferMode mode,
                                    int fileCount, Consumer<File> onConfirm) {
        super(owner, buildTitle(mode, fileCount), ModalityType.APPLICATION_MODAL);
        this.mode      = mode;
        this.fileCount = fileCount;
        this.onConfirm = onConfirm;
        buildUI();
        setSize(600, 520);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private static String buildTitle(TransferMode mode, int count) {
        return switch (mode) {
            case COPY -> "Copiar " + count + " item(s) para…";
            case MOVE -> "Mover "  + count + " item(s) para…";
            default   -> "Destino";
        };
    }

    private void buildUI() {
        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ── Cabeçalho ──────────────────────────────────────────────
        JLabel header = new JLabel(buildTitle(mode, fileCount));
        header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
        add(header, BorderLayout.NORTH);

        // ── Árvore de diretórios ────────────────────────────────────
        JTree tree = buildDirectoryTree();
        JScrollPane scroll = new JScrollPane(tree);
        scroll.setPreferredSize(new Dimension(560, 340));
        add(scroll, BorderLayout.CENTER);

        // ── Rodapé ─────────────────────────────────────────────────
        JPanel south = new JPanel(new BorderLayout(6, 6));

        selectedLabel = new JLabel("Nenhuma pasta selecionada");
        selectedLabel.setFont(selectedLabel.getFont().deriveFont(Font.ITALIC));
        selectedLabel.setForeground(Color.GRAY);
        south.add(selectedLabel, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

        // Botão "Nova pasta"
        JButton newFolderBtn = new JButton("📁 Nova pasta");
        newFolderBtn.addActionListener(e -> createNewFolder());
        buttons.add(newFolderBtn);

        JButton cancelBtn = new JButton("Cancelar");
        cancelBtn.addActionListener(e -> dispose());
        buttons.add(cancelBtn);

        String confirmLabel = mode == TransferMode.COPY ? "Copiar aqui" : "Mover aqui";
        JButton confirmBtn = new JButton(confirmLabel);
        confirmBtn.setFont(confirmBtn.getFont().deriveFont(Font.BOLD));
        confirmBtn.setEnabled(false);
        confirmBtn.addActionListener(e -> {
            if (selectedDestination != null) {
                onConfirm.accept(selectedDestination);
                dispose();
            }
        });
        buttons.add(confirmBtn);

        // Habilita o botão de confirmação quando há seleção
        tree.addTreeSelectionListener(ev -> {
            DefaultMutableTreeNode node =
                    (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (node == null) return;
            Object userObj = node.getUserObject();
            if (userObj instanceof File f && f.isDirectory()) {
                selectedDestination = f;
                selectedLabel.setText(f.getAbsolutePath());
                selectedLabel.setForeground(UIManager.getColor("Label.foreground"));
                confirmBtn.setEnabled(true);
            }
        });

        south.add(buttons, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);
    }

    // ── Árvore de diretórios ────────────────────────────────────────

    private JTree buildDirectoryTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Este computador");

        File[] roots = File.listRoots();
        for (File r : roots) {
            DefaultMutableTreeNode driveNode = new DefaultMutableTreeNode(r);
            driveNode.add(new DefaultMutableTreeNode("carregando...")); // lazy
            root.add(driveNode);
        }


        DefaultTreeModel model = new DefaultTreeModel(root);
        JTree tree = new JTree(model);
        tree.setCellRenderer(new FileTreeCellRenderer());
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);

        // Lazy loading ao expandir
        tree.addTreeExpansionListener(new javax.swing.event.TreeExpansionListener() {
            @Override
            public void treeExpanded(javax.swing.event.TreeExpansionEvent event) {
                DefaultMutableTreeNode node =
                        (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                lazyLoadChildren(node, model, tree);
            }
            @Override public void treeCollapsed(javax.swing.event.TreeExpansionEvent e) {}
        });

        return tree;
    }

    private void lazyLoadChildren(DefaultMutableTreeNode node,
                                  DefaultTreeModel model, JTree tree) {
        // Só carrega se tem o placeholder "carregando..."
        if (node.getChildCount() == 1) {
            DefaultMutableTreeNode first = (DefaultMutableTreeNode) node.getChildAt(0);
            if (!"carregando...".equals(first.getUserObject())) return;
        } else {
            return; // já carregado
        }

        Object obj = node.getUserObject();
        File dir = (obj instanceof File f) ? f : null;
        if (dir == null) return;

        node.removeAllChildren();

        File[] children = dir.listFiles(f -> f.isDirectory() && !f.isHidden());
        if (children != null) {
            java.util.Arrays.sort(children,
                    Comparator.comparing(f -> f.getName().toLowerCase()));
            for (File child : children) {
                DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(child);
                // Adiciona placeholder para que o nó seja expansível
                File[] grandchildren = child.listFiles(f -> f.isDirectory() && !f.isHidden());
                if (grandchildren != null && grandchildren.length > 0) {
                    childNode.add(new DefaultMutableTreeNode("carregando..."));
                }
                node.add(childNode);
            }
        }

        model.nodeStructureChanged(node);
    }

    private void createNewFolder() {
        if (selectedDestination == null) {
            JOptionPane.showMessageDialog(this,
                    "Selecione uma pasta pai antes de criar uma nova pasta.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String name = JOptionPane.showInputDialog(this,
                "Nome da nova pasta:", "Nova pasta");
        if (name == null || name.isBlank()) return;

        File newDir = new File(selectedDestination, name.trim());
        if (newDir.mkdirs()) {
            JOptionPane.showMessageDialog(this,
                    "Pasta criada: " + newDir.getAbsolutePath());
        } else {
            JOptionPane.showMessageDialog(this,
                    "Não foi possível criar a pasta.", "Erro",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Cell renderer com ícone do sistema ────────────────────────────
    private static class FileTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                      boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object obj = node.getUserObject();
            if (obj instanceof File f) {
                setText(f.equals(new File(f.getAbsolutePath()).getParentFile())
                        ? f.getAbsolutePath()
                        : FileSystemView.getFileSystemView().getSystemDisplayName(f));
               // setIcon(FileSystemView.getFileSystemView().getSystemIcon(f));
                setIcon(IconService.getIcon(f, "", FileType.FOLDER, 32));
            }
            return this;
        }
    }
}