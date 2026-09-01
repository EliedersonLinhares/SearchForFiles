package com.esl.searchforfiles.preview.model3DViewer;

import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.jme3.util.BufferUtils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

public class ScreenshotManager {

    private ViewPort screenshotViewPort;
    private Camera screenshotCamera;
    private FrameBuffer screenshotFrameBuffer;
    private Texture2D screenshotTexture;
    private int screenshotWidth = -1;
    private int screenshotHeight = -1;
    private static final int SUPERSAMPLE_FACTOR = 4; // renderiza em 3x e reduz — ajustável (2 = mais rápido, 4 = mais suave)
    private final Map<Geometry, Float> originalLineWidths = new HashMap<>();

   private final Obj3DApp app;
    
    public ScreenshotManager(Obj3DApp app) {
        this.app = app;
    }

    /**
     * Cria (ou recria, se a resolução mudou) um viewport off-screen dedicado
     * a capturas de tela, com framebuffer RGBA8 — necessário para suportar
     * fundo transparente, já que o framebuffer principal não tem canal alpha.
     */
    private void ensureScreenshotViewPort(int renderWidth, int renderHeight) {
        if (screenshotViewPort != null && screenshotWidth == renderWidth && screenshotHeight == renderHeight) {
            return;
        }

        if (screenshotViewPort != null) {
            app.getRenderManager().removePreView(screenshotViewPort);
            screenshotFrameBuffer.dispose();
        }

        screenshotCamera = new Camera(renderWidth, renderHeight);
        screenshotFrameBuffer = new FrameBuffer(renderWidth, renderHeight, 1);
        screenshotTexture = new Texture2D(renderWidth, renderHeight, Image.Format.RGBA8);
        screenshotFrameBuffer.setDepthBuffer(Image.Format.Depth);
        screenshotFrameBuffer.setColorTexture(screenshotTexture);

        screenshotViewPort =  app.getRenderManager().createPreView("ScreenshotView", screenshotCamera);
        screenshotViewPort.setClearFlags(true, true, true);
        screenshotViewPort.attachScene(app.getRootNode());
        screenshotViewPort.setOutputFrameBuffer(screenshotFrameBuffer);
        screenshotViewPort.setEnabled(false);

        screenshotWidth = renderWidth;
        screenshotHeight = renderHeight;
    }

    /**
     * Captura a cena atual em um arquivo PNG.
     *
     * @param outputFile          arquivo de destino (deve terminar em .png)
     * @param transparentBackground se true, o fundo fica transparente (canal alpha);
     *                             se false, usa a cor de fundo atual do viewport principal
     * @param showGrid             se a grade de referência deve aparecer na captura
     * @param width                largura desejada da imagem (use a largura atual do canvas se null)
     * @param height               altura desejada da imagem (use a altura atual do canvas se null)
     */
    public void captureScreenshot(File outputFile, boolean transparentBackground, boolean showGrid,
                                  Integer width, Integer height,
                                  Runnable onSuccess, Consumer<String> onError) {
        app.enqueue(() -> {
            try {
                int finalW = (width != null) ? width : app.getCamera().getWidth();
                int finalH = (height != null) ? height : app.getCamera().getHeight();
                int renderW = finalW * SUPERSAMPLE_FACTOR;
                int renderH = finalH * SUPERSAMPLE_FACTOR;

                ensureScreenshotViewPort(renderW, renderH);

                screenshotCamera.setLocation(app.getCamera().getLocation());
                screenshotCamera.setRotation(app.getCamera().getRotation());
                screenshotCamera.setParallelProjection(app.getCamera().isParallelProjection());
                // CORRIGIDO: recalcula o frustum para a proporção de aspecto do framebuffer
                // de destino, preservando o FOV vertical (evita distorção/esticamento)
                applyAspectCorrectFrustum(screenshotCamera, app.getCamera(), renderW, renderH);

                ColorRGBA captureBg = transparentBackground
                        ? new ColorRGBA(0f, 0f, 0f, 0f)
                        : app.getViewPort().getBackgroundColor();
                screenshotViewPort.setBackgroundColor(captureBg);

                Spatial.CullHint originalGridCull = app.getGridNode().getCullHint();
                app.getGridNode().setCullHint(showGrid ? Spatial.CullHint.Never : Spatial.CullHint.Always);

                // NOVO: escala a espessura das linhas proporcionalmente ao supersampling
                scaleLineWidthsForCapture(app.getRootNode(), SUPERSAMPLE_FACTOR);

                screenshotViewPort.setEnabled(true);
                 app.getRenderManager().renderViewPort(screenshotViewPort, 0.0f);
                screenshotViewPort.setEnabled(false);

                // NOVO: restaura a espessura original, para não afetar a tela normal
                restoreLineWidthsAfterCapture(app.getRootNode());

                app.getGridNode().setCullHint(originalGridCull);

                // --- TRECHO ATUALIZADO E CORRIGIDO (CORES E ORIENTAÇÃO) ---
                ByteBuffer pixelBuffer = BufferUtils.createByteBuffer(renderW * renderH * 4);
                app.getRenderer().readFrameBuffer(screenshotFrameBuffer, pixelBuffer);

                BufferedImage rawImage = new BufferedImage(renderW, renderH, BufferedImage.TYPE_4BYTE_ABGR);
                java.awt.image.DataBufferByte db = (java.awt.image.DataBufferByte) rawImage.getRaster().getDataBuffer();
                byte[] cpuPixels = db.getData();

                // Inverte verticalmente a leitura mapeando as linhas de baixo para cima
                for (int y = 0; y < renderH; y++) {
                    // Linha de destino no Java (de cima para baixo)
                    int targetRowIdx = y * renderW * 4;

                    // Linha de origem na GPU (de baixo para cima)
                    int sourceRowIdx = (renderH - y - 1) * renderW * 4;

                    for (int x = 0; x < renderW; x++) {
                        int targetByteIdx = targetRowIdx + (x * 4);
                        int sourceByteIdx = sourceRowIdx + (x * 4);

                        // Lemos a posição correta da linha invertida no buffer da GPU
                        byte r = pixelBuffer.get(sourceByteIdx);
                        byte g = pixelBuffer.get(sourceByteIdx + 1);
                        byte b = pixelBuffer.get(sourceByteIdx + 2);
                        byte a = pixelBuffer.get(sourceByteIdx + 3);

                        // Escreve no formato correto do Java (Alpha, Blue, Green, Red)
                        cpuPixels[targetByteIdx]     = a;
                        cpuPixels[targetByteIdx + 1] = b; // Azul correto
                        cpuPixels[targetByteIdx + 2] = g;
                        cpuPixels[targetByteIdx + 3] = r; // Vermelho correto
                    }
                }


                BufferedImage finalImage = downscaleSmooth(rawImage, finalW, finalH);

                writePngWithDpi(finalImage, outputFile, 300);

                if (onSuccess != null) onSuccess.run();
            } catch (Exception ex) {
                ex.printStackTrace();
                if (onError != null) onError.accept(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        });
    }

    /**
     * Recalcula o frustum da câmera de captura para a nova proporção de aspecto,
     * usando a estratégia "contain" (nunca corta): compara a proporção da câmera
     * original com a da resolução de destino e sempre EXPANDE o eixo necessário
     * (nunca reduz), garantindo que tudo que era visível na tela ao vivo continue
     * visível na captura — só pode sobrar mais margem de um dos lados.
     */
    private void applyAspectCorrectFrustum(Camera targetCam, Camera sourceCam, int renderW, int renderH) {
        float targetAspect = (float) renderW / (float) renderH;

        float near = sourceCam.getFrustumNear();
        float far = sourceCam.getFrustumFar();
        float top = sourceCam.getFrustumTop();
        float bottom = sourceCam.getFrustumBottom();
        float left = sourceCam.getFrustumLeft();
        float right = sourceCam.getFrustumRight();

        float sourceAspect = (right - left) / (top - bottom);

        float newLeft, newRight, newTop, newBottom;

        if (targetAspect >= sourceAspect) {
            // Resolução de destino é relativamente mais larga: mantém a altura
            // (top/bottom) igual à câmera original e EXPANDE a largura para caber
            newTop = top;
            newBottom = bottom;
            float halfWidth = (top - bottom) / 2f * targetAspect;
            newLeft = -halfWidth;
            newRight = halfWidth;
        } else {
            // Resolução de destino é relativamente mais estreita: mantém a largura
            // (left/right) igual à câmera original e EXPANDE a altura para caber
            newLeft = left;
            newRight = right;
            float halfHeight = (right - left) / 2f / targetAspect;
            newBottom = -halfHeight;
            newTop = halfHeight;
        }

        targetCam.setFrustum(near, far, newLeft, newRight, newTop, newBottom);
    }

    /**
     * Reduz a imagem renderizada em alta resolução para o tamanho final desejado,
     * usando interpolação bicúbica — o downscale de uma imagem maior é o que
     * produz o efeito de anti-aliasing (supersampling / SSAA).
     */
    private BufferedImage downscaleSmooth(BufferedImage source, int targetW, int targetH) {
        BufferedImage result = new BufferedImage(targetW, targetH, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g2 = result.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(source, 0, 0, targetW, targetH, null);
        g2.dispose();
        return result;
    }

    /**
     * Salva o PNG incluindo o metadado de densidade de pixels (chunk pHYs),
     * para que programas como Windows Explorer/Photoshop mostrem a DPI
     * correta em vez do valor genérico padrão (geralmente 72 ou 96).
     */
    private void writePngWithDpi(BufferedImage image, File outputFile, int dpi) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
        if (!writers.hasNext()) {
            ImageIO.write(image, "png", outputFile); // fallback sem DPI, não deveria acontecer
            return;
        }
        ImageWriter writer = writers.next();
        ImageWriteParam writeParam = writer.getDefaultWriteParam();
        IIOMetadata metadata = writer.getDefaultImageMetadata(
                new javax.imageio.ImageTypeSpecifier(image), writeParam);

        double pixelsPerMillimeter = dpi / 25.4;
        IIOMetadataNode physNode = new IIOMetadataNode("pHYs");
        physNode.setAttribute("pixelsPerUnitXAxis", Integer.toString((int) pixelsPerMillimeter * 1000));
        physNode.setAttribute("pixelsPerUnitYAxis", Integer.toString((int) pixelsPerMillimeter * 1000));
        physNode.setAttribute("unitSpecifier", "meter");

        IIOMetadataNode root = new IIOMetadataNode("javax_imageio_png_1.0");
        root.appendChild(physNode);
        metadata.mergeTree("javax_imageio_png_1.0", root);

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputFile)) {
            writer.setOutput(ios);
            writer.write(metadata, new IIOImage(image, null, metadata), writeParam);
        } finally {
            writer.dispose();
        }
    }

    /**
     * Antes de renderizar a captura em alta resolução, multiplica a espessura
     * de todo material com Mesh.Mode.Lines pelo fator de supersampling —
     * senão a linha fica proporcionalmente mais fina no framebuffer grande
     * e "some" no downscale final.
     */
    private void scaleLineWidthsForCapture(Spatial spatial, float factor) {
        if (spatial instanceof Geometry geom) {
            Mesh mesh = geom.getMesh();
            if (mesh.getMode() == Mesh.Mode.Lines && geom.getMaterial() != null) {
                RenderState rs = geom.getMaterial().getAdditionalRenderState();
                float currentWidth = rs.getLineWidth();
                originalLineWidths.put(geom, currentWidth);
                rs.setLineWidth(currentWidth * factor);
            }
        } else if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                scaleLineWidthsForCapture(child, factor);
            }
        }
    }

    private void restoreLineWidthsAfterCapture(Spatial spatial) {
        if (spatial instanceof Geometry geom) {
            Float original = originalLineWidths.remove(geom);
            if (original != null && geom.getMaterial() != null) {
                geom.getMaterial().getAdditionalRenderState().setLineWidth(original);
            }
        } else if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                restoreLineWidthsAfterCapture(child);
            }
        }
    }
}
