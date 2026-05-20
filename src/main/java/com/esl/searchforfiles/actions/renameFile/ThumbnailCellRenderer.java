package com.esl.searchforfiles.actions.renameFile;


import com.esl.searchforfiles.model.FileInfo;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ThumbnailCellRenderer extends DefaultTableCellRenderer {

    // ── Ajuste estes dois valores para calibrar tamanho ───────────
    public static int  THUMB_SIZE  = 132;  // largura/altura do quadrado da imagem
    public static int  ROW_HEIGHT  = 140;  // altura da linha na tabela
    // ─────────────────────────────────────────────────────────────

    private static final Set<String> IMG_EXTS =
            Set.of("jpg","jpeg","png","gif","bmp","webp","tif","tiff");
    private static final Set<String> VID_EXTS =
            Set.of("mp4","mov","avi","mkv","wmv","flv","webm","m4v");

    private final ConcurrentHashMap<String, ImageIcon> cache   = new ConcurrentHashMap<>();
    private final Set<String>                          pending =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private JTable ownerTable;

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int column) {

        this.ownerTable = table;
        JLabel lbl = (JLabel) super.getTableCellRendererComponent(
                table, "", isSelected, hasFocus, row, column);
        lbl.setHorizontalAlignment(CENTER);
        lbl.setVerticalAlignment(CENTER);

        if (!(value instanceof FileInfo fi)) return lbl;

        String    path = fi.getPath();
        ImageIcon icon = cache.get(path);

        if (icon != null) {
            lbl.setIcon(icon);
        } else {
            lbl.setIcon(genericIcon(fi.getExtension().toLowerCase()));
            loadAsync(fi, row);
        }
        lbl.setText("");
        return lbl;
    }

    private void loadAsync(FileInfo fi, int row) {
        String path = fi.getPath();
        if (!pending.add(path)) return;
        String ext = fi.getExtension().toLowerCase();

        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() {
                if (!IMG_EXTS.contains(ext)) return genericIcon(ext);
                try (javax.imageio.stream.ImageInputStream iis =
                             ImageIO.createImageInputStream(new File(path))) {
                    if (iis == null) return genericIcon(ext);
                    java.util.Iterator<javax.imageio.ImageReader> readers =
                            ImageIO.getImageReaders(iis);
                    if (!readers.hasNext()) return genericIcon(ext);
                    javax.imageio.ImageReader reader = readers.next();
                    reader.setInput(iis);
                    javax.imageio.ImageReadParam param = reader.getDefaultReadParam();
                    param.setSourceSubsampling(8, 8, 0, 0);
                    BufferedImage raw = reader.read(0, param);
                    reader.dispose();
                    return raw != null ? fitSquare(raw) : genericIcon(ext);
                } catch (Exception e) { return genericIcon(ext); }
            }

            @Override
            protected void done() {
                pending.remove(path);
                try {
                    ImageIcon icon = get();
                    cache.put(path, icon);
                    if (ownerTable != null && row < ownerTable.getRowCount())
                        ownerTable.repaint(ownerTable.getCellRect(row, 0, true));
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private ImageIcon fitSquare(BufferedImage src) {
        int sz = THUMB_SIZE;
        double scale = (double) sz / Math.max(src.getWidth(), src.getHeight());
        int w = Math.max(1, (int)(src.getWidth()  * scale));
        int h = Math.max(1, (int)(src.getHeight() * scale));
        BufferedImage out = new BufferedImage(sz, sz, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(src, (sz - w) / 2, (sz - h) / 2, w, h, null);
        g2.dispose();
        return new ImageIcon(out);
    }

    private ImageIcon genericIcon(String ext) {
        int sz = THUMB_SIZE;
        BufferedImage img = new BufferedImage(sz, sz, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        Color bg = VID_EXTS.contains(ext)
                ? new Color(33, 100, 180, 80)
                : new Color(130, 130, 130, 60);
        g2.setColor(bg);
        g2.fillRoundRect(2, 2, sz - 4, sz - 4, 8, 8);
        g2.setColor(new Color(130, 130, 130));
        g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(8, sz / 5)));
        FontMetrics fm = g2.getFontMetrics();
        String label = ext.isEmpty() ? "?" : ext.toUpperCase();
        if (label.length() > 4) label = label.substring(0, 4);
        g2.drawString(label, (sz - fm.stringWidth(label)) / 2,
                (sz + fm.getAscent()) / 2 - 2);
        g2.dispose();
        return new ImageIcon(img);
    }
}

