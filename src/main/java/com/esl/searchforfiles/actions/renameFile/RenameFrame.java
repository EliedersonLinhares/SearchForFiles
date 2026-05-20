package com.esl.searchforfiles.actions.renameFile;


import com.esl.searchforfiles.model.FileInfo;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RenameFrame extends JFrame {

    private final RenameMode      mode;
    private final List<FileInfo>  items;
    private final RenameTableModel tableModel;

    private JTextField  nameField;
    private JList<RenameTag> tagList;
    private JTable      table;

    private final Set<String> usedTags = new LinkedHashSet<>();

    public RenameFrame(Window owner, RenameMode mode, List<FileInfo> items) {
        super(mode.label + " — " + items.size() + " item(s) selecionado(s)");
        this.mode       = mode;
        this.items      = items;
        this.tableModel = new RenameTableModel(mode, items);

        if (owner != null) owner.setEnabled(false);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                if (owner != null) { owner.setEnabled(true); owner.toFront(); }
            }
        });

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(920, 580);
        setMinimumSize(new Dimension(680, 420));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildLeftPanel(), buildRightPanel());
        split.setDividerLocation(260);
        split.setDividerSize(5);
        split.setResizeWeight(0.0);     // painel esquerdo fixo; direito expande
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);

        setVisible(true);
    }

    // ── Painel esquerdo ────────────────────────────────────────────
    private JPanel buildLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBackground(new Color(42, 42, 42));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Campo de texto no topo
        nameField = new JTextField();
        nameField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        nameField.setBackground(new Color(55, 55, 55));
        nameField.setForeground(Color.WHITE);
        nameField.setCaretColor(Color.WHITE);
        nameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 80, 80)),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));

        nameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { syncTable(); }
            @Override public void removeUpdate(DocumentEvent e)  { syncTable(); }
            @Override public void changedUpdate(DocumentEvent e) { syncTable(); }
        });

        // Label + campo numa faixa compacta no topo
        JPanel topArea = new JPanel(new BorderLayout(0, 2));
        topArea.setOpaque(false);
        topArea.add(sectionLabel("Nome base"), BorderLayout.NORTH);
        topArea.add(nameField, BorderLayout.CENTER);

        // Label tags imediatamente abaixo do campo (sem espaço extra)
        JPanel tagHeader = new JPanel(new BorderLayout());
        tagHeader.setOpaque(false);
        tagHeader.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));
        tagHeader.add(sectionLabel("Tags — clique para inserir no cursor"),
                BorderLayout.CENTER);

        // JList de tags
        DefaultListModel<RenameTag> listModel = new DefaultListModel<>();
        RenameTag.defaults().forEach(listModel::addElement);

        tagList = new JList<>(listModel);
        tagList.setBackground(new Color(50, 50, 50));
        tagList.setForeground(new Color(200, 200, 200));
        tagList.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tagList.setFixedCellHeight(28);
        tagList.setSelectionBackground(new Color(60, 100, 160));
        tagList.setCellRenderer(new TagCellRenderer());
        tagList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tagList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                RenameTag tag = tagList.getSelectedValue();
                if (tag == null) return;
                insertAtCaret(tag.code);
                tagList.clearSelection();
                nameField.requestFocusInWindow();
            }
        });

        JScrollPane tagScroll = new JScrollPane(tagList);
        tagScroll.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70)));
        tagScroll.setBackground(new Color(50, 50, 50));

        // Centro: label colada ao topo do scroll, sem gap extra
        JPanel centerArea = new JPanel(new BorderLayout(0, 0));
        centerArea.setOpaque(false);
        centerArea.add(tagHeader, BorderLayout.NORTH);
        centerArea.add(tagScroll, BorderLayout.CENTER);

        panel.add(topArea,    BorderLayout.NORTH);
        panel.add(centerArea, BorderLayout.CENTER);

        // Botões inferiores
        JPanel btnRow = new JPanel(new GridLayout(1, 2, 6, 0));
        btnRow.setOpaque(false);
        btnRow.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        JButton renameBtn = makeBtn("Renomear", mode.accentColor);
        renameBtn.addActionListener(e -> onRename());
        JButton closeBtn = makeBtn("Fechar", new Color(90, 90, 90));
        closeBtn.addActionListener(e -> dispose());

        btnRow.add(renameBtn);
        btnRow.add(closeBtn);
        panel.add(btnRow, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildRightPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(45, 45, 45));

        ThumbnailCellRenderer thumbRenderer = new ThumbnailCellRenderer();

        table = new JTable(tableModel);
        table.setBackground(new Color(45, 45, 45));
        table.setForeground(new Color(210, 210, 210));
        table.setGridColor(new Color(65, 65, 65));
        table.setRowHeight(ThumbnailCellRenderer.ROW_HEIGHT);   // ← ajuste via ROW_HEIGHT
        table.setFont(new Font("SansSerif", Font.PLAIN, 12));
        table.setSelectionBackground(new Color(50, 80, 130));
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        // Cabeçalho
        table.getTableHeader().setBackground(new Color(38, 38, 38));
        table.getTableHeader().setForeground(new Color(150, 150, 150));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
        table.getTableHeader().setBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(70, 70, 70)));
        table.getTableHeader().setReorderingAllowed(false);

        // Coluna de thumbnail (FILES) — largura calculada a partir de THUMB_SIZE
        if (mode == RenameMode.FILES) {
            int colW = ThumbnailCellRenderer.THUMB_SIZE + 12;  // ← ajuste via THUMB_SIZE
            table.getColumnModel().getColumn(0).setCellRenderer(thumbRenderer);
            table.getColumnModel().getColumn(0).setMinWidth(colW);
            table.getColumnModel().getColumn(0).setMaxWidth(colW);
            table.getColumnModel().getColumn(0).setPreferredWidth(colW);
        }

        // Colunas de dados com peso igual
        if (mode == RenameMode.FILES) {
            table.getColumnModel().getColumn(1).setPreferredWidth(220);
            table.getColumnModel().getColumn(2).setPreferredWidth(220);
            table.getColumnModel().getColumn(3).setPreferredWidth(220);
        } else {
            table.getColumnModel().getColumn(0).setPreferredWidth(220);
            table.getColumnModel().getColumn(1).setPreferredWidth(220);
            table.getColumnModel().getColumn(2).setPreferredWidth(220);
        }

        // Renderer "Novo nome" — verde
        int newNameCol = mode == RenameMode.FILES ? 2 : 1;
        table.getColumnModel().getColumn(newNameCol)
                .setCellRenderer(new NewNameCellRenderer());

        // Renderer "Caminho" — cinza menor
        int pathCol = mode == RenameMode.FILES ? 3 : 2;
        table.getColumnModel().getColumn(pathCol)
                .setCellRenderer(new PathCellRenderer());

        // Drag & drop para reordenar linhas
        new TableRowDragHandler(table, tableModel);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setBackground(new Color(45, 45, 45));
        scroll.getViewport().setBackground(new Color(45, 45, 45));
        scroll.getVerticalScrollBar().setUnitIncrement(12);

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // ── Lógica ─────────────────────────────────────────────────────

    /** Sincroniza a tabela com o texto atual do campo. */

    private void syncTable() {
        recalcUsedTags();
        tableModel.setNamePattern(nameField.getText());
        tagList.repaint(); // força re-render do TagCellRenderer
    }


    private void recalcUsedTags() {
        usedTags.clear();
        String text = nameField.getText();
        DefaultListModel<RenameTag> lm =
                (DefaultListModel<RenameTag>) tagList.getModel();
        for (int i = 0; i < lm.getSize(); i++) {
            String code = lm.getElementAt(i).code;
            if (text.contains(code)) usedTags.add(code);
        }
    }
    /** Insere o código da tag na posição do cursor do nameField. */

    private void insertAtCaret(String code) {
        if (usedTags.contains(code)) return; // já em uso, ignora

        int    pos     = nameField.getCaretPosition();
        String current = nameField.getText();
        String updated = current.substring(0, pos) + code + current.substring(pos);
        nameField.setText(updated);
        nameField.setCaretPosition(pos + code.length());
        // syncTable() será chamado pelo DocumentListener automaticamente
    }



    /**
     * Placeholder — lógica de renomeação em lote será implementada futuramente.
     */
    private void onRename() {
        if (nameField.getText().isBlank()) {
            JOptionPane.showMessageDialog(this,
                    "Digite um nome base antes de renomear.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JOptionPane.showMessageDialog(this,
                "Renomeação em lote ainda não implementada.",
                "Em breve", JOptionPane.INFORMATION_MESSAGE);
    }

    // ── Helpers ────────────────────────────────────────────────────
    private JLabel sectionLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 10));
        lbl.setForeground(new Color(120, 120, 120));
        lbl.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));
        return lbl;
    }

    private JButton makeBtn(String text, Color borderColor) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 13));
        btn.setForeground(Color.WHITE);
        btn.setBackground(new Color(55, 55, 55));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // ── Cell renderers internos ────────────────────────────────────

    /** Renderiza a coluna "Novo nome" em verde. */
    private static class NewNameCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v,
                                                       boolean sel, boolean foc, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            setForeground(sel ? Color.WHITE : new Color(100, 200, 120));
            setFont(getFont().deriveFont(Font.BOLD));
            return this;
        }
    }

    /** Renderiza a coluna "Caminho" em cinza menor. */
    private static class PathCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v,
                                                       boolean sel, boolean foc, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            setForeground(sel ? Color.WHITE : new Color(120, 120, 120));
            setFont(getFont().deriveFont(11f));
            return this;
        }
    }

    /** Renderiza cada tag com seu código em destaque. */

    private class TagCellRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {

            super.getListCellRendererComponent(list, value, index,
                    isSelected, cellHasFocus);

            if (!(value instanceof RenameTag tag)) return this;

            boolean inUse = usedTags.contains(tag.code);

            if (isSelected) {
                setBackground(new Color(60, 100, 160));
            } else if (inUse) {
                setBackground(new Color(35, 65, 35)); // fundo verde escuro
            } else {
                setBackground(new Color(50, 50, 50));
            }

            String codeColor = inUse ? "#7FD97F" : "#6BAEE8"; // verde : azul
            String descColor = inUse ? "#9FBF9F" : "#aaaaaa";
            String usedSuffix = inUse ? " <span style='color:#7FD97F'>✓ em uso</span>" : "";
            String strikeOpen = inUse ? "<s>" : "";
            String strikeClose = inUse ? "</s>" : "";

            setText("<html>"
                    + strikeOpen
                    + "<span style='font-family:monospace;color:" + codeColor + "'>"
                    + tag.code + "</span>"
                    + strikeClose
                    + "  <span style='color:" + descColor + "'>"
                    + tag.description + "</span>"
                    + usedSuffix
                    + "</html>");

            setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

            // Cursor de bloqueio quando a tag já está em uso
            setCursor(inUse
                    ? Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
                    : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            return this;
        }
    }
}