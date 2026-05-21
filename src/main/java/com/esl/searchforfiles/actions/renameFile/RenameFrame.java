package com.esl.searchforfiles.actions.renameFile;


import com.esl.searchforfiles.model.FileInfo;
import com.esl.searchforfiles.ui.ResultsPanel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.List;

public class RenameFrame extends JFrame {

    private final RenameMode mode;
    private final List<FileInfo> items;
    private final RenameTableModel tableModel;
    private final Set<String> usedTags = new LinkedHashSet<>();

    // Guarda o resultado de cada linha após a operação:
    // null  = ainda não processado
   // true  = renomeado com sucesso
   // false = falhou ou pulado
    private final Map<Integer, Boolean> rowResults = new LinkedHashMap<>();
    private volatile boolean cancelRename = false;
    private JTextField nameField;
    private JList<RenameTag> tagList;
    private JTable table;

    private final ResultsPanel resultsPanel;
    private boolean renamedActionPerformed = false;


    public RenameFrame(Window owner, RenameMode mode, List<FileInfo> items, ResultsPanel resultsPanel) {
        super(mode.label + " — " + items.size() + " item(s) selecionado(s)");
        this.mode = mode;
        this.items = items;
        this.resultsPanel = resultsPanel;
        this.tableModel = new RenameTableModel(mode, items);

        if (owner != null) owner.setEnabled(false);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (owner != null) {
                    owner.setEnabled(true);
                    owner.toFront();
                }
                if(renamedActionPerformed) {
                    resultsPanel.getFileExplorerSwing().getSearchPanel().triggerSearch();
                }
                resultsPanel.exitRenameMode();
            }
        });

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1400, 800);
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
        renamedActionPerformed = false;
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
            @Override
            public void insertUpdate(DocumentEvent e) {
                syncTable();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                syncTable();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                syncTable();
            }
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
            @Override
            public void mouseClicked(MouseEvent e) {
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

        panel.add(topArea, BorderLayout.NORTH);
        panel.add(centerArea, BorderLayout.CENTER);

        // Botões inferiores
        JPanel btnRow = new JPanel(new GridLayout(1, 2, 6, 0));
        btnRow.setOpaque(false);
        btnRow.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        JButton renameBtn = makeBtn("Renomear", mode.accentColor);
        renameBtn.addActionListener(e -> onRename());
        JButton closeBtn = makeBtn("Fechar", new Color(90, 90, 90));
        closeBtn.addActionListener(e -> {
            dispose();

        } );

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
        table.setRowHeight(ThumbnailCellRenderer.ROW_HEIGHT);
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

        // Coluna de thumbnail (FILES)
        if (mode == RenameMode.FILES) {
            int colW = ThumbnailCellRenderer.THUMB_SIZE + 12;
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

        // Renderers de resultado — aplicados após a operação de renomeação
        ResultCellRenderer  resultRenderer  = new ResultCellRenderer();
        CompositeNameRenderer nameRenderer  = new CompositeNameRenderer();

        if (mode == RenameMode.FILES) {
            // col 0 = thumb (já configurado acima)
            table.getColumnModel().getColumn(1).setCellRenderer(resultRenderer);  // nome atual
            table.getColumnModel().getColumn(2).setCellRenderer(nameRenderer);    // novo nome
            table.getColumnModel().getColumn(3).setCellRenderer(resultRenderer);  // caminho
        } else {
            table.getColumnModel().getColumn(0).setCellRenderer(resultRenderer);  // nome atual
            table.getColumnModel().getColumn(1).setCellRenderer(nameRenderer);    // novo nome
            table.getColumnModel().getColumn(2).setCellRenderer(resultRenderer);  // caminho
        }

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

    /**
     * Sincroniza a tabela com o texto atual do campo.
     */

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

    /**
     * Insere o código da tag na posição do cursor do nameField.
     */

    private void insertAtCaret(String code) {
        if (usedTags.contains(code)) return; // já em uso, ignora

        int pos = nameField.getCaretPosition();
        String current = nameField.getText();
        String updated = current.substring(0, pos) + code + current.substring(pos);
        nameField.setText(updated);
        nameField.setCaretPosition(pos + code.length());
        // syncTable() será chamado pelo DocumentListener automaticamente
    }



    private void onRename() {
        if (nameField.getText().isBlank()) {
            JOptionPane.showMessageDialog(this,
                    "Digite um nome base antes de renomear.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Monta prévia completa para confirmação
        List<FileInfo> items    = tableModel.getItems();
        List<String>   previews = new ArrayList<>();
        for (int i = 0; i < items.size(); i++)
            previews.add(tableModel.previewName(i));

        // Verifica se algum novo nome ficou em branco
        long blanks = previews.stream().filter(String::isBlank).count();
        if (blanks > 0) {
            JOptionPane.showMessageDialog(this,
                    blanks + " item(s) resultariam em nome vazio. Revise o padrão.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int opt = JOptionPane.showConfirmDialog(this,
                "Renomear " + items.size() + " item(s)?\n\n"
                        + "Esta ação não pode ser desfeita.",
                "Confirmar renomeação",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (opt != JOptionPane.YES_OPTION) return;

        // Limpa resultados anteriores e inicia worker
        rowResults.clear();
        table.repaint();
        executeRename(items, previews);
    }


    // ═══════════════════════════════════════════════════════════════════
// executeRename() — NOVO
// SwingWorker que processa cada item sequencialmente,
// publicando o resultado de cada linha para a EDT em tempo real.
// ═══════════════════════════════════════════════════════════════════
    private void executeRename(List<FileInfo> items, List<String> newNames) {

        // Desabilita o botão durante a operação
        // (referência guardada para reabilitar no done())
//        Component[] btns = ((JPanel) ((BorderLayout) ((JPanel) getContentPane()
//                .getComponent(0)).getLayout())
//                .getLayoutComponent(BorderLayout.SOUTH))
//                .getComponents();
        // Abordagem mais segura: desabilita todos os botões do painel sul
        JPanel leftPanel = (JPanel) ((JSplitPane) getContentPane()
                .getComponent(0)).getLeftComponent();
        JPanel btnRow = (JPanel) ((BorderLayout) leftPanel.getLayout())
                .getLayoutComponent(BorderLayout.SOUTH);
        Arrays.stream(btnRow.getComponents()).forEach(c -> c.setEnabled(false));

        // Callback para conflito — precisa rodar na EDT e bloquear o worker
        // Usamos um array de 1 elemento para passar o resultado entre threads
        new SwingWorker<Void, int[]>() {
            // int[] = { rowIndex, 1=sucesso / 0=falha }

            @Override
            protected Void doInBackground() {
                for (int i = 0; i < items.size(); i++) {
                    FileInfo fi      = items.get(i);
                    String   newName = newNames.get(i);
                    boolean  ok      = renameItem(fi, newName, i);
                    publish(new int[]{i, ok ? 1 : 0});
                }
                return null;
            }

            @Override
            protected void process(List<int[]> chunks) {
                for (int[] chunk : chunks) {
                    int  row    = chunk[0];
                    boolean ok  = chunk[1] == 1;
                    rowResults.put(row, ok);
                    // Repinta apenas a linha processada
                    table.repaint(table.getCellRect(row, 0, true)
                            .union(table.getCellRect(row,
                                    table.getColumnCount() - 1, true)));
                }
            }

            @Override
            protected void done() {
                Arrays.stream(btnRow.getComponents()).forEach(c -> c.setEnabled(true));

                long success = rowResults.values().stream().filter(v -> v).count();
                long failed  = rowResults.values().stream().filter(v -> !v).count();

                String msg = "✅ " + success + " renomeado(s)";
                if (failed > 0) msg += "\n⚠️ " + failed + " com erro — veja as linhas em vermelho.";
                JOptionPane.showMessageDialog(RenameFrame.this, msg,
                        "Resultado", JOptionPane.INFORMATION_MESSAGE);
                renamedActionPerformed = true;
            }
        }.execute();
    }


    // ═══════════════════════════════════════════════════════════════════
// renameItem() — NOVO
// Executa a renomeação de um único item.
// Lida com conflitos perguntando ao usuário na EDT (via invokeAndWait).
// Retorna true se renomeado com sucesso, false se pulado/falhou.
// ═══════════════════════════════════════════════════════════════════
    private boolean renameItem(FileInfo fi, String newName, int rowIndex) {
        try {
            Path source = Paths.get(fi.getPath());
            Path dest   = source.resolveSibling(newName);

            // Sem mudança real — pula silenciosamente
            if (source.equals(dest)) return true;

            if (Files.exists(dest)) {
                // Conflito — pergunta ao usuário na EDT e aguarda resposta
                int[] answer = {-1}; // 0=sobrescrever, 1=pular, 2=cancelar tudo
                try {
                    SwingUtilities.invokeAndWait(() ->
                            answer[0] = showConflictDialog(fi.getName(), newName));
                } catch (Exception e) {
                    return false;
                }

                switch (answer[0]) {
                    case 0 -> { /* sobrescrever — continua abaixo */ }
                    case 1 -> { return false; } // pular este item
                    case 2 -> { cancelAll();   return false; }
                    default -> { return false; }
                }
            }

            Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
            // Atualiza o path no FileInfo para refletir o novo nome
            fi.setPath(dest.toString());
            fi.setName(newName);
            return true;

        } catch (IOException e) {
            System.err.println("Erro ao renomear " + fi.getName()
                    + " → " + newName + ": " + e.getMessage());
            return false;
        }
    }


    // ═══════════════════════════════════════════════════════════════════
// showConflictDialog() — NOVO
// Exibido na EDT quando o nome de destino já existe.
// Retorna: 0=sobrescrever, 1=pular, 2=cancelar tudo.
// ═══════════════════════════════════════════════════════════════════
    private int showConflictDialog(String originalName, String newName) {
        String msg = "O arquivo \"" + newName + "\" já existe.\n\n"
                + "Origem:  " + originalName + "\n"
                + "Destino: " + newName + "\n\n"
                + "O que deseja fazer?";

        String[] options = {"Sobrescrever", "Pular este", "Cancelar restantes"};
        int choice = JOptionPane.showOptionDialog(
                RenameFrame.this, msg, "Conflito de nome",
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                null, options, options[1]); // padrão = "Pular este"

        return choice < 0 ? 1 : choice; // fechar = pular
    }


    private void cancelAll() { cancelRename = true; }


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

    /**
     * Renderiza cada tag com seu código em destaque.
     */

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


    // ═══════════════════════════════════════════════════════════════════
// ResultCellRenderer — NOVO (classe interna de RenameFrame)
// Colore as linhas da tabela conforme o resultado:
//   verde  = sucesso
//   vermelho = falha
//   normal = ainda não processado
// Aplica-se a TODAS as colunas exceto a de thumbnail.
// ═══════════════════════════════════════════════════════════════════
    private class ResultCellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int col) {

            super.getTableCellRendererComponent(t, value, isSelected,
                    hasFocus, row, col);

            Boolean result = rowResults.get(row);

            if (result == null) {
                // Ainda não processado — visual padrão
                setBackground(isSelected
                        ? new Color(50, 80, 130)
                        : new Color(45, 45, 45));
                setForeground(new Color(210, 210, 210));

            } else if (result) {
                // Sucesso — fundo verde escuro
                setBackground(isSelected
                        ? new Color(30, 90, 30)
                        : new Color(28, 60, 28));
                setForeground(new Color(140, 220, 140));

            } else {
                // Falha — fundo vermelho escuro
                setBackground(isSelected
                        ? new Color(100, 30, 30)
                        : new Color(70, 25, 25));
                setForeground(new Color(220, 120, 120));
            }

            setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
            return this;
        }
    }


    // ═══════════════════════════════════════════════════════════════════
// CompositeNameRenderer — NOVO (classe interna)
// Na coluna "Novo nome": verde quando não processado (prévia),
// herda a cor de resultado (verde/vermelho) após processamento.
// ═══════════════════════════════════════════════════════════════════
    private class CompositeNameRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int col) {

            super.getTableCellRendererComponent(t, value, isSelected,
                    hasFocus, row, col);

            Boolean result = rowResults.get(row);

            if (result == null) {
                // Ainda não processado — texto verde (prévia)
                setBackground(isSelected
                        ? new Color(50, 80, 130)
                        : new Color(45, 45, 45));
                setForeground(isSelected
                        ? Color.WHITE
                        : new Color(100, 200, 120));
                setFont(getFont().deriveFont(Font.BOLD));

            } else if (result) {
                setBackground(isSelected
                        ? new Color(30, 90, 30)
                        : new Color(28, 60, 28));
                setForeground(new Color(140, 220, 140));
                setFont(getFont().deriveFont(Font.BOLD));

            } else {
                setBackground(isSelected
                        ? new Color(100, 30, 30)
                        : new Color(70, 25, 25));
                setForeground(new Color(220, 120, 120));
                setFont(getFont().deriveFont(Font.PLAIN));
            }

            setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
            return this;
        }
    }
}