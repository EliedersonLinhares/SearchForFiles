package com.esl.searchforfiles.Video;


import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferUShort;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;


/**
 * Leitura e escrita de imagens via JNI + FFmpeg.
 * Suporta: PNG, JPEG, BMP, TIFF, WebP, GIF, TGA, PPM/PGM/PBM,
 *          HDR, EXR, HEIC/HEIF, AVIF, JPEG-XL, DDS, PCX, SGI, JPEG2000.
 *
 * Uso mínimo:
 *   BufferedImage img = ImageCodec.read("foto.webp");
 *   ImageCodec.write(img, "saida.png");
 *
 * Uso avançado:
 *   ImageCodec.Result r = ImageCodec.builder("foto.tiff")
 *       .pixelFormat(PixelFormat.RGBA32)
 *       .read();
 *   ImageCodec.write(r, "saida.webp",
 *       ImageCodec.writeOptions().quality(85).lossless(false));
 */
public final class ImageCodec {

    /* ── Formatos de pixel (espelha ICPixelFormat do C) ──────────── */
    public enum PixelFormat {
        RGB24 (0, 3, BufferedImage.TYPE_3BYTE_BGR),
        RGBA32(1, 4, BufferedImage.TYPE_4BYTE_ABGR),
        GRAY8 (2, 1, BufferedImage.TYPE_BYTE_GRAY),
        GRAY16(3, 2, BufferedImage.TYPE_USHORT_GRAY),
        RGB48 (4, 6, BufferedImage.TYPE_USHORT_565_RGB), /* fallback */
        RGBA64(5, 8, BufferedImage.TYPE_4BYTE_ABGR);     /* fallback */

        final int id, bpp, awtType;
        PixelFormat(int id, int bpp, int awtType) {
            this.id = id; this.bpp = bpp; this.awtType = awtType;
        }
        static PixelFormat fromId(int id) {
            for (PixelFormat f : values()) if (f.id == id) return f;
            return RGB24;
        }
    }

    /* ── Informações de formato ───────────────────────────────────── */
    public record FormatInfo(
            String extension, String mimeType, String description,
            boolean supportsAlpha, boolean supports16bit,
            boolean supportsLossless, boolean supportsAnimation
    ) {}

    /* ── Opções de escrita ────────────────────────────────────────── */
    public static final class WriteOptions {
        int quality     = 90;
        int compression = 6;
        boolean lossless      = false;
        int     depth         = 8;
        boolean preserveAlpha = true;

        private WriteOptions() {}

        public WriteOptions quality(int q)          { quality = q;          return this; }
        public WriteOptions compression(int c)       { compression = c;      return this; }
        public WriteOptions lossless(boolean l)      { lossless = l;         return this; }
        public WriteOptions depth(int d)             { depth = d;            return this; }
        public WriteOptions preserveAlpha(boolean p) { preserveAlpha = p;    return this; }
    }

    public static WriteOptions writeOptions() { return new WriteOptions(); }

    /* ── Resultado de leitura ─────────────────────────────────────── */
    public static final class Result {
        private final BufferedImage image;
        private final int           width;
        private final int           height;
        private final PixelFormat   format;
        private final String        codecName;
        private final String        mimeType;
        private final String        sourcePath;

        Result(BufferedImage img, int w, int h, PixelFormat fmt,
               String codec, String mime, String src) {
            image = img; width = w; height = h; format = fmt;
            codecName = codec; mimeType = mime; sourcePath = src;
        }

        public BufferedImage image()      { return image; }
        public int           width()      { return width; }
        public int           height()     { return height; }
        public PixelFormat   format()     { return format; }
        public String        codecName()  { return codecName; }
        public String        mimeType()   { return mimeType; }
        public String        sourcePath() { return sourcePath; }

        public byte[] toBytes(String ext) throws IOException {
            return ImageCodec.toBytes(this, ext, null);
        }

        public byte[] toBytes(String ext, WriteOptions opts) throws IOException {
            return ImageCodec.toBytes(this, ext, opts);
        }

        @Override
        public String toString() {
            return String.format("Image[%dx%d %s '%s' (%s)]",
                    width, height, format, codecName, mimeType);
        }
    }

    /* ── Builder de leitura ───────────────────────────────────────── */
    public static final class ReadBuilder {
        private final String path;
        private PixelFormat fmt = PixelFormat.RGB24;
        private byte[]      memBuf = null;

        ReadBuilder(String path) { this.path = path; }
        ReadBuilder(byte[] buf)  { this.path = null; this.memBuf = buf; }

        public ReadBuilder pixelFormat(PixelFormat f) { fmt = f; return this; }

        public Result read() {
            if (memBuf != null) return readFromMemory(memBuf, fmt);
            return readFromFile(path, fmt);
        }

        public CompletableFuture<Result> readAsync() {
            return CompletableFuture.supplyAsync(this::read);
        }
    }

    /* ── Métodos nativos ──────────────────────────────────────────── */
    private static native byte[]   readImage(String path, int outFmt);
    private static native byte[]   readImageFromMemory(byte[] buf, int outFmt);
    private static native int      writeImage(byte[] pixels,
                                              int w, int h, int fmt, int bpp,
                                              String path,
                                              int quality, int compression,
                                              int lossless, int depth);
    private static native byte[]   writeImageToMemory(byte[] pixels,
                                                      int w, int h, int fmt, int bpp,
                                                      String ext,
                                                      int quality, int compression,
                                                      int lossless, int depth);
    private static native String   detectFormat(String path);
    private static native boolean  canRead(String ext);
    private static native boolean  canWrite(String ext);

    static {
        try {
            Class.forName("com.esl.searchforfiles.Video.FFmpegBridge");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("FFmpegBridge não encontrado", e);
        }
    }
    private ImageCodec() {}

    /* ================================================================ */
    /*  API pública — leitura                                            */
    /* ================================================================ */
    public static ReadBuilder builder(String path) { return new ReadBuilder(path); }
    public static ReadBuilder builder(byte[] buf)  { return new ReadBuilder(buf);  }

    /** Lê qualquer formato suportado, retornando BufferedImage */
    public static BufferedImage read(String path) {
        Result r = readFromFile(path, PixelFormat.RGB24);
        return r != null ? r.image() : null;
    }

    /** Lê e detecta automaticamente se tem transparência */
    public static BufferedImage readAuto(String path) {
        String ext = detectFileFormat(path);
        FormatInfo info = ext != null ? formatInfo(ext) : null;
        PixelFormat fmt = (info != null && info.supportsAlpha())
                ? PixelFormat.RGBA32 : PixelFormat.RGB24;
        Result r = readFromFile(path, fmt);
        return r != null ? r.image() : null;
    }

    /** Lê imagem de buffer em memória */
    public static BufferedImage readMemory(byte[] buf) {
        Result r = readFromMemory(buf, PixelFormat.RGB24);
        return r != null ? r.image() : null;
    }

    /* ================================================================ */
    /*  API pública — escrita                                            */
    /* ================================================================ */

    /** Salva BufferedImage em qualquer formato (extensão define o formato) */
    public static void write(BufferedImage img, String path) throws IOException {
        write(img, path, null);
    }

    public static void write(BufferedImage img, String path,
                             WriteOptions opts) throws IOException {
        Result r = toResult(img, path);
        writeResult(r, path, opts);
    }

    /** Salva Result em arquivo */
    public static void write(Result r, String path,
                             WriteOptions opts) throws IOException {
        writeResult(r, path, opts);
    }

    /** Converte para bytes em memória sem salvar em disco */
    public static byte[] toBytes(BufferedImage img, String ext,
                                 WriteOptions opts) throws IOException {
        Result r = toResult(img, ext);
        return toBytesInternal(r, ext, opts);
    }

    public static byte[] toBytes(Result r, String ext,
                                 WriteOptions opts) throws IOException {
        return toBytesInternal(r, ext, opts);
    }

    /* ================================================================ */
    /*  Conversão de formato                                             */
    /* ================================================================ */

    /**
     * Converte entre formatos com alta qualidade.
     *
     * Exemplo: converter WebP com alpha para JPEG sem alpha:
     *   ImageCodec.convert("entrada.webp", "saida.jpg",
     *       ImageCodec.writeOptions().quality(95));
     */
    public static void convert(String inputPath, String outputPath,
                               WriteOptions opts) throws IOException {
        String ext  = ext(outputPath);
        FormatInfo info = formatInfo(ext);
        PixelFormat fmt = (info != null && info.supportsAlpha())
                ? PixelFormat.RGBA32 : PixelFormat.RGB24;
        Result r = readFromFile(inputPath, fmt);
        if (r == null) throw new IOException("Falha ao ler: " + inputPath);
        writeResult(r, outputPath, opts);
    }

    /* ================================================================ */
    /*  Consultas de formato                                             */
    /* ================================================================ */
    public static String        detectFileFormat(String path)  { return detectFormat(path); }
    public static boolean       canReadFormat(String ext)      { return canRead(ext); }
    public static boolean       canWriteFormat(String ext)     { return canWrite(ext); }

    public static FormatInfo formatInfo(String extension) {
        // Tabela espelhada do C (sem precisar de chamada nativa)
        return FORMATS.get(extension.toLowerCase());
    }

    public static Collection<FormatInfo> supportedFormats() {
        return Collections.unmodifiableCollection(FORMATS.values());
    }

    /* ================================================================ */
    /*  Interno                                                          */
    /* ================================================================ */
    private static Result readFromFile(String path, PixelFormat fmt) {
        try {
            byte[] raw = readImage(path, fmt.id);
            return unpack(raw, path, fmt);
        } catch (Exception e) {
            System.err.println("[ImageCodec] Erro ao ler " + path
                    + ": " + e.getMessage());
            return null;
        }
    }

    private static Result readFromMemory(byte[] buf, PixelFormat fmt) {
        try {
            byte[] raw = readImageFromMemory(buf, fmt.id);
            return unpack(raw, "<memory>", fmt);
        } catch (Exception e) {
            System.err.println("[ImageCodec] Erro ao ler buffer: "
                    + e.getMessage());
            return null;
        }
    }

    /** Desempacota o array retornado pelo JNI */
    private static Result unpack(byte[] raw, String src, PixelFormat fmt) {
        if (raw == null || raw.length < 76) return null;

        int    w     = readInt(raw, 0);
        int    h     = readInt(raw, 4);
        int    bpp   = raw[8] & 0xFF;
        int    fmtId = raw[9] & 0xFF;
        String codec = readString(raw, 10, 32);
        String mime  = readString(raw, 42, 32);

        int pixels = w * h * bpp;
        if (w <= 0 || h <= 0 || raw.length < 76 + pixels) return null;

        byte[] pixelData = new byte[pixels];
        System.arraycopy(raw, 76, pixelData, 0, pixels);

        PixelFormat pf = PixelFormat.fromId(fmtId);
        BufferedImage img = toBufferedImage(pixelData, w, h, pf);
        return new Result(img, w, h, pf, codec, mime, src);
    }

    private static void writeResult(Result r, String path,
                                    WriteOptions opts) throws IOException {
        byte[] pixels = pixelsFrom(r.image(), r.format());
        WriteOptions o = opts != null ? opts : writeOptions();
        int ret = writeImage(pixels,
                r.width(), r.height(), r.format().id, r.format().bpp,
                path, o.quality, o.compression, o.lossless ? 1 : 0, o.depth);
        if (ret < 0)
            throw new IOException("Falha ao escrever: " + path);
    }

    private static byte[] toBytesInternal(Result r, String ext,
                                          WriteOptions opts) {
        byte[] pixels = pixelsFrom(r.image(), r.format());
        WriteOptions o = opts != null ? opts : writeOptions();
        return writeImageToMemory(pixels,
                r.width(), r.height(), r.format().id, r.format().bpp,
                ext, o.quality, o.compression, o.lossless ? 1 : 0, o.depth);
    }

    /** Converte BufferedImage → Result temporário para escrita */
    private static Result toResult(BufferedImage img, String hint) {
        PixelFormat fmt = img.getAlphaRaster() != null
                ? PixelFormat.RGBA32 : PixelFormat.RGB24;
        return new Result(img, img.getWidth(), img.getHeight(),
                fmt, "raw", "image/raw", hint);
    }

    /** Extrai pixels de um BufferedImage no formato correto */
    private static byte[] pixelsFrom(BufferedImage img, PixelFormat fmt) {
        int w = img.getWidth(), h = img.getHeight();

        if (fmt == PixelFormat.RGB24) {
            BufferedImage conv = new BufferedImage(
                    w, h, BufferedImage.TYPE_3BYTE_BGR);
            conv.getGraphics().drawImage(img, 0, 0, null);
            byte[] bgr = ((DataBufferByte) conv.getRaster()
                    .getDataBuffer()).getData();
            // BGR → RGB
            byte[] rgb = new byte[bgr.length];
            for (int i = 0, n = w * h; i < n; i++) {
                rgb[i*3]   = bgr[i*3+2];
                rgb[i*3+1] = bgr[i*3+1];
                rgb[i*3+2] = bgr[i*3];
            }
            return rgb;
        }

        if (fmt == PixelFormat.RGBA32) {
            BufferedImage conv = new BufferedImage(
                    w, h, BufferedImage.TYPE_4BYTE_ABGR);
            conv.getGraphics().drawImage(img, 0, 0, null);
            byte[] abgr = ((DataBufferByte) conv.getRaster()
                    .getDataBuffer()).getData();
            // ABGR → RGBA
            byte[] rgba = new byte[abgr.length];
            for (int i = 0, n = w * h; i < n; i++) {
                rgba[i*4]   = abgr[i*4+3]; // R
                rgba[i*4+1] = abgr[i*4+2]; // G
                rgba[i*4+2] = abgr[i*4+1]; // B
                rgba[i*4+3] = abgr[i*4];   // A
            }
            return rgba;
        }

        if (fmt == PixelFormat.GRAY8) {
            BufferedImage conv = new BufferedImage(
                    w, h, BufferedImage.TYPE_BYTE_GRAY);
            conv.getGraphics().drawImage(img, 0, 0, null);
            return ((DataBufferByte) conv.getRaster()
                    .getDataBuffer()).getData();
        }

        // Fallback: RGB24
        return pixelsFrom(img, PixelFormat.RGB24);
    }

    /** Constrói BufferedImage a partir do array de pixels */
    private static BufferedImage toBufferedImage(byte[] pixels,
                                                 int w, int h,
                                                 PixelFormat fmt) {
        switch (fmt) {
            case RGB24 -> {
                BufferedImage img = new BufferedImage(
                        w, h, BufferedImage.TYPE_3BYTE_BGR);
                byte[] dst = ((DataBufferByte) img.getRaster()
                        .getDataBuffer()).getData();
                for (int i = 0, n = w * h; i < n; i++) {
                    dst[i*3]   = pixels[i*3+2]; // B
                    dst[i*3+1] = pixels[i*3+1]; // G
                    dst[i*3+2] = pixels[i*3];   // R
                }
                return img;
            }
            case RGBA32 -> {
                BufferedImage img = new BufferedImage(
                        w, h, BufferedImage.TYPE_4BYTE_ABGR);
                byte[] dst = ((DataBufferByte) img.getRaster()
                        .getDataBuffer()).getData();
                for (int i = 0, n = w * h; i < n; i++) {
                    dst[i*4]   = pixels[i*4+3]; // A
                    dst[i*4+1] = pixels[i*4+2]; // B
                    dst[i*4+2] = pixels[i*4+1]; // G
                    dst[i*4+3] = pixels[i*4];   // R
                }
                return img;
            }
            case GRAY8 -> {
                BufferedImage img = new BufferedImage(
                        w, h, BufferedImage.TYPE_BYTE_GRAY);
                byte[] dst = ((DataBufferByte) img.getRaster()
                        .getDataBuffer()).getData();
                System.arraycopy(pixels, 0, dst, 0, pixels.length);
                return img;
            }
            default -> {
                // Para formatos de alta profundidade, converter para RGB24
                BufferedImage img = new BufferedImage(
                        w, h, BufferedImage.TYPE_3BYTE_BGR);
                byte[] dst = ((DataBufferByte) img.getRaster()
                        .getDataBuffer()).getData();
                int bpp = fmt.bpp;
                for (int i = 0, n = w * h; i < n; i++) {
                    // Pegar byte mais significativo de cada canal
                    dst[i*3]   = pixels[i*bpp + bpp - 1]; // B
                    dst[i*3+1] = pixels[i*bpp + bpp/2];   // G
                    dst[i*3+2] = pixels[i*bpp];            // R
                }
                return img;
            }
        }
    }

    private static int readInt(byte[] b, int off) {
        return ((b[off]&0xFF)<<24)|((b[off+1]&0xFF)<<16)
                |((b[off+2]&0xFF)<<8)|(b[off+3]&0xFF);
    }

    private static String readString(byte[] b, int off, int maxLen) {
        int end = off;
        while (end < off + maxLen && b[end] != 0) end++;
        return new String(b, off, end - off, StandardCharsets.UTF_8);
    }

    private static String ext(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot).toLowerCase() : ".bin";
    }

    /* ── Tabela de formatos (espelho do C, sem chamada nativa) ─────── */
    private static final Map<String, FormatInfo> FORMATS = new LinkedHashMap<>();
    static {
        Object[][] rows = {
                {".png",  "image/png",               "Portable Network Graphics",      true,  true,  true,  false},
                {".jpg",  "image/jpeg",              "JPEG",                           false, false, false, false},
                {".jpeg", "image/jpeg",              "JPEG",                           false, false, false, false},
                {".bmp",  "image/bmp",               "Bitmap",                         true,  false, true,  false},
                {".tiff", "image/tiff",              "Tagged Image File Format",        true,  true,  true,  false},
                {".tif",  "image/tiff",              "Tagged Image File Format",        true,  true,  true,  false},
                {".webp", "image/webp",              "WebP",                           true,  false, true,  true },
                {".gif",  "image/gif",               "GIF",                            true,  false, true,  true },
                {".tga",  "image/x-targa",           "Truevision TGA",                 true,  false, true,  false},
                {".ppm",  "image/x-portable-pixmap", "Portable Pixmap",                false, false, true,  false},
                {".pgm",  "image/x-portable-graymap","Portable Graymap",               false, true,  true,  false},
                {".pbm",  "image/x-portable-bitmap", "Portable Bitmap",                false, false, true,  false},
                {".hdr",  "image/vnd.radiance",      "Radiance HDR",                   false, true,  true,  false},
                {".exr",  "image/x-exr",             "OpenEXR",                        true,  true,  true,  false},
                {".heic", "image/heic",              "HEIC",                           true,  false, false, false},
                {".heif", "image/heif",              "HEIF",                           true,  false, false, false},
                {".avif", "image/avif",              "AVIF",                           true,  true,  true,  false},
                {".jxl",  "image/jxl",              "JPEG XL",                         true,  true,  true,  false},
                {".dds",  "image/vnd.ms-dds",        "DirectDraw Surface",              true,  false, true,  false},
                {".pcx",  "image/x-pcx",             "PCX",                             false, false, true,  false},
                {".sgi",  "image/sgi",               "SGI",                             true,  true,  true,  false},
                {".jp2",  "image/jp2",               "JPEG 2000",                       true,  true,  true,  false},
                {".j2k",  "image/j2k",               "JPEG 2000 Codestream",            true,  true,  true,  false},
                {".ico",  "image/x-icon",            "Windows Icon",                    true,  false, true,  false},
        };
        for (Object[] r : rows) {
            String k = (String)r[0];
            FORMATS.put(k, new FormatInfo(k,(String)r[1],(String)r[2],
                    (boolean)r[3],(boolean)r[4],(boolean)r[5],(boolean)r[6]));
        }
    }


    /* ── Adicionar estes métodos em ImageCodec.java ──────────────────── */

    /* ── Métodos nativos PSD ──────────────────────────────────────────── */
    private static native byte[] readPSD(String path);
    private static native byte[] readPSDFromMemory(byte[] buf);


    /* ================================================================== */
    /*  Resultado específico de PSD com metadados extras                   */
    /* ================================================================== */
    public static final class PSDResult {
        private final BufferedImage image;
        private final int           width;
        private final int           height;
        private final boolean       hasAlpha;
        private final int           depth;       /* bits por canal: 8, 16 ou 32 */
        private final PSDColorMode  colorMode;
        private final String        sourcePath;

        PSDResult(BufferedImage img, int w, int h,
                  boolean alpha, int depth, PSDColorMode mode, String src) {
            this.image      = img;
            this.width      = w;
            this.height     = h;
            this.hasAlpha   = alpha;
            this.depth      = depth;
            this.colorMode  = mode;
            this.sourcePath = src;
        }

        public BufferedImage image()      { return image; }
        public int           width()      { return width; }
        public int           height()     { return height; }
        public boolean       hasAlpha()   { return hasAlpha; }
        public int           depth()      { return depth; }
        public PSDColorMode  colorMode()  { return colorMode; }
        public String        sourcePath() { return sourcePath; }

        /** Converte para Result padrão do ImageCodec para usar nas APIs de escrita */
        public Result toResult() {
            PixelFormat fmt = hasAlpha ? PixelFormat.RGBA32 : PixelFormat.RGB24;
            return new Result(image, width, height, fmt,
                    "psd", "image/vnd.adobe.photoshop", sourcePath);
        }

        @Override
        public String toString() {
            return String.format("PSD[%dx%d %dbit %s%s from '%s']",
                    width, height, depth, colorMode,
                    hasAlpha ? "+alpha" : "",
                    new File(sourcePath).getName());
        }
    }

    /* ── Enum espelhando PSDColorMode do C ───────────────────────────── */
    public enum PSDColorMode {
        BITMAP(0), GRAYSCALE(1), INDEXED(2), RGB(3),
        CMYK(4), MULTICHANNEL(7), DUOTONE(8), LAB(9);

        public final int id;
        PSDColorMode(int id) { this.id = id; }

        static PSDColorMode fromId(int id) {
            for (PSDColorMode m : values()) if (m.id == id) return m;
            return RGB;
        }
    }

    /* ================================================================== */
    /*  API pública PSD                                                     */
    /* ================================================================== */

    /**
     * Lê um arquivo PSD/PSB e retorna PSDResult com metadados completos.
     * Suporta: RGB, CMYK (→RGB), Grayscale, Lab, 8/16/32 bits por canal.
     *
     * Limitação: achatamento da imagem (flatten) — camadas não são separadas.
     * Compressão suportada: Raw e PackBits RLE.
     * Compressão ZIP: salve o PSD sem compressão ZIP no Photoshop.
     */
    public static PSDResult readPSDFile(String path) {
        return readPSDInternal(path, null);
    }

    /**
     * Lê PSD de buffer em memória.
     */
    public static PSDResult readPSDMemory(byte[] buf) {
        return readPSDInternal(null, buf);
    }

    /**
     * Atalho: lê PSD e retorna diretamente o BufferedImage.
     */
//    public static BufferedImage readPSD(String path) {
//        PSDResult r = readPSDFile(path);
//        return r != null ? r.image() : null;
//    }

    /**
     * Lê PSD e converte para outro formato em um passo.
     *
     * Exemplo: converter PSD para PNG mantendo transparência:
     *   ImageCodec.convertPSD("arte.psd", "arte.png", null);
     *
     * Exemplo: converter PSD CMYK para JPEG:
     *   ImageCodec.convertPSD("foto.psd", "foto.jpg",
     *       ImageCodec.writeOptions().quality(95));
     */
    public static void convertPSD(String psdPath, String outPath,
                                  WriteOptions opts) throws IOException {
        PSDResult psd = readPSDFile(psdPath);
        if (psd == null)
            throw new IOException("Falha ao ler PSD: " + psdPath);

        // Se saída suporta alpha e PSD tem alpha, usar RGBA32
        String ext      = ext(outPath);
        FormatInfo info = formatInfo(ext);
        boolean wantAlpha = psd.hasAlpha()
                && info != null && info.supportsAlpha();

        Result r = psd.toResult();
        if (!wantAlpha && psd.hasAlpha()) {
            // Remover alpha: converter RGBA → RGB via novo Result
            BufferedImage noAlpha = flattenAlpha(psd.image());
            r = new Result(noAlpha, psd.width(), psd.height(),
                    PixelFormat.RGB24, "psd",
                    "image/vnd.adobe.photoshop", psdPath);
        }
        writeResult(r, outPath, opts);
    }

    /**
     * Retorna informações de suporte para PSD.
     */
    public static FormatInfo psdFormatInfo() {
        return new FormatInfo(
                ".psd", "image/vnd.adobe.photoshop",
                "Adobe Photoshop Document",
                true,   /* supportsAlpha    */
                true,   /* supports16bit    */
                true,   /* supportsLossless */
                false   /* supportsAnimation */
        );
    }

    /* ================================================================== */
    /*  Interno PSD                                                         */
    /* ================================================================== */
    private static PSDResult readPSDInternal(String path, byte[] memBuf) {
        try {
            byte[] raw = (memBuf != null)
                    ? readPSDFromMemory(memBuf)
                    : readPSD(path);

            if (raw == null || raw.length < 12) {
                System.err.println("[PSD] Array retornado inválido");
                return null;
            }

            /* Desempacotar header de 12 bytes */
            int          w        = readInt(raw, 0);
            int          h        = readInt(raw, 4);
            boolean      hasAlpha = raw[8] != 0;
            int          depth    = ((raw[9] & 0xFF) << 8) | (raw[10] & 0xFF);
            PSDColorMode mode     = PSDColorMode.fromId(raw[11] & 0xFF);

            int outCh    = hasAlpha ? 4 : 3;
            int expected = 12 + w * h * outCh;

            if (w <= 0 || h <= 0 || raw.length < expected) {
                System.err.printf("[PSD] Tamanho inválido: %dx%d "
                                + "(esperado %d bytes, recebido %d)%n",
                        w, h, expected, raw.length);
                return null;
            }

            byte[] pixels = new byte[w * h * outCh];
            System.arraycopy(raw, 12, pixels, 0, pixels.length);

            BufferedImage img = hasAlpha
                    ? rgbaToImage(pixels, w, h)
                    : rgbToImage(pixels, w, h);

            String src = path != null ? path : "<memory>";
            PSDResult result = new PSDResult(img, w, h, hasAlpha, depth, mode, src);

            System.out.printf("[PSD] Lido: %s%n", result);
            return result;

        } catch (Exception e) {
            System.err.println("[PSD] Erro: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /** RGB24 byte[] → BufferedImage TYPE_3BYTE_BGR */
    private static BufferedImage rgbToImage(byte[] rgb, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        byte[] dst = ((DataBufferByte) img.getRaster()
                .getDataBuffer()).getData();
        for (int i = 0, n = w * h; i < n; i++) {
            dst[i*3]   = rgb[i*3+2]; // B
            dst[i*3+1] = rgb[i*3+1]; // G
            dst[i*3+2] = rgb[i*3];   // R
        }
        return img;
    }

    /** RGBA32 byte[] → BufferedImage TYPE_4BYTE_ABGR */
    private static BufferedImage rgbaToImage(byte[] rgba, int w, int h) {
        BufferedImage img = new BufferedImage(
                w, h, BufferedImage.TYPE_4BYTE_ABGR);
        byte[] dst = ((DataBufferByte) img.getRaster()
                .getDataBuffer()).getData();
        for (int i = 0, n = w * h; i < n; i++) {
            dst[i*4]   = rgba[i*4+3]; // A
            dst[i*4+1] = rgba[i*4+2]; // B
            dst[i*4+2] = rgba[i*4+1]; // G
            dst[i*4+3] = rgba[i*4];   // R
        }
        return img;
    }

    /**
     * Achata alpha sobre fundo branco (para formatos sem transparência como JPEG).
     */
    private static BufferedImage flattenAlpha(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        java.awt.Graphics2D g = out.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }
}