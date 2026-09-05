package com.esl.searchforfiles.preview.model3DViewer;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.EventObject;

/**
 * Painel permanente exibindo a hierarquia de grupos/partes do modelo 3D
 * carregado, com checkboxes para mostrar/esconder cada parte individualmente.
 * Fica embutido de forma fixa no layout principal via JSplitPane.
 */
public class GroupVisibilityPanel extends JPanel {

    private final Obj3DApp app;
    private final JTree tree;
    private final DefaultTreeModel treeModel;

    public GroupVisibilityPanel(Obj3DApp app) {
        this.app = app;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Grupos do Modelo"));

        treeModel = new DefaultTreeModel(new DefaultMutableTreeNode("Nenhum modelo carregado"));
        tree = new JTree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new CheckBoxNodeRenderer());
        tree.setCellEditor(new CheckBoxNodeEditor(tree));
        tree.setEditable(true);
        tree.setRowHeight(22);
        tree.setLargeModel(true);

        JScrollPane scrollPane = new JScrollPane(tree);
        add(scrollPane, BorderLayout.CENTER);
        add(buildBottomPanel(), BorderLayout.SOUTH);

        setMinimumSize(new Dimension(180, 120));
        setPreferredSize(new Dimension(260, 300));
    }

    private JPanel buildBottomPanel() {
        JPanel bottomPanel = new JPanel(new GridLayout(1, 3, 4, 0));
        JButton btnShowAll = new JButton("Mostrar Tudo");
        JButton btnHideAll = new JButton("Esconder Tudo");
        JButton btnRefresh = new JButton("Atualizar");

        btnShowAll.addActionListener(e -> setAllGroups(true));
        btnHideAll.addActionListener(e -> setAllGroups(false));
        btnRefresh.addActionListener(e -> refresh());

        bottomPanel.add(btnShowAll);
        bottomPanel.add(btnHideAll);
        bottomPanel.add(btnRefresh);
        return bottomPanel;
    }

    private void setAllGroups(boolean visible) {
        app.setAllGroupsVisible(visible, () -> SwingUtilities.invokeLater(this::refresh));
    }

    /**
     * Solicita a hierarquia atual do modelo ao Obj3DApp e reconstrói a JTree.
     * Deve ser chamada sempre que um novo modelo terminar de carregar.
     */
    public void refresh() {
        app.requestGroupHierarchy(root ->
                SwingUtilities.invokeLater(() -> {
                    DefaultMutableTreeNode rootTreeNode = convertToTreeNode(root);
                    treeModel.setRoot(rootTreeNode);
                    treeModel.reload();
                    expandAllRows();
                })
        );
    }

    private DefaultMutableTreeNode convertToTreeNode(GroupNode groupNode) {
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(new CheckBoxNode(groupNode));
        for (GroupNode child : groupNode.children) {
            treeNode.add(convertToTreeNode(child));
        }
        return treeNode;
    }

    private void expandAllRows() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    private static class CheckBoxNode {
        final GroupNode groupNode;
        CheckBoxNode(GroupNode groupNode) { this.groupNode = groupNode; }
        @Override public String toString() { return groupNode.name; }
    }

    private static class CheckBoxNodeRenderer implements TreeCellRenderer {
        private final JCheckBox checkBox = new JCheckBox();

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
            DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) value;
            Object userObject = treeNode.getUserObject();

            if (userObject instanceof CheckBoxNode cbNode) {
                checkBox.setText(cbNode.groupNode.name);
                checkBox.setSelected(cbNode.groupNode.visible);
            } else {
                checkBox.setText(String.valueOf(userObject));
                checkBox.setSelected(true);
            }

            checkBox.setBackground(selected ? tree.getBackground().darker() : tree.getBackground());
            checkBox.setOpaque(true);
            return checkBox;
        }
    }

    private class CheckBoxNodeEditor extends AbstractCellEditor implements TreeCellEditor {
        private final JCheckBox checkBox = new JCheckBox();
        private CheckBoxNode currentNode;

        CheckBoxNodeEditor(JTree tree) {
            checkBox.addActionListener(e -> {
                if (currentNode != null) {
                    boolean newValue = checkBox.isSelected();
                    currentNode.groupNode.visible = newValue;
                    app.setGroupVisible(currentNode.groupNode.id, newValue);
                }
                stopCellEditing();
            });
        }

        @Override
        public Component getTreeCellEditorComponent(JTree tree, Object value, boolean selected,
                                                    boolean expanded, boolean leaf, int row) {
            DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) value;
            Object userObject = treeNode.getUserObject();

            if (userObject instanceof CheckBoxNode cbNode) {
                currentNode = cbNode;
                checkBox.setText(cbNode.groupNode.name);
                checkBox.setSelected(cbNode.groupNode.visible);
            }
            return checkBox;
        }

        @Override
        public Object getCellEditorValue() {
            return currentNode;
        }

        @Override
        public boolean isCellEditable(EventObject event) {
            if (event instanceof MouseEvent me) {
                TreePath path = tree.getPathForLocation(me.getX(), me.getY());
                if (path == null) return false;
                Rectangle bounds = tree.getPathBounds(path);
                return bounds != null && me.getX() < bounds.x + checkBox.getPreferredSize().width;
            }
            return true;
        }
    }
}