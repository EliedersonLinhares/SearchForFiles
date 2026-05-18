package com.esl.searchforfiles.actions.imageEditor.actions;


import com.esl.searchforfiles.actions.imageEditor.ImageEditAction;

import java.awt.image.BufferedImage;

/**
 * Ação de ajuste de imagem: brilho, contraste, gamma e saturação.
 * Encapsula a lógica de pixel processing do FiltersManager original,
 * adaptada para o pipeline de ações do editor (sem dependências de i18n/VideoPlayer).
 */
public class ImageAdjustAction extends ImageEditAction {

    // Defaults: brilho=0, contraste=1, gamma=1, saturação=1  (sem efeito)
    private double brightness  = 0.0;   // -1.0 a  1.0
    private double contrast    = 1.0;   //  0.0 a  2.0
    private double gamma       = 1.0;   //  0.1 a 10.0
    private double saturation  = 1.0;   //  0.0 a  3.0

    public ImageAdjustAction() {
        super("Ajuste de imagem");
    }

    // ── Getters / Setters ──────────────────────────────────────────
    public double getBrightness()             { return brightness; }
    public void   setBrightness(double v)     { brightness = v;    syncParams(); }

    public double getContrast()               { return contrast; }
    public void   setContrast(double v)       { contrast = v;    syncParams(); }

    public double getGamma()                  { return gamma; }
    public void   setGamma(double v)          { gamma = v;    syncParams(); }

    public double getSaturation()             { return saturation; }
    public void   setSaturation(double v)     { saturation = v;   syncParams(); }

    /** Mantém o mapa genérico de params sincronizado (usado por getSummary). */
    private void syncParams() {
        setParam("brilho",     String.format("%.2f", brightness));
        setParam("contraste",  String.format("%.2f", contrast));
        setParam("gamma",      String.format("%.2f", gamma));
        setParam("saturação",  String.format("%.2f", saturation));
    }

    /** Verifica se ao menos um parâmetro difere do neutro. */
    public boolean hasEffect() {
        return brightness != 0.0 || contrast != 1.0 || gamma != 1.0 || saturation != 1.0;
    }

    // ── Aplicação dos filtros ──────────────────────────────────────
    /**
     * Aplica os filtros na imagem original e retorna uma nova imagem processada.
     * Usa LUT (look-up table) para brilho + contraste + gamma e
     * processamento por pixel para saturação.
     */
    public BufferedImage apply(BufferedImage original) {
        if (!isEnabled() || original == null || !hasEffect()) return original;

        int   width    = original.getWidth();
        int   height   = original.getHeight();
        int[] src      = original.getRGB(0, 0, width, height, null, 0, width);
        int[] dst      = new int[src.length];

        // Monta LUT combinada para brilho + contraste + gamma (evita 3 ops por pixel)
        int[] lut = null;
        if (brightness != 0.0 || contrast != 1.0 || gamma != 1.0) {
            lut = new int[256];
            for (int i = 0; i < 256; i++) {
                double v = i;
                if (contrast != 1.0)   v = (v - 128.0) * contrast + 128.0;
                if (brightness != 0.0) v += brightness * 255.0;
                if (gamma != 1.0) {
                    v = Math.max(0, Math.min(255, v));
                    v = 255.0 * Math.pow(v / 255.0, 1.0 / gamma);
                }
                lut[i] = (int) Math.max(0, Math.min(255, v));
            }
        }

        if (saturation != 1.0) {
            // Caminho completo: LUT + saturação por pixel
            for (int i = 0; i < src.length; i++) {
                int rgb = src[i];
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >>  8) & 0xFF;
                int b =  rgb        & 0xFF;

                if (lut != null) { r = lut[r]; g = lut[g]; b = lut[b]; }

                float gray = r * 0.299f + g * 0.587f + b * 0.114f;
                r = clamp((int)(gray + saturation * (r - gray)), 0, 255);
                g = clamp((int)(gray + saturation * (g - gray)), 0, 255);
                b = clamp((int)(gray + saturation * (b - gray)), 0, 255);

                dst[i] = (r << 16) | (g << 8) | b;
            }
        } else if (lut != null) {
            // Caminho rápido: apenas LUT
            for (int i = 0; i < src.length; i++) {
                int rgb = src[i];
                dst[i] = (lut[(rgb >> 16) & 0xFF] << 16)
                        | (lut[(rgb >>  8) & 0xFF] <<  8)
                        |  lut[ rgb        & 0xFF];
            }
        } else {
            System.arraycopy(src, 0, dst, 0, src.length);
        }

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, width, height, dst, 0, width);
        return out;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
