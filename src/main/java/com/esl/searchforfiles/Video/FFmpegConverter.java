package com.esl.searchforfiles.Video;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FFmpegConverter {

    static { loadNativeLibrary(); } // reaproveite o mesmo método do FFmpegBridge

    private static void loadNativeLibrary() {
        try {
            String os     = System.getProperty("os.name").toLowerCase();
            boolean isWin = os.contains("win");
            String folder = isWin ? "windows-x86-64" :
                    os.contains("mac") ? "darwin-x86-64" : "linux-x86-64";

            Path tempDir = Files.createTempDirectory("ffmpeg_native");
            tempDir.toFile().deleteOnExit();

            String[] deps = isWin ? new String[]{
                    "avutil-60.dll", "swresample-6.dll", "swscale-9.dll",
                    "avcodec-62.dll", "avformat-62.dll"
            } : new String[]{
                    "libavutil.so", "libswresample.so", "libswscale.so",
                    "libavcodec.so", "libavformat.so"
            };

            for (String dep : deps) {
                try (InputStream in = FFmpegConverter.class
                        .getResourceAsStream("/native/" + folder + "/" + dep)) {
                    if (in != null) {
                        Path dest = tempDir.resolve(dep);
                        Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                        dest.toFile().deleteOnExit();
                        System.load(dest.toString());
                    }
                }
            }

            String libName = isWin ? "ffmpeg_bridge.dll" : "libffmpeg_bridge.so";
            try (InputStream in = FFmpegConverter.class
                    .getResourceAsStream("/native/" + folder + "/" + libName)) {
                if (in == null) throw new RuntimeException("Lib não encontrada");
                Path dest = tempDir.resolve(libName);
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                dest.toFile().deleteOnExit();
                System.load(dest.toString());
            }

            //  System.out.println("FFmpegBridge carregada com sucesso!");

        } catch (Exception e) {
            throw new RuntimeException("Falha ao carregar lib nativa", e);
        }
    }

    // ── Ciclo de vida ──────────────────────────────────────────────
    /**
     * Abre a conversão com as opções escolhidas.
     * @param videoCodec "copy", "libx264", "libx265", "libvpx-vp9", "mpeg4"
     * @param audioCodec "copy", "aac", "libmp3lame", "libopus"
     * @param outWidth   0 = manter original
     * @param outHeight  0 = manter original
     * @param videoBitrate bits/s, 0 = usar crf/padrão
     * @param audioBitrate bits/s, 0 = padrão
     * @param crf        0-51 (x264/x265), -1 = não usar
     * @param preset     "ultrafast".."veryslow", null/vazio = médio
     */
    public native long openConverter(String inputPath, String outputPath,
                                     String videoCodec, String audioCodec,
                                     int outWidth, int outHeight,
                                     long videoBitrate, long audioBitrate,
                                     int crf, String preset,int maxThreads);

    /** Processa um pacote; retorna false quando terminou (EOF + flush). */
    public native boolean processNextPacket(long ctx);

    public native double  getProgress(long ctx);      // 0.0 a 1.0
    public native boolean isFinished(long ctx);
    public native boolean hasError(long ctx);
    public native String  getErrorMessage(long ctx);

    public native void closeConverter(long ctx);
}