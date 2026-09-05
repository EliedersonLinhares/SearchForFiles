package com.esl.searchforfiles.actions.compressFile;

import com.esl.searchforfiles.model.FileInfo;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class ZipTableModel
        extends AbstractTableModel {

    public enum Status {
        WAITING,
        PROCESSING,
        SUCCESS,
        ERROR,
        SKIPPED
    }
    private static final String[] COLUMNS = {
            "",
            "Nome",
            "Tamanho",
            "Caminho",
            "Status"
    };

    private final List<FileInfo> items;
    private final List<Status> statuses;

    public ZipTableModel(List<FileInfo> items) {
        this.items = new ArrayList<>(items != null ? items : List.of());

        this.statuses = new ArrayList<>(this.items.size());

        for (int i = 0; i < this.items.size(); i++) {
            statuses.add(Status.WAITING);
        }
    }

    @Override
    public int getRowCount() {
        return items.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {return COLUMNS[column];}

    @Override
    public Class<?> getColumnClass(int column) {
        if (column == 0) {
            return FileInfo.class;
        }
        if (column == 2) {
            return Long.class;
        }
        return String.class;
    }

    @Override
    public Object getValueAt(int row, int column) {

        FileInfo info = items.get(row);

        return switch (column) {
            case 0 -> info;
            case 1 -> info.getName();
            case 2 -> info.getSize();
            case 3 -> info.getPath();
            case 4 -> getStatusText(statuses.get(row));
            default -> "";
        };
    }

    private String getStatusText(
            Status status) {
        return switch (status) {
            case WAITING -> "Aguardando";
            case PROCESSING -> "Compactando...";
            case SUCCESS -> "Concluído";
            case ERROR -> "Erro";
            case SKIPPED -> "Ignorado";
        };
    }

    public FileInfo getItem(int row) {
        return items.get(row);
    }

    public List<FileInfo> getItems() {
        return items;
    }

    public Status getStatus(int row) {
        return statuses.get(row);
    }

    public void setStatus(int row, Status status) {
        if (row < 0 || row >= statuses.size()) {
            return;
        }
        statuses.set(row, status);

        fireTableRowsUpdated(row, row);
    }

    public void resetStatuses() {
        for (int i = 0; i < statuses.size(); i++) {
            statuses.set(i, Status.WAITING);
        }
        fireTableDataChanged();
    }
}