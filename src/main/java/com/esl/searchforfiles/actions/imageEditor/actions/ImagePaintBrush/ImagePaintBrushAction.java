package com.esl.searchforfiles.actions.imageEditor.actions.ImagePaintBrush;


import com.esl.searchforfiles.actions.imageEditor.ImageEditAction;

import java.awt.*;
import java.awt.image.BufferedImage;

public class ImagePaintBrushAction extends ImageEditAction {

    public enum BrushType { SOLID, SOFT }

    // ── Parâmetros ────────────────────────────────────────────────
    private Color     brushColor  = Color.RED;
    private int       brushSize   = 40;       // px na imagem de referência
    private float     opacity     = 1.0f;     // 0.0–1.0 (força por stroke)
    private BrushType brushType   = BrushType.SOFT;

    // ── Máscara RGBA normalizada ──────────────────────────────────
    // Cada pixel armazena a cor acumulada (R,G,B) e a cobertura (A).
    // Dimensões relativas à imagem de referência usada ao pintar.
    private int maskRefW = 0, maskRefH = 0;
    private float[][] maskR, maskG, maskB, maskA;   // 0.0–1.0

    public ImagePaintBrushAction() {
        super("Paint Brush");
        syncParams();
    }

    // ── Getters / Setters ─────────────────────────────────────────
    public Color     getBrushColor()           { return brushColor; }
    public void      setBrushColor(Color c)    { brushColor = c;         syncParams(); }

    public int       getBrushSize()            { return brushSize; }
    public void      setBrushSize(int v)       { brushSize = Math.max(2, v); }

    public float     getOpacity()              { return opacity; }
    public void      setOpacity(float v)       { opacity = Math.max(0f, Math.min(1f, v)); syncParams(); }

    public BrushType getBrushType()            { return brushType; }
    public void      setBrushType(BrushType t) { brushType = t;          syncParams(); }

    public boolean hasMask()  { return maskA != null; }

    public void clearMask() {
        maskR = maskG = maskB = maskA = null;
        maskRefW = maskRefH = 0;
        syncParams();
    }

     public boolean hasEffect() { return hasMask(); }

    private void syncParams() {
        setParam("cor",      String.format("#%02X%02X%02X",
                brushColor.getRed(), brushColor.getGreen(), brushColor.getBlue()));
        setParam("opacidade", String.format("%.0f%%", opacity * 100));
        setParam("tipo",      brushType == BrushType.SOFT ? "suave" : "sólido");
        setParam("máscara",   hasMask() ? "definida" : "vazia");
    }

    // ── Pintura da máscara ────────────────────────────────────────

    /**
     * Chamado pelo ImagePreviewPanel a cada evento de drag.
     * Acumula a cor na máscara usando o modo "pintar por cima" com alpha compositing.
     */
    public void paintStroke(int cx, int cy, int refW, int refH) {
        ensureMask(refW, refH);

        float br = brushColor.getRed()   / 255f;
        float bg = brushColor.getGreen() / 255f;
        float bb = brushColor.getBlue()  / 255f;

        int r = brushSize / 2;
        int x0 = Math.max(0, cx - r), x1 = Math.min(refW - 1, cx + r);
        int y0 = Math.max(0, cy - r), y1 = Math.min(refH - 1, cy + r);
        float r2 = r * r;

        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                float dx = x - cx, dy = y - cy;
                float dist2 = dx*dx + dy*dy;
                if (dist2 > r2) continue;

                // Força do stroke: sólido = constante, suave = gradiente radial
                float strokeAlpha;
                if (brushType == BrushType.SOLID) {
                    strokeAlpha = opacity;
                } else {
                    float t = dist2 / r2;                    // 0 (centro) → 1 (borda)
                    float falloff = 1f - (t * t);            // curva suave tipo Photoshop
                    strokeAlpha = opacity * falloff;
                }

                // Alpha compositing: "pintar por cima" acumulado
                // src over dst: outA = srcA + dstA*(1-srcA)
                float existingA = maskA[y][x];
                float newA      = strokeAlpha + existingA * (1f - strokeAlpha);

                if (newA > 0f) {
                    // Blenda a cor proporcionalmente à cobertura
                    maskR[y][x] = (br * strokeAlpha + maskR[y][x] * existingA * (1f - strokeAlpha)) / newA;
                    maskG[y][x] = (bg * strokeAlpha + maskG[y][x] * existingA * (1f - strokeAlpha)) / newA;
                    maskB[y][x] = (bb * strokeAlpha + maskB[y][x] * existingA * (1f - strokeAlpha)) / newA;
                    maskA[y][x] = Math.min(1f, newA);
                }
            }
        }
    }

    private void ensureMask(int refW, int refH) {
        if (maskA == null || maskRefW != refW || maskRefH != refH) {
            if (maskA != null) {
                // Redimensiona máscaras existentes
                maskR = resizeMask(maskR, maskRefW, maskRefH, refW, refH);
                maskG = resizeMask(maskG, maskRefW, maskRefH, refW, refH);
                maskB = resizeMask(maskB, maskRefW, maskRefH, refW, refH);
                maskA = resizeMask(maskA, maskRefW, maskRefH, refW, refH);
            } else {
                maskR = new float[refH][refW];
                maskG = new float[refH][refW];
                maskB = new float[refH][refW];
                maskA = new float[refH][refW];
            }
            maskRefW = refW;
            maskRefH = refH;
        }
    }

    // ── Aplicação ─────────────────────────────────────────────────


    public BufferedImage apply(BufferedImage original) {
        if (!isEnabled() || original == null || !hasEffect()) return original;

        int w = original.getWidth(), h = original.getHeight();
        int[] src = original.getRGB(0, 0, w, h, null, 0, w);
        int[] dst = new int[src.length];

        // Redimensiona as 4 máscaras para as dimensões da imagem alvo
        float[][] sR = resizeMask(maskR, maskRefW, maskRefH, w, h);
        float[][] sG = resizeMask(maskG, maskRefW, maskRefH, w, h);
        float[][] sB = resizeMask(maskB, maskRefW, maskRefH, w, h);
        float[][] sA = resizeMask(maskA, maskRefW, maskRefH, w, h);

        for (int i = 0; i < src.length; i++) {
            int rgb = src[i];
            int py  = i / w, px = i % w;
            float a = sA[py][px];

            if (a <= 0f) { dst[i] = rgb; continue; }

            int or = (rgb >> 16) & 0xFF;
            int og = (rgb >>  8) & 0xFF;
            int ob =  rgb        & 0xFF;

            // Compositing: cor pintada sobre a original
            int nr = clamp(Math.round(sR[py][px] * 255f * a + or * (1f - a)), 0, 255);
            int ng = clamp(Math.round(sG[py][px] * 255f * a + og * (1f - a)), 0, 255);
            int nb = clamp(Math.round(sB[py][px] * 255f * a + ob * (1f - a)), 0, 255);

            dst[i] = (nr << 16) | (ng << 8) | nb;
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, w, h, dst, 0, w);
        return out;
    }

    // ── Redimensionamento bilinear ────────────────────────────────

    private static float[][] resizeMask(float[][] src, int sw, int sh, int dw, int dh) {
        float[][] dst = new float[dh][dw];
        float scaleX = (float) sw / dw, scaleY = (float) sh / dh;
        for (int y = 0; y < dh; y++) {
            float fy = y * scaleY;
            int y0 = (int) fy, y1 = Math.min(y0 + 1, sh - 1);
            float wy = fy - y0;
            for (int x = 0; x < dw; x++) {
                float fx = x * scaleX;
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