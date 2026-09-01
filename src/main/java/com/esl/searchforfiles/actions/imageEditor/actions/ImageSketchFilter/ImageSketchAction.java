package com.esl.searchforfiles.actions.imageEditor.actions.ImageSketchFilter;

import com.esl.searchforfiles.actions.imageEditor.ImageEditAction;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;


import java.awt.*;

public class ImageSketchAction extends ImageEditAction {

    // ── Parâmetros ────────────────────────────────────────────────
    private int     kernelSize       = 5;
    private int     dilateIterations = 1;
    private boolean effectApplied    = false;

    public ImageSketchAction() {
        super("Filtro de desenho");
        syncParams();
    }

    // ── Getters / Setters ─────────────────────────────────────────
    public int  getKernelSize()             { return kernelSize; }
    public void setKernelSize(int v)        { kernelSize = Math.max(1, v | 1); syncParams(); }

    public int  getDilateIterations()       { return dilateIterations; }
    public void setDilateIterations(int v)  { dilateIterations = Math.max(1, v); syncParams(); }

    public boolean isEffectApplied()        { return effectApplied; }
    public void setEffectApplied(boolean v) { effectApplied = v; syncParams(); }

    public boolean hasEffect()              { return effectApplied; }

    private void syncParams() {
        setParam("kernel",    kernelSize + "×" + kernelSize);
        setParam("iterações", String.valueOf(dilateIterations));
        setParam("efeito",    effectApplied ? "ativo" : "inativo");
    }

    // ══════════════════════════════════════════════════════════════
    //  Pipeline principal
    // ══════════════════════════════════════════════════════════════
    public BufferedImage apply(BufferedImage img) {
        if (!isEnabled() || img == null || !hasEffect()) return img;

        // 1. BGR → Grayscale (luminância ponderada)
        byte[] gray = toGrayscale(img);

        // 2. Dilatar (morfologia: máximo na janela do kernel)
        byte[] dilated = gray;
        for (int i = 0; i < dilateIterations; i++)
            dilated = dilate(dilated, img.getWidth(), img.getHeight(), kernelSize);

        // 3. absdiff: |dilated − gray|
        byte[] diff = absDiff(dilated, gray);

        // 4. bitwise_not: inverte cada byte
        byte[] inverted = bitwiseNot(diff);

        // 5. Reconstruir BufferedImage grayscale → BGR (3 canais)
        return grayBytesToBGR(inverted, img.getWidth(), img.getHeight());
    }

    // ══════════════════════════════════════════════════════════════
    //  Operações (substituem o JavaCV / OpenCV)
    // ══════════════════════════════════════════════════════════════

    /**
     * cvtColor(BGR → GRAY)
     * Fórmula ITU-R BT.601 (igual ao OpenCV):
     *   Y = 0.114·B + 0.587·G + 0.299·R
     */
    private static byte[] toGrayscale(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        byte[] gray = new byte[w * h];

        // Normalizar para TYPE_3BYTE_BGR para leitura direta
        BufferedImage bgr = ensureBGR(src);
        byte[] pixels = ((DataBufferByte) bgr.getRaster()
                .getDataBuffer()).getData();

        for (int i = 0, j = 0; i < gray.length; i++, j += 3) {
            int b = pixels[j]     & 0xFF;
            int g = pixels[j + 1] & 0xFF;
            int r = pixels[j + 2] & 0xFF;
            // pesos inteiros para evitar float por pixel
            gray[i] = (byte)((b * 29 + g * 150 + r * 77) >> 8);
        }
        return gray;
    }

    /**
     * dilate — morfologia de erosão máxima com kernel retangular.
     * Para cada pixel, pega o máximo numa janela (kernelSize × kernelSize).
     * Equivalente ao dilate() do OpenCV com kernel de uns.
     */
    private static byte[] dilate(byte[] src, int w, int h, int ks) {
        byte[] dst  = new byte[w * h];
        int    half = ks / 2;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int max = 0;

                int yMin = Math.max(0, y - half);
                int yMax = Math.min(h - 1, y + half);
                int xMin = Math.max(0, x - half);
                int xMax = Math.min(w - 1, x + half);

                for (int ky = yMin; ky <= yMax; ky++) {
                    for (int kx = xMin; kx <= xMax; kx++) {
                        int v = src[ky * w + kx] & 0xFF;
                        if (v > max) max = v;
                    }
                }
                dst[y * w + x] = (byte) max;
            }
        }
        return dst;
    }

    /**
     * absdiff — diferença absoluta pixel a pixel.
     * absDiff[i] = |a[i] − b[i]|
     */
    private static byte[] absDiff(byte[] a, byte[] b) {
        byte[] out = new byte[a.length];
        for (int i = 0; i < a.length; i++)
            out[i] = (byte) Math.abs((a[i] & 0xFF) - (b[i] & 0xFF));
        return out;
    }

    /**
     * bitwise_not — inverte todos os bits de cada byte.
     * not[i] = 255 − src[i]  (equivalente para uint8)
     */
    private static byte[] bitwiseNot(byte[] src) {
        byte[] out = new byte[src.length];
        for (int i = 0; i < src.length; i++)
            out[i] = (byte) ~src[i];
        return out;
    }

    // ══════════════════════════════════════════════════════════════
    //  Conversões de imagem
    // ══════════════════════════════════════════════════════════════

    /**
     * Converte grayscale byte[] → BufferedImage TYPE_3BYTE_BGR
     * (replica o canal gray nos três canais B, G, R).
     */
    private static BufferedImage grayBytesToBGR(byte[] gray, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        byte[] dst = ((DataBufferByte) out.getRaster()
                .getDataBuffer()).getData();

        for (int i = 0, j = 0; i < gray.length; i++, j += 3) {
            dst[j] = dst[j + 1] = dst[j + 2] = gray[i];
        }
        return out;
    }

    /**
     * Garante que a imagem seja TYPE_3BYTE_BGR para leitura direta
     * de bytes. Redesenha em novo buffer caso o tipo seja diferente.
     */
    private static BufferedImage ensureBGR(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_3BYTE_BGR) return src;
        BufferedImage out = new BufferedImage(
                src.getWidth(), src.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }
}
