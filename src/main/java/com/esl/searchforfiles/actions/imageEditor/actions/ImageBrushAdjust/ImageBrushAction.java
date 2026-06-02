package com.esl.searchforfiles.actions.imageEditor.actions.ImageBrushAdjust;


import com.esl.searchforfiles.actions.imageEditor.ImageEditAction;

import java.awt.image.BufferedImage;

public class ImageBrushAction extends ImageEditAction {

    public enum BrushTarget { BRIGHTNESS, CONTRAST, GAMMA, SATURATION }

    // ── Parâmetros ────────────────────────────────────────────────
    private BrushTarget activeTarget = BrushTarget.BRIGHTNESS;
    private double brightness  = 0.0;   // -1.0 a 1.0  (delta)
    private double contrast    = 1.0;   //  0.0 a 2.0
    private double gamma       = 1.0;   //  0.1 a 3.0
    private double saturation  = 1.0;   //  0.0 a 3.0
    private int    brushSize   = 40;    // px na imagem de referência

    // ── Máscara normalizada ───────────────────────────────────────
    // Dimensões da imagem usada ao pintar (proxy/preview)
    private int    maskRefW = 0, maskRefH = 0;
    private float[][] mask  = null;   // [y][x], valores 0.0–1.0

    public ImageBrushAction() {
        super("Brush de ajuste");
        syncParams();
    }

    // ── Getters / Setters ─────────────────────────────────────────
    public BrushTarget getActiveTarget()           { return activeTarget; }
    public void        setActiveTarget(BrushTarget t) { activeTarget = t; syncParams(); }

    public double getBrightness()                  { return brightness; }
    public void   setBrightness(double v)          { brightness = v;  syncParams(); }

    public double getContrast()                    { return contrast; }
    public void   setContrast(double v)            { contrast = v;    syncParams(); }

    public double getGamma()                       { return gamma; }
    public void   setGamma(double v)               { gamma = v;      syncParams(); }

    public double getSaturation()                  { return saturation; }
    public void   setSaturation(double v)          { saturation = v;  syncParams(); }

    public int  getBrushSize()                     { return brushSize; }
    public void setBrushSize(int v)                { brushSize = Math.max(4, v); }

    public boolean hasMask()  { return mask != null; }
    public void    clearMask(){ mask = null; maskRefW = 0; maskRefH = 0; syncParams(); }


    public boolean hasEffect() { return mask != null; }

    private void syncParams() {
        setParam("brush",      activeTarget.name().toLowerCase());
        setParam("brilho",     String.format("%.2f", brightness));
        setParam("contraste",  String.format("%.2f", contrast));
        setParam("gamma",      String.format("%.2f", gamma));
        setParam("saturação",  String.format("%.2f", saturation));
        setParam("máscara",    mask != null ? "definida" : "vazia");
    }

    // ── Pintura da máscara ────────────────────────────────────────

    /**
     * Chamado pelo ImagePreviewPanel a cada evento de drag.
     * cx, cy  = centro do brush em coordenadas da imagem de referência.
     * refW/H  = dimensões da imagem de referência (proxy).
     */
    public void paintStroke(int cx, int cy, int refW, int refH) {
        // Inicializa ou reseta a máscara se a referência mudou
        if (mask == null || maskRefW != refW || maskRefH != refH) {
            maskRefW = refW;
            maskRefH = refH;
            if (mask == null) {
                mask = new float[refH][refW];
            } else {
                // Redimensiona máscara existente para a nova referência
                mask = resizeMask(mask, maskRefW, maskRefH, refW, refH);
                maskRefW = refW; maskRefH = refH;
            }
        }

        int r = brushSize / 2;
        int x0 = Math.max(0, cx - r), x1 = Math.min(refW - 1, cx + r);
        int y0 = Math.max(0, cy - r), y1 = Math.min(refH - 1, cy + r);
        float r2 = r * r;

        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                float dx = x - cx, dy = y - cy;
                float dist2 = dx*dx + dy*dy;
                if (dist2 > r2) continue;
                // Força de aplicação: 1.0 no centro, 0.0 na borda (suavização)
                float strength = 1.0f - (dist2 / r2);
                // Acumula, limitado a 1.0
                mask[y][x] = Math.min(1.0f, mask[y][x] + strength * 0.08f);
            }
        }
        syncParams();
    }

    // ── Aplicação ─────────────────────────────────────────────────


    public BufferedImage apply(BufferedImage original) {
        if (!isEnabled() || original == null || !hasEffect()) return original;

        int w = original.getWidth(), h = original.getHeight();
        int[] src = original.getRGB(0, 0, w, h, null, 0, w);
        int[] dst = new int[src.length];

        // Redimensiona a máscara para as dimensões da imagem alvo
        float[][] scaledMask = resizeMask(mask, maskRefW, maskRefH, w, h);

        // LUTs por canal para brightness/contrast/gamma (evita recalcular por pixel)
        int[] lutB = buildLut(brightness, 1.0,    1.0);
        int[] lutC = buildLut(0.0,        contrast, 1.0);
        int[] lutG = buildLut(0.0,        1.0,    gamma);

        for (int i = 0; i < src.length; i++) {
            int rgb = src[i];
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >>  8) & 0xFF;
            int b =  rgb        & 0xFF;

            int py = i / w, px = i % w;
            float m = scaledMask[py][px];   // 0.0–1.0

            if (m <= 0f) { dst[i] = rgb; continue; }

            int nr = r, ng = g, nb = b;

            switch (activeTarget) {
                case BRIGHTNESS -> {
                    nr = blend(r, lutB[r], m);
                    ng = blend(g, lutB[g], m);
                    nb = blend(b, lutB[b], m);
                }
                case CONTRAST -> {
                    nr = blend(r, lutC[r], m);
                    ng = blend(g, lutC[g], m);
                    nb = blend(b, lutC[b], m);
                }
                case GAMMA -> {
                    nr = blend(r, lutG[r], m);
                    ng = blend(g, lutG[g], m);
                    nb = blend(b, lutG[b], m);
                }
                case SATURATION -> {
                    float gray = r * 0.299f + g * 0.587f + b * 0.114f;
                    int sr = clamp((int)(gray + saturation * (r - gray)), 0, 255);
                    int sg = clamp((int)(gray + saturation * (g - gray)), 0, 255);
                    int sb = clamp((int)(gray + saturation * (b - gray)), 0, 255);
                    nr = blend(r, sr, m);
                    ng = blend(g, sg, m);
                    nb = blend(b, sb, m);
                }
            }

            dst[i] = (nr << 16) | (ng << 8) | nb;
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, w, h, dst, 0, w);
        return out;
    }

    // ── Utilitários ───────────────────────────────────────────────

    /** Interpola linearmente entre o valor original e o modificado pela máscara. */
    private static int blend(int original, int modified, float m) {
        return clamp(Math.round(original + m * (modified - original)), 0, 255);
    }

    /** Constrói uma LUT de 256 entradas para brightness + contrast + gamma. */
    private static int[] buildLut(double brightness, double contrast, double gamma) {
        int[] lut = new int[256];
        for (int i = 0; i < 256; i++) {
            double v = i;
            if (contrast != 1.0)   v = (v - 128.0) * contrast + 128.0;
            if (brightness != 0.0) v += brightness * 255.0;
            if (gamma != 1.0) {
                v = Math.max(0, Math.min(255, v));
                v = 255.0 * Math.pow(v / 255.0, 1.0 / gamma);
            }
            lut[i] = clamp((int) v, 0, 255);
        }
        return lut;
    }

    /** Redimensionamento bilinear da máscara float[][]. */
    private static float[][] resizeMask(float[][] src, int sw, int sh, int dw, int dh) {
        float[][] dst = new float[dh][dw];
        float sx = (float) sw / dw, sy = (float) sh / dh;
        for (int y = 0; y < dh; y++) {
            float fy = y * sy;
            int y0 = (int) fy, y1 = Math.min(y0 + 1, sh - 1);
            float wy = fy - y0;
            for (int x = 0; x < dw; x++) {
                float fx = x * sx;
                int x0 = (int) fx, x1 = Math.min(x0 + 1, sw - 1);
                float wx = fx - x0;
                dst[y][x] =
                        src[y0][x0] * (1-wx)*(1-wy) +
                                src[y0][x1] *    wx *(1-wy) +
                                src[y1][x0] * (1-wx)*   wy  +
                                src[y1][x1] *    wx *   wy;
            }
        }
        return dst;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}