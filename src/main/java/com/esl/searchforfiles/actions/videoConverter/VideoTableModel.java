package com.esl.searchforfiles.actions.videoConverter;

import com.esl.searchforfiles.model.FileInfo;

import javax.swing.table.AbstractTableModel;
import java.io.File;
import java.util.*;

public class VideoTableModel extends AbstractTableModel {

    public enum Status { WAITING, PROCESSING, SUCCESS, ERROR, CANCELLED }

    private static final String[] COLUMNS = { "Nome", "Tamanho", "Caminho", "Status", "" };
    private static final int COL_WARNING = 4;

    private final List<FileInfo> items;
    private final List<Status> statuses;

    /* Avisos/erros de compatibilidade por linha, populados por applyValidation().
       null = ainda não validado ou sem nada a reportar. */
    private final List<FormatCompatibility.ValidationResult> validations;

    public VideoTableModel(List<FileInfo> items) {
        this.items = new ArrayList<>(items != null ? items : List.of());
        this.statuses = new ArrayList<>(this.items.size());
        this.validations = new ArrayList<>(this.items.size());
        for (int i = 0; i < this.items.size(); i++) {
            statuses.add(Status.WAITING);
            validations.add(null);
        }
    }

    @Override public int getRowCount() { return items.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int c) { return COLUMNS[c]; }

    @Override
    public Class<?> getColumnClass(int c) {
        return c == 1 ? Long.class : String.class;
    }

    @Override
    public Object getValueAt(int row, int col) {
        FileInfo info = items.get(row);
        return switch (col) {
            case 0 -> info.getName();
            case 1 -> info.getSize();
            case 2 -> info.getPath();
            case 3 -> statusLabel(statuses.get(row));
            case COL_WARNING -> warningSymbol(row);
            default -> "";
        };
    }

    private String statusLabel(Status s) {
        return switch (s) {
            case WAITING -> "Aguardando";
            case PROCESSING -> "Convertendo...";
            case SUCCESS -> "✓ Concluído";
            case ERROR -> "✗ Erro";
            case CANCELLED -> "Cancelado";
        };
    }

    private String warningSymbol(int row) {
        FormatCompatibility.ValidationResult v = validations.get(row);
        if (v == null) return "";
        if (!v.isValid()) return "⛔"; // erro: combinação impossível para este arquivo
        if (v.hasWarnings()) return "⚠"; // aviso: possível, mas com ressalvas
        return "";
    }

    /** Tooltip mostrado ao passar o mouse sobre a coluna de aviso, para uma linha específica. */
    public String getWarningTooltip(int row) {
        if (row < 0 || row >= validations.size()) return null;
        FormatCompatibility.ValidationResult v = validations.get(row);
        if (v == null) return null;

        StringBuilder sb = new StringBuilder("<html>");
        for (String err : v.errors()) {
            sb.append("<b>Erro:</b> ").append(escapeHtml(err)).append("<br>");
        }
        for (String warn : v.warnings()) {
            sb.append("<b>Aviso:</b> ").append(escapeHtml(warn)).append("<br>");
        }
        sb.append("</html>");
        return sb.toString();
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public boolean rowHasError(int row) {
        FormatCompatibility.ValidationResult v = validations.get(row);
        return v != null && !v.isValid();
    }

    public boolean rowHasWarning(int row) {
        FormatCompatibility.ValidationResult v = validations.get(row);
        return v != null && v.hasWarnings();
    }

    /** Aplica o resultado da validação em lote, marcando cada linha correspondente. */
    public void applyValidation(FormatCompatibility.BatchValidationResult batchResult) {
        for (int i = 0; i < items.size(); i++) {
            String path = new File(items.get(i).getPath()).getAbsolutePath();
            validations.set(i, batchResult.perFile().get(path));
        }
        fireTableDataChanged();
    }

    /** Limpa os indicadores de validação (ex: ao trocar as opções de conversão). */
    public void clearValidation() {
        for (int i = 0; i < validations.size(); i++) validations.set(i, null);
        fireTableDataChanged();
    }

    public FileInfo getItem(int row) { return items.get(row); }
    public List<FileInfo> getItems() { return items; }
    public Status getStatus(int row) { return statuses.get(row); }

    public void setStatus(int row, Status status) {
        if (row < 0 || row >= statuses.size()) return;
        statuses.set(row, status);
        fireTableRowsUpdated(row, row);
    }

    public void markProcessing(String fileName) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getName().equals(fileName) && statuses.get(i) == Status.WAITING) {
                statuses.set(i, Status.PROCESSING);
                fireTableRowsUpdated(i, i);
            }
        }
    }

    public void applyResults(BatchConversionResult result) {
        for (FileConversionResult r : result.getResults()) {
            for (int i = 0; i < items.size(); i++) {
                if (new File(items.get(i).getPath()).equals(r.getSource())) {
                    statuses.set(i, switch (r.getStatus()) {
                        case SUCCESS -> Status.SUCCESS;
                        case ERROR -> Status.ERROR;
                        case CANCELLED -> Status.CANCELLED;
                        case SKIPPED -> Status.WAITING;
                    });
                }
            }
        }
        fireTableDataChanged();
    }

    public void resetStatuses() {
        for (int i = 0; i < statuses.size(); i++) statuses.set(i, Status.WAITING);
        fireTableDataChanged();
    }
}