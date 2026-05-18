package com.esl.searchforfiles.actions.fileTransfer;


import com.esl.searchforfiles.ui.FileExplorerSwing;
import com.esl.searchforfiles.ui.SearchController;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

public class TransferDropHelper {

    /**
     * @param files           Lista de arquivos do Transferable (snapshots dos paths)
     * @param destination     Pasta de destino
     * @param transferService TransferService ativo ou null
     * @param onDone          Callback pós-operação (EDT)
     */
    public static void showDropMenu(Component owner,
                                    List<File> files,
                                    File destination,
                                    TransferService transferService,
                                    Runnable onDone) {

        if (files == null || files.isEmpty() || destination == null) return;

        // BUG 1 FIX — deduplica por caminho absoluto antes de qualquer coisa
        List<File> deduped = files.stream()
                .filter(f -> f != null && f.exists())
                .collect(java.util.stream.Collectors.toMap(
                        File::getAbsolutePath,
                        f -> f,
                        (a, b) -> a,                   // mantém o primeiro em caso de duplicata
                        java.util.LinkedHashMap::new))
                .values().stream().toList();

        if (deduped.isEmpty()) return;

        // BUG 3 FIX — descarta drop se TODOS os arquivos já estão na pasta destino
        boolean allSameParent = deduped.stream().allMatch(f -> {
            File parent = f.getParentFile();
            return parent != null && parent.getAbsolutePath()
                    .equalsIgnoreCase(destination.getAbsolutePath());
        });
        if (allSameParent) return; // origem == destino, não faz nada

        // Remove arquivos cuja pasta pai já é o destino (drop parcial no mesmo dir)
        List<File> filtered = deduped.stream()
                .filter(f -> {
                    File parent = f.getParentFile();
                    return parent == null || !parent.getAbsolutePath()
                            .equalsIgnoreCase(destination.getAbsolutePath());
                }).toList();

        if (filtered.isEmpty()) return;

        JPopupMenu menu = new JPopupMenu();

        JLabel header = new JLabel("  " + filtered.size() + " item(s) → " + destination.getName());
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        header.setForeground(new Color(33, 150, 243));
        header.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        menu.add(header);
        menu.addSeparator();

        JMenuItem moveItem = new JMenuItem("✂️  Mover para cá");
        moveItem.addActionListener(e ->
                runTransfer(TransferMode.MOVE, filtered, destination, transferService, owner, onDone));
        menu.add(moveItem);

        JMenuItem copyItem = new JMenuItem("📋  Copiar para cá");
        copyItem.addActionListener(e ->
                runTransfer(TransferMode.COPY, filtered, destination, transferService, owner, onDone));
        menu.add(copyItem);

        menu.addSeparator();

        JMenuItem cancelItem = new JMenuItem("✕  Cancelar");
        cancelItem.setForeground(Color.GRAY);
        cancelItem.addActionListener(e -> menu.setVisible(false));
        menu.add(cancelItem);

        menu.show(owner, owner.getWidth() / 2, owner.getHeight() / 2);
    }

    // ── Execução ──────────────────────────────────────────────────────

    private static void runTransfer(TransferMode mode,
                                    List<File> files,
                                    File destination,
                                    TransferService transferService,
                                    Component owner,
                                    Runnable onDone) {

        Window win = SwingUtilities.getWindowAncestor(owner);
        TransferProgressDialog progress = new TransferProgressDialog(win, mode, files.size());
        SwingUtilities.invokeLater(() -> progress.setVisible(true));

        // Obtém referência ao SearchController para suspender o auto-refresh
        SearchController controller = resolveController(owner);
        if (controller != null) controller.setTransferInProgress(true);

        // Sempre usa um TransferService temporário alimentado pela lista verificada.
        // Isso evita que o Set<File> original (com paths pré-MOVE) gere duplicatas.
        TransferService tm = new TransferService();
        tm.enterTransferMode();
        files.forEach(tm::toggleSelection);

        tm.execute(mode, destination, new TransferService.TransferListener() {
            @Override public void onProgress(int done, int t, String file) {
                progress.update(done, t, file);
            }
            @Override public void onCompleted(int success, int failed) {
                progress.dispose();

                // Reativa o auto-refresh ANTES de fazer o refresh final
                if (controller != null) controller.setTransferInProgress(false);

                // Limpa seleção do TM original se existir
                if (transferService != null) transferService.clearSelection();

                String msg = "✅ " + success + " item(s) " +
                        (mode == TransferMode.MOVE ? "movido(s)" : "copiado(s)") +
                        (failed > 0 ? "\n⚠️ " + failed + " com erro" : "");
                JOptionPane.showMessageDialog(owner, msg);
                if (onDone != null) SwingUtilities.invokeLater(onDone);
            }
            @Override public void onError(String message) {
                progress.dispose();
                JOptionPane.showMessageDialog(owner,
                        "Erro: " + message, "Erro", JOptionPane.ERROR_MESSAGE);
            }
        });
    }


    /**
     * Sobe a hierarquia de componentes até encontrar o FileExplorerSwing
     * e retorna seu SearchController.
     */
    private static SearchController resolveController(Component c) {
        Window w = SwingUtilities.getWindowAncestor(c);
        if (w instanceof FileExplorerSwing explorer) {
            return explorer.getController();
        }
        return null;
    }
}