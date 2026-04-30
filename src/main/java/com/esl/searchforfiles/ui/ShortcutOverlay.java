package com.esl.searchforfiles.ui;


import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

// ShortcutOverlay.java
// Badge "seta de atalho" no canto inferior ESQUERDO do thumbnail.
// Ocupa o mesmo canto que o RatingOverlay? Ajuste o canto aqui
// ou mova o RatingOverlay para outro canto conforme seu layout.
// ═══════════════════════════════════════════════════════════════
public class ShortcutOverlay extends JComponent {

    private static final String ICON_PATH = "/icons/overlay/arrow.png";
    private static BufferedImage cachedImage;   // compartilhado entre instâncias
    private BufferedImage icon;

    private static final Color BG_COLOR = new Color(0, 0, 0, 90);

    public ShortcutOverlay() {
        setOpaque(false);
        loadImage();
    }

    private void loadImage() {
        if (cachedImage != null) { icon = cachedImage; return; }

        new SwingWorker<BufferedImage, Void>() {
            @Override protected BufferedImage doInBackground() {
                try (InputStream is = ShortcutOverlay.class.getResourceAsStream(ICON_PATH)) {
                    return (is != null) ? ImageIO.read(is) : null;
                } catch (IOException e) {
                    System.err.println("⚠️ ShortcutOverlay: " + e.getMessage());
                    return null;
                }
            }
            @Override protected void done() {
                try {
                    BufferedImage img = get();
                    if (img != null) { cachedImage = img; icon = img; repaint(); }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (icon == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int iconSize = Math.min(40, Math.max(14, (int)(getWidth() * 0.22)));
        int pad      = 4;

        // Canto superior DIREITO
        int bgX = getWidth() - iconSize - pad * 2 - 2;
        int bgY = 2;
        int bgW = iconSize + pad * 2;
        int bgH = iconSize + pad * 2;

        g2.setColor(BG_COLOR);
        g2.fillRoundRect(bgX, bgY, bgW, bgH, 6, 6);
        g2.drawImage(icon, bgX + pad, bgY + pad, iconSize, iconSize, null);
        g2.dispose();
    }
}
