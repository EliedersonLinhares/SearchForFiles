package com.esl.searchforfiles.service;


import com.esl.searchforfiles.model.TransferMode;

import javax.swing.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class TransferService {
    /** Listener chamado na EDT após operação concluir ou falhar. */
    public interface TransferListener {
        void onProgress(int done, int total, String currentFile);
        void onCompleted(int success, int failed);
        void onError(String message);
    }

    // ── Estado de seleção ─────────────────────────────────────────────
    private final Set<File> selectedFiles = new LinkedHashSet<>();
    private boolean transferModeActive = false;

    public boolean isTransferModeActive() { return transferModeActive; }

    public void enterTransferMode() {
        transferModeActive = true;
        selectedFiles.clear();
    }

    public void exitTransferMode() {
        transferModeActive = false;
        selectedFiles.clear();
    }

    public void toggleSelection(File file) {
        if (!selectedFiles.remove(file)) selectedFiles.add(file);
    }

    public void selectAll(List<File> files) { selectedFiles.addAll(files); }

    public void clearSelection() { selectedFiles.clear(); }

    public Set<File> getSelectedFiles() {
        return Collections.unmodifiableSet(selectedFiles);
    }

    public boolean isSelected(File file) { return selectedFiles.contains(file); }

    public int getSelectedCount() { return selectedFiles.size(); }

    // ── Operações de arquivo ──────────────────────────────────────────

    /**
     * Executa COPY ou MOVE para destino, em SwingWorker.
     * DELETE usa executeDelete().
     */
    public void execute(TransferMode mode, File destination,
                        TransferListener listener) {
        List<File> toProcess = new ArrayList<>(selectedFiles);

        new SwingWorker<int[], Void>() {
            int success = 0, failed = 0;

            @Override
            protected int[] doInBackground() {
                int total = toProcess.size();
                for (int i = 0; i < total; i++) {
                    File src = toProcess.get(i);
                    final int idx = i;
                    SwingUtilities.invokeLater(() ->
                            listener.onProgress(idx + 1, total, src.getName()));
                    try {
                        transferFile(src, destination, mode);
                        success++;
                    } catch (IOException ex) {
                        failed++;
                        System.err.println("Erro em " + src.getName() + ": " + ex.getMessage());
                    }
                }
                return new int[]{success, failed};
            }

            @Override
            protected void done() {
                try {
                    int[] r = get();
                    listener.onCompleted(r[0], r[1]);
                } catch (Exception e) {
                    listener.onError(e.getMessage());
                }
            }
        }.execute();
    }

    /** Executa DELETE, pedindo confirmação antes de chamar. */
    public void executeDelete(TransferListener listener) {
        List<File> toDelete = new ArrayList<>(selectedFiles);

        new SwingWorker<int[], Void>() {
            int success = 0, failed = 0;

            @Override
            protected int[] doInBackground() {
                int total = toDelete.size();
                for (int i = 0; i < total; i++) {
                    File f = toDelete.get(i);
                    final int idx = i;
                    SwingUtilities.invokeLater(() ->
                            listener.onProgress(idx + 1, total, f.getName()));
                    try {
                        deleteRecursively(f);
                        success++;
                    } catch (IOException ex) {
                        failed++;
                    }
                }
                return new int[]{success, failed};
            }

            @Override
            protected void done() {
                try {
                    int[] r = get();
                    listener.onCompleted(r[0], r[1]);
                } catch (Exception e) {
                    listener.onError(e.getMessage());
                }
            }
        }.execute();
    }

    // ── Helpers privados ──────────────────────────────────────────────

    private void transferFile(File src, File destDir, TransferMode mode) throws IOException {
        File dest = resolveDestination(src, destDir);

        if (src.isDirectory()) {
            copyDirectoryRecursively(src, dest);
            if (mode == TransferMode.MOVE) deleteRecursively(src);
        } else {
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (mode == TransferMode.MOVE) Files.delete(src.toPath());
        }
    }

    /** Resolve nome de destino, adicionando sufixo "_cópia" se já existir. */
    private File resolveDestination(File src, File destDir) {
        File dest = new File(destDir, src.getName());
        if (!dest.exists()) return dest;

        String name = src.getName();
        String base, ext = "";
        int dot = name.lastIndexOf('.');
        if (!src.isDirectory() && dot > 0) {
            base = name.substring(0, dot);
            ext  = name.substring(dot);         // inclui o ponto
        } else {
            base = name;
        }

        int n = 1;
        do {
            dest = new File(destDir, base + "_cópia" + (n > 1 ? n : "") + ext);
            n++;
        } while (dest.exists());
        return dest;
    }

    private void copyDirectoryRecursively(File src, File dest) throws IOException {
        dest.mkdirs();
        File[] children = src.listFiles();
        if (children == null) return;
        for (File child : children) {
            transferFile(child, dest, TransferMode.COPY);
        }
    }

    private void deleteRecursively(File f) throws IOException {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursively(c);
        }
        Files.delete(f.toPath());
    }
}
