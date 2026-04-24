package com.esl.searchforfiles.ui;


import com.esl.searchforfiles.database.DatabaseManager;
import com.esl.searchforfiles.model.FileInfo;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

// ════════════════════════════════════════════════════════════════
// 6. TagManagerDialog — JDialog completo para adicionar/remover tags
// ════════════════════════════════════════════════════════════════

public class TagManagerDialog extends JDialog {

    private final FileInfo        fileInfo;
    private final DatabaseManager dbManager;

    private DefaultListModel<String> listModel;
    private JList<String>            tagList;
    private AutocompleteTagField     autocompleteField; // NOVO — substitui JTextField

    public TagManagerDialog(Frame owner, FileInfo fileInfo, DatabaseManager dbManager) {
        super(owner, "🏷️  Tags — " + fileInfo.getName(), true);
        this.fileInfo  = fileInfo;
        this.dbManager = dbManager;

        setSize(400, 360);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(buildHeader(),  BorderLayout.NORTH);
        add(buildCenter(),  BorderLayout.CENTER);
        add(buildFooter(),  BorderLayout.SOUTH);

        loadTags();
    }

    private JLabel buildHeader() {
        JLabel lbl = new JLabel("Arquivo: " + fileInfo.getName());
        lbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        return lbl;
    }

    private JPanel buildCenter() {
        // ── Lista de tags aplicadas ───────────────────────────────
        listModel = new DefaultListModel<>();
        tagList   = new JList<>(listModel);
        tagList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tagList.setFont(new Font("SansSerif", Font.PLAIN, 13));
        tagList.setCellRenderer(new TagCellRenderer());

        JScrollPane scroll = new JScrollPane(tagList);
        scroll.setBorder(BorderFactory.createTitledBorder("Tags aplicadas"));

        // ── Campo de autocomplete ─────────────────────────────────
        autocompleteField = new AutocompleteTagField(
                dbManager, getCurrentTagsAsSet());

        // Quando usuário seleciona/confirma uma sugestão → adiciona direto
        autocompleteField.setTagSelectedListener(this::addTag);

        JButton addBtn = new JButton("＋ Adicionar");
        addBtn.addActionListener(e -> addTag(autocompleteField.getText()));

        JPanel inputRow = new JPanel(new BorderLayout(5, 0));
        inputRow.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        inputRow.add(autocompleteField, BorderLayout.CENTER);
        inputRow.add(addBtn,            BorderLayout.EAST);

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(scroll,    BorderLayout.CENTER);
        panel.add(inputRow,  BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildFooter() {
        JButton removeBtn = new JButton("🗑  Remover selecionada");
        removeBtn.addActionListener(e -> removeSelectedTag());

        JButton renameBtn = new JButton("✏️  Renomear...");
        renameBtn.addActionListener(e -> renameSelectedTag());

        JButton closeBtn = new JButton("Fechar");
        closeBtn.addActionListener(e -> dispose());

        JPanel left  = new JPanel(new FlowLayout(FlowLayout.LEFT,  4, 0));
        left.add(removeBtn);
        left.add(renameBtn);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.add(closeBtn);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(left,  BorderLayout.WEST);
        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    // ── Operações ─────────────────────────────────────────────────

    private void loadTags() {
        try {
            listModel.clear();
            for (String t : dbManager.getTagsForFile(fileInfo.getPath()))
                listModel.addElement(t);
            // Atualiza o conjunto do autocomplete
            autocompleteField.setAlreadyApplied(getCurrentTagsAsSet());
        } catch (SQLException ex) {
            showError("Erro ao carregar tags: " + ex.getMessage());
        }
    }

    private void addTag(String name) {
        if (name == null || name.isBlank()) return;
        name = name.trim();

        if (listModel.contains(name)) {
            JOptionPane.showMessageDialog(this,
                    "A tag \"" + name + "\" já está aplicada a este arquivo.",
                    "Tag duplicada", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            dbManager.addTagToFile(fileInfo.getPath(), name);
            listModel.addElement(name);
            autocompleteField.clear();
            autocompleteField.setAlreadyApplied(getCurrentTagsAsSet()); // atualiza exclusões
            autocompleteField.requestFieldFocus();
        } catch (SQLException ex) {
            showError("Erro ao adicionar tag: " + ex.getMessage());
        }
    }

    private void removeSelectedTag() {
        String selected = tagList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Selecione uma tag para remover.",
                    "Nenhuma seleção", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Remover a tag \"" + selected + "\" deste arquivo?",
                "Confirmar remoção", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            dbManager.removeTagFromFile(fileInfo.getPath(), selected);
            listModel.removeElement(selected);
            autocompleteField.setAlreadyApplied(getCurrentTagsAsSet());
        } catch (SQLException ex) {
            showError("Erro ao remover tag: " + ex.getMessage());
        }
    }

    private void renameSelectedTag() {
        String selected = tagList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Selecione uma tag para renomear.",
                    "Nenhuma seleção", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String newName = JOptionPane.showInputDialog(this,
                "Novo nome para a tag \"" + selected + "\":", selected);

        if (newName == null || newName.isBlank() || newName.equals(selected)) return;
        newName = newName.trim();

        try {
            dbManager.removeTagFromFile(fileInfo.getPath(), selected);
            dbManager.addTagToFile(fileInfo.getPath(), newName);
            int idx = listModel.indexOf(selected);
            listModel.set(idx, newName);
            autocompleteField.setAlreadyApplied(getCurrentTagsAsSet());
        } catch (SQLException ex) {
            showError("Erro ao renomear tag: " + ex.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    /** Snapshot das tags atuais como Set para excluir do autocomplete. */
    private Set<String> getCurrentTagsAsSet() {
        Set<String> set = new HashSet<>();
        for (int i = 0; i < listModel.size(); i++)
            set.add(listModel.getElementAt(i));
        return set;
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Erro", JOptionPane.ERROR_MESSAGE);
    }

    // ── Renderer ─────────────────────────────────────────────────
    private static class TagCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {

            JLabel lbl = (JLabel) super.getListCellRendererComponent(
                    list, "  🏷️  " + value + "  ", index, isSelected, cellHasFocus);

            if (!isSelected)
                lbl.setBackground(index % 2 == 0 ? new Color(245, 245, 245) : Color.WHITE);

            lbl.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            return lbl;
        }
    }
}