package com.esl.searchforfiles.actions.imageEditor;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class ImageSaveManager {

    private final Component parent;
    private File lastDirectory = null;


    public ImageSaveManager(Component parent) {
        this.parent = parent;
    }

    // ── API pública ────────────────────────────────────────────────

    /**
     * "Salvar": sobrescreve cada arquivo original com as ações aplicadas.
     * Pula arquivos com formato não suportado e informa ao final.
     *
     * @param images   imagens originais (mesma ordem de imageFiles)
     * @param files    arquivos originais de destino
     * @param actions  função que aplica as ações sobre cada imagem
     * @param onDone   callback chamado na EDT ao término (sucesso ou não)
     */
    public void saveAll(List<BufferedImage> images,
                        List<File> files,
                        UnaryOperator<BufferedImage> actions,
                        Consumer<List<File>> onDone) {

        List<String> unsupported = new ArrayList<>();
        for (File f : files) {
            String ext = extensionOf(f.getName());
            if (!ext.equals("jpg") && !ext.equals("jpeg") && !ext.equals("png"))
                unsupported.add(f.getName());
        }

        if (!unsupported.isEmpty()) {
            int proceed = JOptionPane.showConfirmDialog(parent,
                    "<html>Os arquivos abaixo têm formato não suportado e serão <b>ignorados</b>:<br><br>"
                            + String.join("<br>", unsupported)
                            + "<br><br>Continuar com os demais?</html>",
                    "Formatos não suportados", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (proceed != JOptionPane.YES_OPTION) return;
        }

        int confirm = JOptionPane.showConfirmDialog(parent,
                "<html>Sobrescrever <b>" + files.size() + "</b> arquivo(s) original(is) com as alterações?</html>",
                "Confirmar salvamento", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        // Monta lista de targets: índice → (file, format)
        List<SaveTarget> targets = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            File f   = files.get(i);
            String e = extensionOf(f.getName());
            if (unsupported.contains(f.getName())) continue;
            targets.add(new SaveTarget(i, f, e.equals("png") ? "png" : "jpg"));
        }

        writeBatch(images, targets, actions, onDone);
    }


    /**
     * "Salvar como": usuário escolhe pasta e formato via JnaFileChooser,
     * depois salva todas as imagens lá.
     *
     * @param images       imagens originais
     * @param files        arquivos originais (usados para montar nomes de saída)
     * @param originalDir  pasta dos originais (para aviso especial de sobrescrita)
     * @param actions      função que aplica as ações sobre cada imagem
     * @param onDone       callback chamado na EDT ao término
     */
    /**
     * "Salvar como": usuário escolhe pasta e formato via JnaFileChooser,
     * depois salva todas as imagens lá.
     *
     * @param images       imagens originais
     * @param files        arquivos originais (usados para montar nomes de saída)
     * @param originalDir  pasta dos originais (para aviso especial de sobrescrita)
     * @param actions      função que aplica as ações sobre cada imagem
     * @param onDone       callback chamado na EDT ao término
     */
    public void saveAllAs(List<BufferedImage> images,
                          List<File> files,
                          File originalDir,
                          UnaryOperator<BufferedImage> actions,
                          Consumer<List<File>> onDone) {

        // ── Escolha de formato ─────────────────────────────────────
        String[] fmtOptions = {"JPEG", "PNG"};
        int fmtChoice = JOptionPane.showOptionDialog(parent,
                "Escolha o formato de saída para todas as imagens:",
                "Salvar como",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, fmtOptions, fmtOptions[0]);
        if (fmtChoice < 0) return;

        String fmt = fmtChoice == 0 ? "jpg" : "png";
        String ext = fmt;

        // ── Escolha de pasta via JFileChooser (DIRECTORIES_ONLY) ──
        JFileChooser chooser = new JFileChooser(
                lastDirectory != null ? lastDirectory : originalDir);
        chooser.setDialogTitle("Escolha a pasta de destino");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;

        File chosen = chooser.getSelectedFile();
        if (chosen == null) return;
        File destDir = chosen;   // chosen já É o diretório selecionado
        lastDirectory = destDir;

        // ── Monta destinos e detecta colisões ──────────────────────
        List<SaveTarget> targets = new ArrayList<>();
        List<String> willOverwrite = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            File dest = new File(destDir, baseName(files.get(i).getName()) + "." + ext);
            targets.add(new SaveTarget(i, dest, fmt));
            if (dest.exists()) willOverwrite.add(dest.getName());
        }

        if (!willOverwrite.isEmpty()) {
            String hint = canonicalQuietly(destDir).equals(canonicalQuietly(originalDir))
                    ? "A pasta escolhida é a mesma dos originais."
                    : "A pasta de destino já contém arquivos com o mesmo nome.";
            int confirm = JOptionPane.showConfirmDialog(parent,
                    "<html>" + hint + " Os seguintes arquivos serão sobrescritos:<br><br>"
                            + String.join("<br>", willOverwrite)
                            + "<br><br>Deseja continuar?</html>",
                    "Confirmar sobrescrita", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
        }

        writeBatch(images, targets, actions, onDone);
    }

    // ── Núcleo de escrita em lote ──────────────────────────────────

    private record SaveTarget(int imageIndex, File file, String format) {}

    private void writeBatch(List<BufferedImage> images,
                            List<SaveTarget> targets,
                            UnaryOperator<BufferedImage> actions,
                            Consumer<List<File>> onDone) {

        int total = targets.size();

        // Diálogo de progresso
        JDialog progress = new JDialog((Window) SwingUtilities.getWindowAncestor(parent),
                "Salvando…", Dialog.ModalityType.MODELESS);
        progress.setSize(360, 110);
        progress.setLocationRelativeTo(parent);
        progress.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        progress.setResizable(false);

        JLabel statusLabel = new JLabel("Iniciando…", SwingConstants.CENTER);
        JProgressBar bar   = new JProgressBar(0, total);
        bar.setStringPainted(true);

        JPanel pp = new JPanel(new BorderLayout(8, 8));
        pp.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        pp.add(statusLabel, BorderLayout.NORTH);
        pp.add(bar,         BorderLayout.CENTER);
        progress.add(pp);
        progress.setVisible(true);

        new SwingWorker<List<String>, Integer>() {

            // Lista acumulada dos arquivos salvos com sucesso
            final List<File> savedFiles = new ArrayList<>();

            @Override
            protected List<String> doInBackground() {
                List<String> errors = new ArrayList<>();
                for (int i = 0; i < total; i++) {
                    SaveTarget t = targets.get(i);
                    try {
                        BufferedImage result = actions.apply(images.get(t.imageIndex()));
                        result = prepareForFormat(result, t.format());
                        if (!ImageIO.write(result, t.format(), t.file()))
                            throw new IOException("Sem writer para: " + t.format());
                        savedFiles.add(t.file()); // ← registra somente os bem-sucedidos
                    } catch (Exception ex) {
                        errors.add(t.file().getName() + ": " + ex.getMessage());
                    }
                    publish(i);
                }
                return errors;
            }

            @Override
            protected void process(List<Integer> chunks) {
                int last = chunks.get(chunks.size() - 1);
                bar.setValue(last + 1);
                statusLabel.setText("Salvando " + (last + 1) + " de " + total + "…");
            }

            @Override
            protected void done() {
                progress.dispose();
                try {
                    List<String> errs = get();
                    if (errs.isEmpty()) {
                        showInfo(total + " imagem(s) salva(s) com sucesso.");
                    } else {
                        showError("<html>Concluído com " + errs.size() + " erro(s):<br><br>"
                                + String.join("<br>", errs) + "</html>");
                    }
                } catch (Exception ex) {
                    showError("Erro inesperado:\n" + ex.getMessage());
                }
                // Passa a lista real de arquivos salvos para o callback
                if (onDone != null)
                    SwingUtilities.invokeLater(() -> onDone.accept(savedFiles));
            }
        }.execute();
    }

    // ── Helpers ────────────────────────────────────────────────────

    private BufferedImage prepareForFormat(BufferedImage img, String format) {
        if (!format.equals("jpg") && !format.equals("jpeg")) return img;
        if (img.getType() == BufferedImage.TYPE_INT_RGB)     return img;
        BufferedImage rgb = new BufferedImage(
                img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = rgb.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g2.drawImage(img, 0, 0, null);
        g2.dispose();
        return rgb;
    }

    private String resolveFormat(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "jpg";
        if (name.endsWith(".png"))                           return "png";
        return "jpg"; // padrão
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    private static String baseName(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(0, dot) : filename;
    }

    private static String canonicalQuietly(File f) {
        try { return f.getCanonicalPath(); } catch (Exception e) { return f.getAbsolutePath(); }
    }

    private void showInfo(String message) {
        JOptionPane.showMessageDialog(parent, message, "Salvo", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(parent, message, "Erro ao salvar", JOptionPane.ERROR_MESSAGE);
    }
}