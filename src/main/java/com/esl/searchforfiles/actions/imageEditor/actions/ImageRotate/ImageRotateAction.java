package com.esl.searchforfiles.actions.imageEditor.actions.ImageRotate;


import com.esl.searchforfiles.actions.imageEditor.ImageEditAction;

import java.awt.image.BufferedImage;

public class ImageRotateAction extends ImageEditAction {

    public enum Transform {
        NONE,
        ROTATE_CW,    // 90° horário
        ROTATE_CCW,   // 90° anti-horário
        FLIP_H,       // inverter horizontal
        FLIP_V        // inverter vertical
    }

    private Transform transform = Transform.NONE;

    public ImageRotateAction() {
        super("Girar / Inverter");
    }

    public Transform getTransform() { return transform; }

    public void setTransform(Transform t) {
        this.transform = t;
        syncParams();
    }

    private void syncParams() {
        setParam("operação", switch (transform) {
            case ROTATE_CW  -> "90° horário";
            case ROTATE_CCW -> "90° anti-horário";
            case FLIP_H     -> "inverter horizontal";
            case FLIP_V     -> "inverter vertical";
            case NONE       -> "nenhuma";
        });
    }

    public boolean hasEffect() {
        return transform != Transform.NONE;
    }


    public BufferedImage apply(BufferedImage original) {
        if (!isEnabled() || original == null || !hasEffect()) return original;

        int w = original.getWidth();
        int h = original.getHeight();

        return switch (transform) {
            case ROTATE_CW  -> rotateCW(original, w, h);
            case ROTATE_CCW -> rotateCCW(original, w, h);
            case FLIP_H     -> flipH(original, w, h);
            case FLIP_V     -> flipV(original, w, h);
            case NONE       -> original;
        };
    }

    // ── transformações ────────────────────────────────────────────

    private BufferedImage rotateCW(BufferedImage src, int w, int h) {
        // saída tem dimensões trocadas
        BufferedImage out = new BufferedImage(h, w, src.getType());
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out.setRGB(h - 1 - y, x, src.getRGB(x, y));
        return out;
    }

    private BufferedImage rotateCCW(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(h, w, src.getType());
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out.setRGB(y, w - 1 - x, src.getRGB(x, y));
        return out;
    }

    private BufferedImage flipH(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, src.getType());
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out.setRGB(w - 1 - x, y, src.getRGB(x, y));
        return out;
    }

    private BufferedImage flipV(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, src.getType());
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out.setRGB(x, h - 1 - y, src.getRGB(x, y));
        return out;
    }
}