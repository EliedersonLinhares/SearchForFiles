package com.esl.searchforfiles.configuration.logger;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Dialog reutilizável para exibição dos logs da aplicação.
 *
 * Abrindo de qualquer lugar:
 *   LogDialog.show(parentFrame);          // abre ou traz para frente
 *   LogDialog.show(parentFrame, true);    // força nova janela
 */
public class LogDialog extends JDialog implements AppLogger.LogListener {

    // ── Cores por nível ───────────────────────────────────────────────────────

    private static final Color COLOR_INFO     = new Color(0x2196F3); // azul
    private static final Color COLOR_WARN     = new Color(0xFF9800); // laranja
    private static final Color COLOR_ERROR    = new Color(0xF44336); // vermelho
    private static final Color COLOR_CRITICAL = new Color(0x9C27B0); // roxo
    private static final Color BG_INFO        = new Color(0xE3F2FD);
    private static final Color BG_WARN        = new Color(0xFFF3E0);
    private static final Color BG_ERROR       = new Color(0xFFEBEE);
    private static final Color BG_CRITICAL    = new Color(0xF3E5F5);

    // ── Modelo de tabela ──────────────────────────────────────────────────────

    private static final String[] COLUMNS = {"Hora", "Nível", "Origem", "Mensagem"};

    private static class LogTableModel extends AbstractTableModel {
        private final List<AppLogger.LogEntry> rows = new ArrayList<>();

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }

        @Override
        public Object getValueAt(int row, int col) {
            AppLogger.LogEntry e = rows.get(row);
            return switch (col) {
                case 0 -> e.formattedTime();
                case 1 -> e.level().label;
                case 2 -> e.source();
                case 3 -> e.message();
                default -> "";
            };
        }

        void addEntry(AppLogger.LogEntry entry) {
            rows.add(entry);
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        void setEntries(List<AppLogger.LogEntry> entries) {
            rows.clear();
            rows.addAll(entries);
            fireTableDataChanged();
        }

        AppLogger.LogEntry getEntry(int row) {
            return rows.get(row);
        }
    }

    // ── Renderer colorido ─────────────────────────────────────────────────────

    private static class LevelRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {

            Component c = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);

            if (!isSelected) {
                LogTableModel model = (LogTableModel) table.getModel();
                // row é visível — converte para modelo se houver sorter
                int modelRow = table.convertRowIndexToModel(row);
                AppLogger.Level level = model.getEntry(modelRow).level();
                c.setBackground(switch (level) {
                    case INFO     -> BG_INFO;
                    case WARN     -> BG_WARN;
                    case ERROR    -> BG_ERROR;
                    case CRITICAL -> BG_CRITICAL;
                });
                c.setForeground(switch (level) {
                    case INFO     -> COLOR_INFO;
                    case WARN     -> COLOR_WARN;
                    case ERROR    -> COLOR_ERROR;
                    case CRITICAL -> COLOR_CRITICAL;
                });
            }
            return c;
        }
    }

    // ── Campos do dialog ──────────────────────────────────────────────────────

    private final LogTableModel   tableModel  = new LogTableModel();
    private final JTable          table       = new JTable(tableModel);
    private final JLabel          countLabel  = new JLabel("0 entradas");
    private final JCheckBox       autoScroll  = new JCheckBox("Auto-scroll", true);

    /** Filtros de nível ativos */
    private final Set<AppLogger.Level> activeFilters =
            EnumSet.allOf(AppLogger.Level.class);

    // ── Instância singleton opcional (uma janela por vez) ─────────────────────

    private static LogDialog singletonInstance;

    /**
     * Abre (ou traz para frente) uma única janela de log.
     * Thread-safe: pode ser chamado de qualquer thread.
     */
    public static void show(Frame parent) {
        SwingUtilities.invokeLater(() -> {
            if (singletonInstance == null || !singletonInstance.isDisplayable()) {
                singletonInstance = new LogDialog(parent);
            }
            singletonInstance.setVisible(true);
            singletonInstance.toFront();
        });
    }

    // ── Construtor ────────────────────────────────────────────────────────────

    public LogDialog(Frame parent) {
        super(parent, "Log do Sistema", false); // não-modal
        buildUi();
        loadHistory();
        AppLogger.get().addListener(this);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                AppLogger.get().removeListener(LogDialog.this);
            }
        });
        setSize(820, 460);
        setLocationRelativeTo(parent);
    }

    // ── Construção da UI ──────────────────────────────────────────────────────

    private void buildUi() {
        setLayout(new BorderLayout(0, 4));

        // ── Barra de filtros ──
        add(buildFilterBar(), BorderLayout.NORTH);

        // ── Tabela ──
        configureTable();
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        // ── Rodapé ──
        add(buildFooter(), BorderLayout.SOUTH);
    }

    private JPanel buildFilterBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        bar.add(new JLabel("Exibir:"));

        for (AppLogger.Level level : AppLogger.Level.values()) {
            JCheckBox cb = new JCheckBox(level.icon + " " + level.label, true);
            cb.setForeground(levelColor(level));
            cb.addActionListener(e -> toggleFilter(level, cb.isSelected()));
            bar.add(cb);
        }

        bar.add(Box.createHorizontalStrut(16));

        JButton btnClear = new JButton("Limpar");
        btnClear.addActionListener(e -> clearTable());
        bar.add(btnClear);

        return bar;
    }

    private void configureTable() {
        table.setRowHeight(22);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        table.getTableHeader().setReorderingAllowed(false);

        // Larguras de coluna
        table.getColumnModel().getColumn(0).setPreferredWidth(60);
        table.getColumnModel().getColumn(0).setMaxWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(70);
        table.getColumnModel().getColumn(1).setMaxWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setMaxWidth(180);
        table.getColumnModel().getColumn(3).setPreferredWidth(500);

        LevelRenderer renderer = new LevelRenderer();
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        // Clique duplo — mostra mensagem completa
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) showDetail();
            }
        });
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        footer.add(countLabel, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(autoScroll);

        JButton btnExport = new JButton("Exportar TXT");
        btnExport.addActionListener(e -> exportLogs());
        right.add(btnExport);

        footer.add(right, BorderLayout.EAST);
        return footer;
    }

    // ── Lógica ────────────────────────────────────────────────────────────────

    /** Chamado pela EDT quando chega um novo log. */
    @Override
    public void onLog(AppLogger.LogEntry entry) {
        // onLog já é disparado na EDT pelo AppLogger
        if (!activeFilters.contains(entry.level())) return;
        tableModel.addEntry(entry);
        updateCount();
        if (autoScroll.isSelected()) scrollToBottom();
    }

    private void toggleFilter(AppLogger.Level level, boolean active) {
        if (active) activeFilters.add(level); else activeFilters.remove(level);
        reloadFiltered();
    }

    private void reloadFiltered() {
        List<AppLogger.LogEntry> filtered = AppLogger.get().getEntries().stream()
                .filter(e -> activeFilters.contains(e.level()))
                .toList();
        tableModel.setEntries(filtered);
        updateCount();
        if (autoScroll.isSelected()) scrollToBottom();
    }

    private void loadHistory() {
        tableModel.setEntries(AppLogger.get().getEntries());
        updateCount();
        scrollToBottom();
    }

    private void clearTable() {
        AppLogger.get().clear();
        tableModel.setEntries(List.of());
        updateCount();
    }

    private void showDetail() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = table.convertRowIndexToModel(viewRow);
        AppLogger.LogEntry e = tableModel.getEntry(modelRow);
        JTextArea area = new JTextArea(
                "%s  [%s]  %s\n\n%s".formatted(
                        e.formattedTime(), e.level().label, e.source(), e.message()),
                6, 50);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JOptionPane.showMessageDialog(this, new JScrollPane(area),
                "Detalhe do log", JOptionPane.PLAIN_MESSAGE);
    }

    private void exportLogs() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("logs.txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try (var writer = new java.io.PrintWriter(chooser.getSelectedFile())) {
            AppLogger.get().getEntries().forEach(writer::println);
            JOptionPane.showMessageDialog(this, "Logs exportados com sucesso.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao exportar: " + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void scrollToBottom() {
        int last = table.getRowCount() - 1;
        if (last >= 0) table.scrollRectToVisible(table.getCellRect(last, 0, true));
    }

    private void updateCount() {
        countLabel.setText(tableModel.getRowCount() + " entradas");
    }

    private Color levelColor(AppLogger.Level level) {
        return switch (level) {
            case INFO     -> COLOR_INFO;
            case WARN     -> COLOR_WARN;
            case ERROR    -> COLOR_ERROR;
            case CRITICAL -> COLOR_CRITICAL;
        };
    }
}