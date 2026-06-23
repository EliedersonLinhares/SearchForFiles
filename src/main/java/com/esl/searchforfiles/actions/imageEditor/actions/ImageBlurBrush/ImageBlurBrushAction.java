package com.esl.searchforfiles.actions.imageEditor.actions.ImageBlurBrush;


import com.esl.searchforfiles.actions.imageEditor.ImageEditAction;

import java.awt.image.BufferedImage;

public class ImageBlurBrushAction extends ImageEditAction {

    public enum BrushType { SOLID, SOFT }

    // ── Parâmetros ────────────────────────────────────────────────
    private int       blurRadius = 5;    // raio do kernel de desfoque (1–20)
    private int       brushSize  = 40;   // px na imagem de referência
    private BrushType brushType  = BrushType.SOFT;

    // ── Máscara normalizada ───────────────────────────────────────
    private int     maskRefW = 0, maskRefH = 0;
    private float[][] mask   = null;   // [y][x]  0.0–1.0

    public ImageBlurBrushAction() {
        super("Blur Brush");
        syncParams();
    }

    // ── Getters / Setters ─────────────────────────────────────────
    public int       getBlurRadius()           { return blurRadius; }
    public void      setBlurRadius(int v)      { blurRadius = Math.max(1, Math.min(20, v)); syncParams(); }

    public int       getBrushSize()            { return brushSize; }
    public void      setBrushSize(int v)       { brushSize = Math.max(2, v); }

    public BrushType getBrushType()            { return brushType; }
    public void      setBrushType(BrushType t) { brushType = t; syncParams(); }

    public boolean hasMask()  { return mask != null; }
    public void    clearMask(){ mask = null; maskRefW = maskRefH = 0; syncParams(); }

   public boolean hasEffect() { return mask != null; }

    private void syncParams() {
        setParam("raio blur", blurRadius + " px");
        setParam("tipo",      brushType == BrushType.SOFT ? "suave" : "sólido");
        setParam("máscara",   mask != null ? "definida" : "vazia");
    }

    // ── Pintura da máscara ────────────────────────────────────────

    public void paintStroke(int cx, int cy, int refW, int refH) {
        if (mask == null || maskRefW != refW || maskRefH != refH) {
            if (mask != null)
                mask = resizeMask(mask, maskRefW, maskRefH, refW, refH);
            else
                mask = new float[refH][refW];
            maskRefW = refW; maskRefH = refH;
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

                float strength = (brushType == BrushType.SOLID)
                        ? 1.0f
                        : 1.0f - (dist2 / r2) * (dist2 / r2);   // falloff suave

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

        // Redimensiona a máscara para as dimensões da imagem alvo
        float[][] scaledMask = resizeMask(mask, maskRefW, maskRefH, w, h);

        // Gera a versão completamente desfocada com box blur separável
        int[] src    = original.getRGB(0, 0, w, h, null, 0, w);
        int[] blurred = boxBlur(src, w, h, blurRadius);
        int[] dst    = new int[src.length];

        // Interpola pixel a pixel usando a máscara como peso
        for (int i = 0; i < src.length; i++) {
            int py = i / w, px = i % w;
            float m = scaledMask[py][px];

            if (m <= 0f) { dst[i] = src[i]; continue; }

            int or = (src[i] >> 16) & 0xFF;
            int og = (src[i] >>  8) & 0xFF;
            int ob =  src[i]        & 0xFF;

            int br = (blurred[i] >> 16) & 0xFF;
            int bg = (blurred[i] >>  8) & 0xFF;
            int bb =  blurred[i]        & 0xFF;

            // Blend: original → desfocado, proporcional à máscara
            int nr = clamp(Math.round(or + m * (br - or)), 0, 255);
            int ng = clamp(Math.round(og + m * (bg - og)), 0, 255);
            int nb = clamp(Math.round(ob + m * (bb - ob)), 0, 255);

            dst[i] = (nr << 16) | (ng << 8) | nb;
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, w, h, dst, 0, w);
        return out;
    }

    // ── Box blur separável (horizontal + vertical) ────────────────
    // O(w*h*r) — muito mais rápido que um kernel 2D ingênuo O(w*h*r²)

    private static int[] boxBlur(int[] src, int w, int h, int radius) {
        int[] tmp = new int[src.length];
        int[] dst = new int[src.length];

        // Passagem horizontal
        for (int y = 0; y < h; y++) {
            int rSum = 0, gSum = 0, bSum = 0, count = 0;
            // Inicializa janela
            for (int kx = -radius; kx <= radius; kx++) {
                int sx = Math.max(0, Math.min(w - 1, kx));
                int rgb = src[y * w + sx];
                rSum += (rgb >> 16) & 0xFF;
                gSum += (rgb >>  8) & 0xFF;
                bSum +=  rgb        & 0xFF;
                count++;
            }
            for (int x = 0; x < w; x++) {
                tmp[y * w + x] = ((rSum/count) << 16) | ((gSum/count) << 8) | (bSum/count);
                // Desliza a janela
                int removeX = Math.max(0, Math.min(w - 1, x - radius));
                int addX    = Math.max(0, Math.min(w - 1, x + radius + 1));
                int rem = src[y * w + removeX];
                int add = src[y * w + addX];
                rSum += ((add >> 16) & 0xFF) - ((rem >> 16) & 0xFF);
                gSum += ((add >>  8) & 0xFF) - ((rem >>  8) & 0xFF);
                bSum += ( add        & 0xFF) - ( rem        & 0xFF);
            }
        }

        // Passagem vertical
        for (int x = 0; x < w; x++) {
            int rSum = 0, gSum = 0, bSum = 0, count = 0;
            for (int ky = -radius; ky <= radius; ky++) {
                int sy = Math.max(0, Math.min(h - 1, ky));
                int rgb = tmp[sy * w + x];
                rSum += (rgb >> 16) & 0xFF;
                gSum += (rgb >>  8) & 0xFF;
                bSum +=  rgb        & 0xFF;
                count++;
            }
            for (int y = 0; y < h; y++) {
                dst[y * w + x] = ((rSum/count) << 16) | ((gSum/count) << 8) | (bSum/count);
                int removeY = Math.max(0, Math.min(h - 1, y - radius));
                int addY    = Math.max(0, Math.min(h - 1, y + radius + 1));
                int rem = tmp[removeY * w + x];
                int add = tmp[addY    * w + x];
                rSum += ((add >> 16) & 0xFF) - ((rem >> 16) & 0xFF);
                gSum += ((add >>  8) & 0xFF) - ((rem >>  8) & 0xFF);
                bSum += ( add        & 0xFF) - ( rem        & 0xFF);
            }
        }

        return dst;
    }

    // ── Redimensionamento bilinear da máscara ─────────────────────

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
                        src[y0][x0]*(1-wx)*(1-wy) + src[y0][x1]*wx*(1-wy) +
                                src[y1][x0]*(1-wx)*   wy  + src[y1][x1]*wx*   wy;
            }
        }
        return dst;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
