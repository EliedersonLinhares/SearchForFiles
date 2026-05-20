package com.esl.searchforfiles.actions.renameFile;


import com.esl.searchforfiles.model.FileInfo;

import javax.swing.table.AbstractTableModel;
import java.io.File;
import java.util.List;

public class RenameTableModel extends AbstractTableModel {

    private final RenameMode     mode;
    private final List<FileInfo> items;      // mutável para permitir reordenação
    private String               namePattern = "";

    private static final String[] COLS_FILES   = {"", "Nome atual", "Novo nome", "Caminho"};
    private static final String[] COLS_FOLDERS = {"Nome atual", "Novo nome", "Caminho"};

    public RenameTableModel(RenameMode mode, List<FileInfo> items) {
        this.mode  = mode;
        this.items = new java.util.ArrayList<>(items); // cópia mutável
    }

    public void setNamePattern(String pattern) {
        this.namePattern = pattern == null ? "" : pattern;
        int col = mode == RenameMode.FILES ? 2 : 1;
        fireTableColumnUpdated(col);
    }

    private void fireTableColumnUpdated(int col) {
        fireTableChanged(new javax.swing.event.TableModelEvent(
                this, 0, getRowCount() - 1, col));
    }

    /** Move a linha 'from' para a posição 'to'. */
    public void moveRow(int from, int to) {
        if (from == to || from < 0 || to < 0
                || from >= items.size() || to >= items.size()) return;
        FileInfo item = items.remove(from);
        items.add(to, item);
        // Notifica a faixa afetada
        int lo = Math.min(from, to);
        int hi = Math.max(from, to);
        fireTableRowsUpdated(lo, hi);
    }

    public String previewName(int rowIndex) {
        if (namePattern.isBlank()) return "";
        FileInfo fi  = items.get(rowIndex);
        String   ext = fi.getExtension().isBlank() ? ""
                : "." + fi.getExtension().toLowerCase();
        String result = namePattern
                .replace("<inc Nr>",    String.format("%02d", rowIndex + 1))
                .replace("<inc Alpha>", alphaIndex(rowIndex))
                .replace("<ad. pasta>", parentName(fi))
                .replace("<data>",      java.time.LocalDate.now()
                        .format(java.time.format.DateTimeFormatter
                                .ofPattern("yyyyMMdd")))
                .replace("<ext>",       fi.getExtension().toLowerCase())
                .replace("<nome orig>", stripExt(fi.getName()));
        if (mode == RenameMode.FILES && !result.contains("."))
            result += ext;
        return result;
    }

    private String alphaIndex(int i) {
        StringBuilder sb = new StringBuilder();
        i++;
        while (i > 0) { sb.insert(0, (char)('a' + (i - 1) % 26)); i = (i - 1) / 26; }
        return sb.toString();
    }
    private String parentName(FileInfo fi) {
        File parent = new File(fi.getPath()).getParentFile();
        return parent != null ? parent.getName() : "";
    }
    private String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @Override public int getRowCount()    { return items.size(); }
    @Override public boolean isCellEditable(int r, int c) { return false; }
    @Override public int getColumnCount() {
        return mode == RenameMode.FILES ? COLS_FILES.length : COLS_FOLDERS.length;
    }
    @Override public String getColumnName(int col) {
        return mode == RenameMode.FILES ? COLS_FILES[col] : COLS_FOLDERS[col];
    }
    @Override public Object getValueAt(int row, int col) {
        FileInfo fi = items.get(row);
        if (mode == RenameMode.FILES) {
            return switch (col) {
                case 0 -> fi;
                case 1 -> fi.getName();
                case 2 -> previewName(row);
                case 3 -> new File(fi.getPath()).getParent();
                default -> "";
            };
        } else {
            return switch (col) {
                case 0 -> fi.getName();
                case 1 -> previewName(row);
                case 2 -> new File(fi.getPath()).getParent();
                default -> "";
            };
        }
    }
    @Override public Class<?> getColumnClass(int col) {
        if (mode == RenameMode.FILES && col == 0) return FileInfo.class;
        return String.class;
    }
    public FileInfo getItem(int row) { return items.get(row); }
    public List<FileInfo> getItems() { return items; }
}