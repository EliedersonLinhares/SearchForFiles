package com.esl.searchforfiles.Video;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Captura thumbnails de vídeo via JNI + FFmpeg.
 *
 * Uso mínimo:
 *   BufferedImage img = VideoThumbnail.capture("video.mp4");
 *
 * Uso avançado:
 *   VideoThumbnail.Result r = VideoThumbnail.builder("video.mp4")
 *       .position(0.5)          // meio exato
 *       .size(320, 180)         // redimensionar
 *       .build()
 *       .capture();
 *
 *   ImageIO.write(r.image(), "png", new File("thumb.png"));
 */
public final class VideoThumbnail {

    /* ── Metadados escritos pelo JNI após cada captura ───────────── */
    static volatile int    lastWidth            = 0;
    static volatile int    lastHeight           = 0;
    static volatile double lastTimestampSeconds = 0.0;

    // ── Métodos nativos (implementados em thumbnail_jni.c) ─────────
    private static native byte[] captureThumbnail(String path,
                                                  double positionRatio,
                                                  int outWidth,
                                                  int outHeight);

    private static native int    saveThumbnail(byte[] rgbData,
                                               int width, int height,
                                               String outPath);

    private static native double getDuration(String path);

    // ── Carregar a mesma DLL do FFmpegBridge ───────────────────────
    static {
        // Força a inicialização do FFmpegBridge, que carrega a DLL.
        // Como VideoThumbnail está na mesma DLL, isso é suficiente.
        try {
            Class.forName("com.esl.searchforfiles.Video.FFmpegBridge");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                    "FFmpegBridge não encontrado — DLL não carregada", e);
        }
    }

    private VideoThumbnail() {}

    /* ============================================================== */
    /*  Resultado imutável                                             */
    /* ============================================================== */
    public static final class Result {
        private final BufferedImage image;
        private final int           width;
        private final int           height;
        private final double        timestampSeconds;
        private final String        sourceFile;

        Result(BufferedImage image, int w, int h,
               double ts, String src) {
            this.image            = image;
            this.width            = w;
            this.height           = h;
            this.timestampSeconds = ts;
            this.sourceFile       = src;
        }

        public BufferedImage image()            { return image; }
        public int           width()            { return width; }
        public int           height()           { return height; }
        public double        timestampSeconds() { return timestampSeconds; }
        public String        sourceFile()       { return sourceFile; }

        /** Formata o timestamp como MM:SS */
        public String timestampFormatted() {
            long total = (long) timestampSeconds;
            return String.format("%02d:%02d", total / 60, total % 60);
        }

        /**
         * Salva a imagem no caminho indicado.
         * O formato é inferido pela extensão (.png / .jpg).
         */
        public void saveTo(String outPath) throws IOException {
            String ext = outPath.substring(outPath.lastIndexOf('.') + 1)
                    .toLowerCase();
            String fmt = ext.equals("jpg") || ext.equals("jpeg")
                    ? "jpeg" : "png";
            ImageIO.write(image, fmt, new File(outPath));
        }

        /**
         * Salva via JNI (mais rápido para JPEG, pois evita round-trip Java).
         */
        public int saveViaNative(String outPath) {
            byte[] raw = rgbFromImage(image);
            return saveThumbnail(raw, width, height, outPath);
        }

        @Override
        public String toString() {
            return String.format("Thumbnail[%dx%d @ %s from '%s']",
                    width, height, timestampFormatted(), sourceFile);
        }
    }

    /* ============================================================== */
    /*  Builder                                                         */
    /* ============================================================== */
    public static final class Builder {
        private final String path;
        private double positionRatio = -1.0; /* -1 = aleatório */
        private int    outWidth      = 0;
        private int    outHeight     = 0;

        Builder(String path) { this.path = path; }

        /**
         * Posição no vídeo (0.0 = início, 1.0 = fim).
         * Se não chamado, sorteia entre 33% e 66%.
         */
        public Builder position(double ratio) {
            this.positionRatio = Math.max(0.01, Math.min(0.99, ratio));
            return this;
        }

        /** Largura do thumbnail. 0 = manter original. */
        public Builder width(int w)  { this.outWidth  = w; return this; }

        /** Altura do thumbnail. 0 = manter original. */
        public Builder height(int h) { this.outHeight = h; return this; }

        /**
         * Define largura e altura mantendo proporção se apenas um for > 0.
         */
        public Builder size(int w, int h) {
            this.outWidth  = w;
            this.outHeight = h;
            return this;
        }

        /** Captura síncrona */
        public Result capture() {
            return VideoThumbnail.captureInternal(
                    path, positionRatio, outWidth, outHeight);
        }

        /** Captura assíncrona — não bloqueia a EDT */
        public CompletableFuture<Result> captureAsync() {
            return CompletableFuture.supplyAsync(this::capture);
        }
    }

    /* ============================================================== */
    /*  API pública                                                     */
    /* ============================================================== */

    /** Inicia um builder para configuração avançada */
    public static Builder builder(String videoPath) {
        return new Builder(videoPath);
    }

    /**
     * Captura rápida com posição aleatória (33%–66%) e resolução original.
     */
    public static BufferedImage capture(String videoPath) {
        Result r = captureInternal(videoPath, -1.0, 0, 0);
        return r != null ? r.image() : null;
    }

    /**
     * Captura rápida e salva diretamente em arquivo.
     *
     * @param videoPath  Caminho do vídeo
     * @param outPath    Caminho de saída (.png ou .jpg)
     * @return           O resultado, ou null em caso de erro
     */
    public static Result captureAndSave(String videoPath, String outPath) {
        Result r = captureInternal(videoPath, -1.0, 0, 0);
        if (r != null) {
            try { r.saveTo(outPath); }
            catch (IOException e) {
                System.err.println("[VideoThumbnail] Erro ao salvar: "
                        + e.getMessage());
            }
        }
        return r;
    }

    /**
     * Retorna a duração do vídeo em segundos sem decodificar frames.
     */
    public static double getDurationSeconds(String videoPath) {
        return getDuration(videoPath);
    }

    /* ============================================================== */
    /*  Interno                                                         */
    /* ============================================================== */
    private static Result captureInternal(String path, double ratio,
                                          int w, int h) {
        // ratio <= 0 → C sorteia entre 33–66%
        double effectiveRatio = ratio <= 0 ? 0.0 : ratio;

        byte[] rgb;
        try {
            rgb = captureThumbnail(path, effectiveRatio, w, h);
        } catch (Exception e) {
            System.err.println("[VideoThumbnail] Erro nativo: " + e.getMessage());
            return null;
        }

        if (rgb == null) return null;

        // Ler metadados gravados pelo JNI
        int    fw  = lastWidth;
        int    fh  = lastHeight;
        double fts = lastTimestampSeconds;

        BufferedImage img = rgbToImage(rgb, fw, fh);
        return new Result(img, fw, fh, fts, path);
    }

    /** byte[] RGB24 → BufferedImage TYPE_3BYTE_BGR */
    static BufferedImage rgbToImage(byte[] rgb, int w, int h) {
        BufferedImage img = new BufferedImage(
                w, h, BufferedImage.TYPE_3BYTE_BGR);
        byte[] dst = ((DataBufferByte) img.getRaster()
                .getDataBuffer()).getData();

        // RGB → BGR
        for (int i = 0, n = w * h; i < n; i++) {
            dst[i * 3]     = rgb[i * 3 + 2]; // B
            dst[i * 3 + 1] = rgb[i * 3 + 1]; // G
            dst[i * 3 + 2] = rgb[i * 3];     // R
        }
        return img;
    }

    /** BufferedImage → byte[] RGB24 (para salvar via nativo) */
    static byte[] rgbFromImage(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage conv = new BufferedImage(
                w, h, BufferedImage.TYPE_3BYTE_BGR);
        conv.getGraphics().drawImage(img, 0, 0, null);
        byte[] bgr = ((DataBufferByte) conv.getRaster()
                .getDataBuffer()).getData();

        byte[] rgb = new byte[bgr.length];
        for (int i = 0, n = w * h; i < n; i++) {
            rgb[i * 3]     = bgr[i * 3 + 2]; // R
            rgb[i * 3 + 1] = bgr[i * 3 + 1]; // G
            rgb[i * 3 + 2] = bgr[i * 3];     // B
        }
        return rgb;
    }
}