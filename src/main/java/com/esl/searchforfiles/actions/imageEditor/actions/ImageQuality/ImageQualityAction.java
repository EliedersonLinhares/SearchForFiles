package com.esl.searchforfiles.actions.imageEditor.actions.ImageQuality;


import com.esl.searchforfiles.actions.imageEditor.ImageEditAction;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Action de compressão JPEG.
 *
 * Simula o slider de qualidade do Photoshop: a imagem é recomprimida
 * em memória com o nível escolhido e devolvida como BufferedImage,
 * permitindo visualizar os artefatos de compressão em tempo real.
 *
 * Qualidade 100 = sem perda visível (passa direto sem processar).
 * Qualidade 0   = máxima compressão / mínima qualidade.
 *
 * Usa apenas javax.imageio — sem dependências externas.
 */
public class ImageQualityAction extends ImageEditAction {

    // 0–100 (mapeado para 0.0–1.0 no ImageWriter)
    private int quality = 100;

    public ImageQualityAction() {
        super("Qualidade JPEG");
        syncParams();
    }

    public int  getQuality()      { return quality; }
    public void setQuality(int v) { quality = Math.max(0, Math.min(100, v)); syncParams(); }


    public boolean hasEffect() { return quality < 100; }

    private void syncParams() {
        setParam("qualidade", quality + "%");
    }

    // ── Aplicação ─────────────────────────────────────────────────


    public BufferedImage apply(BufferedImage original) {
        if (!isEnabled() || original == null || !hasEffect()) return original;

        try {
            // ── 1. Obtém o writer JPEG ────────────────────────────
            Iterator<ImageWriter> writers =
                    ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) return original;
            ImageWriter writer = writers.next();

            // ── 2. Configura a qualidade ──────────────────────────
            JPEGImageWriteParam params = new JPEGImageWriteParam(null);
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(quality / 100f);   // 0.0–1.0

            // ── 3. Comprime em memória ────────────────────────────
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);

                // JPEG não suporta canal alpha — converte para RGB se necessário
                BufferedImage rgb = toRGB(original);
                writer.write(null, new IIOImage(rgb, null, null), params);
            } finally {
                writer.dispose();
            }

            // ── 4. Lê de volta como BufferedImage ─────────────────
            byte[] jpegBytes = baos.toByteArray();
            return ImageIO.read(new ByteArrayInputStream(jpegBytes));

        } catch (IOException e) {
            // Em caso de erro retorna a imagem original sem processar
            return original;
        }
    }

    /**
     * Garante que a imagem está em TYPE_INT_RGB (sem canal alpha),
     * requisito do encoder JPEG nativo do Java.
     */
    private static BufferedImage toRGB(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage rgb = new BufferedImage(
                src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);   // fundo branco para imagens com transparência
        g.fillRect(0, 0, src.getWidth(), src.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }
}
