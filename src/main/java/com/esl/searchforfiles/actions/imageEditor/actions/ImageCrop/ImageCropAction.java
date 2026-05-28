package com.esl.searchforfiles.actions.imageEditor.actions.ImageCrop;


import com.esl.searchforfiles.actions.imageEditor.ImageEditAction;

import java.awt.*;
import java.awt.image.BufferedImage;

public class ImageCropAction extends ImageEditAction {

    public enum AspectRatioPreset {
        FREE      ("Livre",        0,  0),
        SQUARE    ("1:1 Quadrado", 1,  1),
        RATIO_4_3 ("4:3",          4,  3),
        RATIO_16_9("16:9",        16,  9),
        RATIO_3_2 ("3:2",          3,  2),
        RATIO_2_3 ("2:3 Retrato",  2,  3),
        RATIO_9_16("9:16 Retrato", 9, 16);

        public final String label;
        public final double ratioW, ratioH;

        AspectRatioPreset(String label, double w, double h) {
            this.label = label; this.ratioW = w; this.ratioH = h;
        }

        /** Proporção W/H, ou 0 se livre. */
        public double ratio() {
            return (ratioW == 0) ? 0 : ratioW / ratioH;
        }

        @Override public String toString() { return label; }
    }

    // Região normalizada (0.0–1.0). Null = sem seleção.
    private double normX = 0, normY = 0, normW = 1, normH = 1;
    private boolean regionSet = false;

    private AspectRatioPreset aspectPreset = AspectRatioPreset.FREE;

    public ImageCropAction() {
        super("Crop");
    }

    // ── Proporção ─────────────────────────────────────────────────

    public AspectRatioPreset getAspectPreset() { return aspectPreset; }
    public void setAspectPreset(AspectRatioPreset p) {
        aspectPreset = p;
        syncParams();
    }

    // ── Região normalizada ────────────────────────────────────────

    public void setCropRegionNormalized(double x, double y, double w, double h) {
        normX = clamp01(x);
        normY = clamp01(y);
        normW = clamp01(w);
        normH = clamp01(h);
        regionSet = (normW > 0 && normH > 0);
        syncParams();
    }

    /**
     * Converte coordenadas em pixels (relativas à imagem de referência)
     * para valores normalizados e armazena.
     */
    public void setCropRegionPixels(int x, int y, int w, int h,
                                    int refWidth, int refHeight) {
        setCropRegionNormalized(
                (double) x / refWidth,
                (double) y / refHeight,
                (double) w / refWidth,
                (double) h / refHeight);
    }

    public boolean hasRegion() { return regionSet; }

    /** Dimensão normalizada — útil para o painel reconstruir o rect visual. */
    public double[] getNormalizedRegion() {
        return new double[]{ normX, normY, normW, normH };
    }

    public void clearRegion() {
        regionSet = false;
        syncParams();
    }

    // ── hasEffect / syncParams ────────────────────────────────────


    public boolean hasEffect() { return regionSet; }

    private void syncParams() {
        setParam("proporção", aspectPreset.label);
        if (regionSet) {
            setParam("região", String.format("%.0f%%,%.0f%% — %.0f%%×%.0f%%",
                    normX * 100, normY * 100, normW * 100, normH * 100));
        } else {
            setParam("região", "não definida");
        }
    }

    // ── Aplicação ─────────────────────────────────────────────────


    public BufferedImage apply(BufferedImage original) {
        if (!isEnabled() || original == null || !hasEffect()) return original;

        int imgW = original.getWidth();
        int imgH = original.getHeight();

        int x = (int) Math.round(normX * imgW);
        int y = (int) Math.round(normY * imgH);
        int w = (int) Math.round(normW * imgW);
        int h = (int) Math.round(normH * imgH);

        // Garante limites
        x = Math.max(0, Math.min(x, imgW - 1));
        y = Math.max(0, Math.min(y, imgH - 1));
        w = Math.max(1, Math.min(w, imgW - x));
        h = Math.max(1, Math.min(h, imgH - y));

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(original.getSubimage(x, y, w, h), 0, 0, null);
        g.dispose();
        return out;
    }

    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }
}
