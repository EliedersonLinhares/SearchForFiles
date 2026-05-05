package com.esl.searchforfiles.ui;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Iterator;

public class AnimatedGifThumb {
    // ── 1. Flag estática (futura opção ligar/desligar) ───────────────────
    private final FileItemPanel fileItemPanel;
    private static boolean showAnimatedGif = true;

    public static boolean isShowAnimatedGif() { return showAnimatedGif; }
    public static void setShowAnimatedGif(boolean v) { showAnimatedGif = v; }


    public AnimatedGifThumb(FileItemPanel fileItemPanel) {
        this.fileItemPanel = fileItemPanel;
    }


    // ── 2. Utilitário: detecta se é GIF animado ──────────────────────────
   public static boolean isAnimatedGif(File file) {
        if (!FileItemPanel.getExtension(file).equals("gif")) return false;
        try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
            if (in == null) return false;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            reader.setInput(in);
            boolean animated = reader.getNumImages(true) > 1;
            reader.dispose();
            return animated;
        } catch (Exception e) {
            return false;
        }
    }


// ── 3. Loader de GIF animado ─────────────────────────────────────────
    /**
     * Carrega o GIF preservando a animação e escala-o para boxSize,
     * mantendo proporção. O Swing anima automaticamente ImageIcon de GIF.
     * Usa createGifLabel() para garantir limpeza de fundo a cada frame.
     */
    void loadAnimatedGif(File file, int boxSize, JLabel target) {
        String key = "gif_" + file.getAbsolutePath() + "_" + boxSize;

        ImageIcon cached = FileItemPanel.ICON_CACHE.get(key);
        if (cached != null) {
            target.setIcon(cached);
            return;
        }

        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() {
                try {
                    // Lê os bytes brutos — preserva todos os frames e metadados
                    byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
                    ImageIcon raw = new ImageIcon(data);

                    int origW = raw.getIconWidth();
                    int origH = raw.getIconHeight();
                    if (origW <= 0 || origH <= 0) return null;

                    double scale = Math.min((double) boxSize / origW,
                            (double) boxSize / origH);
                    int dstW = Math.max(1, (int) (origW * scale));
                    int dstH = Math.max(1, (int) (origH * scale));

                    // SCALE_DEFAULT preserva a animação (SCALE_SMOOTH achata para 1 frame)
                    Image scaled = raw.getImage()
                            .getScaledInstance(dstW, dstH, Image.SCALE_DEFAULT);

                    // ImageIcon direto — SEM canvas intermediário
                    // O canvas causava o fantasma: frame-0 fixo + GIF animando por baixo
                    ImageIcon icon = new ImageIcon(scaled);
                    FileItemPanel.ICON_CACHE.put(key, icon);
                    return icon;

                } catch (Exception e) {
                    System.err.println("⚠️ GIF load error: " + file.getName()
                            + " — " + e.getMessage());
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    ImageIcon ic = get();
                    SwingUtilities.invokeLater(() -> {
                        if (ic != null) {
                            target.setIcon(ic);
                        } else {
                            fileItemPanel.loadThumbnailFit(file, boxSize, target);
                        }
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> fileItemPanel.loadThumbnailFit(file, boxSize, target));
                }
            }
        }.execute();
    }

// ── 4. Cria um JLabel que limpa o fundo a cada frame do GIF ──────────
    /**
     * JLabel com paintComponent sobrescrito para limpar o fundo
     * antes de cada frame do GIF animado, evitando o artefato de
     * imagens de outros componentes "vazando" entre os frames.
     */

    JLabel createGifLabel(int boxSize) {
        JLabel label = new JLabel();
        label.setOpaque(true); // opaco — pinta o fundo antes de cada frame
        label.setBackground(fileItemPanel.getNormalColor());
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setPreferredSize(new Dimension(boxSize, boxSize));
        return label;
    }


    /**
     * JLayeredPane opaco que limpa o fundo a cada repaint.
     * Necessário porque o RepaintManager propaga o ciclo de frames
     * do GIF para cima na hierarquia — sem isso componentes vizinhos
     * "vazam" entre os frames mesmo com o JLabel opaco.
     */
    JLayeredPane createGifLayeredPane(int boxSize) {
        JLayeredPane pane = new JLayeredPane() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
                super.paintComponent(g);
            }
        };
        pane.setOpaque(true);
        pane.setBackground(fileItemPanel.getNormalColor());
        pane.setPreferredSize(new Dimension(boxSize, boxSize));
        pane.setMaximumSize(new Dimension(boxSize, boxSize));
        pane.setAlignmentX(Component.CENTER_ALIGNMENT);
        return pane;
    }
}
