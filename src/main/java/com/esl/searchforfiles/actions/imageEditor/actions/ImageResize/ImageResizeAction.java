package com.esl.searchforfiles.actions.imageEditor.actions.ImageResize;


import com.esl.searchforfiles.actions.imageEditor.ImageEditAction;

import java.awt.*;
import java.awt.image.BufferedImage;

public class ImageResizeAction extends ImageEditAction {

    public enum Mode { MANUAL, PERCENTAGE }

    // ── Estado ────────────────────────────────────────────────────
    private Mode mode        = Mode.MANUAL;
    private int  targetWidth  = -1;   // -1 = não definido
    private int  targetHeight = -1;
    private double percentage = 100.0; // 1..∞  (100 = sem efeito)

    public ImageResizeAction() {
        super("Redimensionar");
    }

    // ── Getters / Setters ─────────────────────────────────────────
    public Mode   getMode()               { return mode; }
    public void   setMode(Mode m)         { mode = m;           syncParams(); }

    public int    getTargetWidth()        { return targetWidth; }
    public void   setTargetWidth(int v)   { targetWidth  = v;   syncParams(); }

    public int    getTargetHeight()       { return targetHeight; }
    public void   setTargetHeight(int v)  { targetHeight = v;   syncParams(); }

    public double getPercentage()         { return percentage; }
    public void   setPercentage(double v) { percentage = v;     syncParams(); }

    // ── Efeito ────────────────────────────────────────────────────
    public boolean hasEffect() {
        if (mode == Mode.PERCENTAGE) return percentage != 100.0;
        return targetWidth > 0 || targetHeight > 0;
    }

    private void syncParams() {
        if (mode == Mode.PERCENTAGE) {
            setParam("modo",       "porcentagem");
            setParam("escala",     String.format("%.1f%%", percentage));
            setParam("largura",    "—");
            setParam("altura",     "—");
        } else {
            setParam("modo",       "manual");
            setParam("escala",     "—");
            setParam("largura",    targetWidth  > 0 ? targetWidth  + " px" : "original");
            setParam("altura",     targetHeight > 0 ? targetHeight + " px" : "original");
        }
    }

    // ── Aplicação ─────────────────────────────────────────────────
    public BufferedImage apply(BufferedImage original) {
        if (!isEnabled() || original == null || !hasEffect()) return original;

        int srcW = original.getWidth();
        int srcH = original.getHeight();
        int dstW, dstH;

        if (mode == Mode.PERCENTAGE) {
            double scale = percentage / 100.0;
            dstW = Math.max(1, (int) Math.round(srcW * scale));
            dstH = Math.max(1, (int) Math.round(srcH * scale));
        } else {
            if (targetWidth > 0 && targetHeight > 0) {
                dstW = targetWidth;
                dstH = targetHeight;
            } else if (targetWidth > 0) {
                dstW = targetWidth;
                dstH = Math.max(1, (int) Math.round((double) srcH / srcW * dstW));
            } else {
                dstH = targetHeight;
                dstW = Math.max(1, (int) Math.round((double) srcW / srcH * dstH));
            }
        }

        if (dstW == srcW && dstH == srcH) return original;

        BufferedImage out = new BufferedImage(dstW, dstH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(original, 0, 0, dstW, dstH, null);
        g.dispose();
        return out;
    }
}

