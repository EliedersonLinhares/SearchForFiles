package com.esl.searchforfiles.Video;


import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FFmpegBridge {

    static { loadNativeLibrary(); }

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
                try (InputStream in = FFmpegBridge.class
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
            try (InputStream in = FFmpegBridge.class
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
    public native long    openVideo(String filepath);
    public native void    closeVideo(long ctx);

    // ── Decode unificado ───────────────────────────────────────────
    /** Lê pacotes do FFmpeg e enfileira frames de vídeo e áudio */
    public native void     grabNextFrames(long ctx);

    /** Retira um frame de vídeo da fila (RGB24), ou null se vazia */
    public native byte[]   pollVideoFrame(long ctx);

    /** Retira um frame de áudio da fila (PCM S16 LE), ou null se vazia */
    public native byte[]   pollAudioFrame(long ctx);

    /** true quando EOF e ambas as filas estão vazias */
    public native boolean  isEOF(long ctx);

    // ── Seek ──────────────────────────────────────────────────────
    public native void     seekToSeconds(long ctx, double seconds);

    // ── Metadados ─────────────────────────────────────────────────
    public native int      getWidth(long ctx);
    public native int      getHeight(long ctx);
    public native double   getFrameRate(long ctx);
    public native long     getTotalFrames(long ctx);
    public native int      getSampleRate(long ctx);
    public native int      getAudioChannels(long ctx);
    public native int      getTotalAudioStreams(long ctx);
    public native String   getVideoCodecName(long ctx);
}