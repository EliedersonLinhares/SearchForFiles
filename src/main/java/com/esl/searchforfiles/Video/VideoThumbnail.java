package com.esl.searchforfiles.Video;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Captura thumbnails de vídeo via JNI + FFmpeg.
 * Thread-safe: múltiplas capturas simultâneas são limitadas por semáforo.
 *
 * Uso mínimo:
 *   BufferedImage img = VideoThumbnail.capture("video.mp4");
 *
 * Uso avançado:
 *   VideoThumbnail.Result r = VideoThumbnail.builder("video.mp4")
 *       .position(0.5)
 *       .size(256, 256)
 *       .build()
 *       .capture();
 */
public final class VideoThumbnail {

    /**
     * Limita capturas simultâneas para não saturar CPU/heap.
     * Ajuste conforme o hardware: 4 é seguro para a maioria.
     */
    private static final int MAX_CONCURRENT = 4;
    private static final Semaphore SEMAPHORE = new Semaphore(MAX_CONCURRENT, true);

    private static final AtomicInteger WORKER_COUNT = new AtomicInteger(0);
    /**
     * Pool dedicado para capturas assíncronas.
     * Daemon = não impede o encerramento da JVM.
     */
    private static final ExecutorService POOL = Executors.newFixedThreadPool(
            MAX_CONCURRENT,
            r -> {
                Thread t = new Thread(r, "ThumbnailWorker-"
                        + WORKER_COUNT.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
    );

    // ── Métodos nativos ────────────────────────────────────────────
    // Retorna: [width(4b) | height(4b) | timestamp_ms(8b) | RGB24...]
    // O JNI empacota metadados + dados em um único array para evitar
    // campos estáticos compartilhados entre threads.
    private static native byte[] captureThumbnail(String path,
                                                  double positionRatio,
                                                  int outWidth,
                                                  int outHeight);

    private static native int    saveThumbnail(byte[] rgbData,
                                               int width, int height,
                                               String outPath);

    private static native double getDuration(String path);

    static {
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

        Result(BufferedImage img, int w, int h, double ts, String src) {
            this.image            = img;
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

        public String timestampFormatted() {
            long t = (long) timestampSeconds;
            return String.format("%02d:%02d", t / 60, t % 60);
        }

        public void saveTo(String outPath) throws IOException {
            String ext = outPath.substring(outPath.lastIndexOf('.') + 1)
                    .toLowerCase();
            String fmt = ext.equals("jpg") || ext.equals("jpeg")
                    ? "jpeg" : "png";
            ImageIO.write(image, fmt, new File(outPath));
        }

        public int saveViaNative(String outPath) {
            byte[] raw = rgbFromImage(image);
            return saveThumbnail(raw, width, height, outPath);
        }

        @Override
        public String toString() {
            return String.format("Thumbnail[%dx%d @ %s from '%s']",
                    width, height, timestampFormatted(),
                    new File(sourceFile).getName());
        }
    }

    /* ============================================================== */
    /*  Builder                                                         */
    /* ============================================================== */
    public static final class Builder {
        private final String path;
        private double positionRatio = 0.0;
        private int    outWidth      = 0;
        private int    outHeight     = 0;

        Builder(String path) { this.path = path; }

        public Builder position(double r) {
            positionRatio = Math.max(0.01, Math.min(0.99, r));
            return this;
        }
        public Builder width(int w)       { outWidth  = w; return this; }
        public Builder height(int h)      { outHeight = h; return this; }
        public Builder size(int w, int h) { outWidth  = w; outHeight = h; return this; }

        /** Captura síncrona — bloqueia a thread chamante */
        public Result capture() {
            return VideoThumbnail.captureInternal(
                    path, positionRatio, outWidth, outHeight);
        }

        /** Captura assíncrona no pool dedicado */
        public CompletableFuture<Result> captureAsync() {
            return CompletableFuture.supplyAsync(this::capture, POOL);
        }
    }

    /* ============================================================== */
    /*  API pública                                                     */
    /* ============================================================== */
    public static Builder builder(String videoPath) {
        return new Builder(videoPath);
    }

    /** Captura rápida, posição aleatória, resolução original */
    public static BufferedImage capture(String videoPath) {
        Result r = captureInternal(videoPath, 0.0, 0, 0);
        return r != null ? r.image() : null;
    }

    /** Captura e salva em arquivo */
    public static Result captureAndSave(String videoPath, String outPath) {
        Result r = captureInternal(videoPath, 0.0, 0, 0);
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
     * Gera thumbnails em lote com controle de concorrência.
     * Não bloqueia — retorna lista de futures.
     *
     * @param paths     Lista de caminhos de vídeo
     * @param cacheDir  Pasta onde salvar os .jpg gerados
     * @param size      Lado do thumbnail quadrado (ex: 256)
     */
    public static List<CompletableFuture<Result>> captureAllAsync(
            List<String> paths, File cacheDir, int size) {

        List<CompletableFuture<Result>> futures = new ArrayList<>();

        for (String path : paths) {
            String name  = new File(path).getName();
            String noExt = name.contains(".")
                    ? name.substring(0, name.lastIndexOf('.')) : name;
            File cacheFile = new File(cacheDir, noExt + "_thumb.jpg");

            CompletableFuture<Result> f = CompletableFuture
                    .supplyAsync(() -> {
                        // Cache hit
                        if (cacheFile.exists()) return null;

                        return VideoThumbnail.builder(path)
                                .size(size, size)
                                .capture();
                    }, POOL)
                    .thenApply(r -> {
                        if (r != null) {
                            try { r.saveTo(cacheFile.getAbsolutePath()); }
                            catch (IOException e) {
                                System.err.println("[batch] Erro: " + e.getMessage());
                            }
                        }
                        return r;
                    })
                    .exceptionally(ex -> {
                        System.err.println("[batch] Falha em " + path
                                + ": " + ex.getMessage());
                        return null;
                    });

            futures.add(f);
        }
        return futures;
    }

    /** Duração do vídeo em segundos sem decodificar frames */
    public static double getDurationSeconds(String videoPath) {
        return getDuration(videoPath);
    }

    /** Encerra o pool de workers (chamar ao fechar a aplicação) */
    public static void shutdown() {
        POOL.shutdown();
    }

    /* ============================================================== */
    /*  Interno                                                         */
    /* ============================================================== */
//    private static Result captureInternal(String path, double ratio,
//                                          int w, int h) {
//        try {
//            SEMAPHORE.acquire(); // bloqueia se MAX_CONCURRENT ativo
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            return null;
//        }
//
//        try {
//            byte[] raw = captureThumbnail(path, ratio, w, h);
//            if (raw == null || raw.length < 16) return null;
//
//            // Desempacotar header: [width(4) | height(4) | ts_ms(8) | RGB...]
//            int    fw  = readInt(raw, 0);
//            int    fh  = readInt(raw, 4);
//            double fts = readLong(raw, 8) / 1000.0;
//            int    offset = 16;
//
//            if (fw <= 0 || fh <= 0 || raw.length < offset + fw * fh * 3)
//                return null;
//
//            byte[] rgb = new byte[fw * fh * 3];
//            System.arraycopy(raw, offset, rgb, 0, rgb.length);
//
//            BufferedImage img = rgbToImage(rgb, fw, fh);
//            return new Result(img, fw, fh, fts, path);
//
//        } catch (Exception e) {
//            System.err.println("[VideoThumbnail] Erro: " + e.getMessage());
//            return null;
//        } finally {
//            SEMAPHORE.release();
//        }
//    }

    private static Result captureInternal(String path, double ratio,
                                          int w, int h) {
        try {
            SEMAPHORE.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        try {

            byte[] raw = captureThumbnail(path, ratio, w, h);

            if (raw == null || raw.length < 16) {
                System.err.println("[Thumb] Array inválido: "
                        + (raw == null ? "null" : raw.length + " bytes (mínimo 16)"));
                return null;
            }

            int    fw  = readInt(raw, 0);
            int    fh  = readInt(raw, 4);
            double fts = readLong(raw, 8) / 1000.0;

            int expectedSize = 16 + fw * fh * 3;

            if (fw <= 0 || fh <= 0 || raw.length < expectedSize) {
                return null;
            }

            byte[] rgb = new byte[fw * fh * 3];
            System.arraycopy(raw, 16, rgb, 0, rgb.length);

            BufferedImage img = rgbToImage(rgb, fw, fh);

            return new Result(img, fw, fh, fts, path);

        } catch (Exception e) {
            System.err.println("[Thumb] Exceção em captureInternal: "
                    + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            SEMAPHORE.release();
        }
    }

    private static int readInt(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off+1] & 0xFF) << 16)
                | ((b[off+2] & 0xFF) << 8)  | (b[off+3] & 0xFF);
    }

    private static long readLong(byte[] b, int off) {
        long hi = readInt(b, off) & 0xFFFFFFFFL;
        long lo = readInt(b, off + 4) & 0xFFFFFFFFL;
        return (hi << 32) | lo;
    }

    static BufferedImage rgbToImage(byte[] rgb, int w, int h) {
        BufferedImage img = new BufferedImage(
                w, h, BufferedImage.TYPE_3BYTE_BGR);
        byte[] dst = ((DataBufferByte) img.getRaster()
                .getDataBuffer()).getData();
        for (int i = 0, n = w * h; i < n; i++) {
            dst[i*3]   = rgb[i*3+2];
            dst[i*3+1] = rgb[i*3+1];
            dst[i*3+2] = rgb[i*3];
        }
        return img;
    }

    static byte[] rgbFromImage(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage c = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        c.getGraphics().drawImage(img, 0, 0, null);
        byte[] bgr = ((DataBufferByte) c.getRaster().getDataBuffer()).getData();
        byte[] rgb = new byte[bgr.length];
        for (int i = 0, n = w * h; i < n; i++) {
            rgb[i*3]   = bgr[i*3+2];
            rgb[i*3+1] = bgr[i*3+1];
            rgb[i*3+2] = bgr[i*3];
        }
        return rgb;
    }
}