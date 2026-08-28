package com.esl.searchforfiles.preview;

import com.esl.searchforfiles.Video.FFmpegBridge;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.List;

public class VideoPreviewPanel extends JPanel {

    private final FFmpegBridge bridge = new FFmpegBridge();
    private final java.util.ArrayDeque<double[]> pendingSubStarts = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<String> pendingSubTexts = new java.util.ArrayDeque<>();
    private long videoCtx = 0L;
    private boolean isPlaying = false;
    private boolean isMuted = false;
    private float volume = 1.0f; // 0.0f a 1.0f
    private BufferedImage currentFrame;
    private byte[] frameTargetBuffer; // Cache para cópia rápida de pixels
    private SourceDataLine audioLine;
    private Thread playerThread;
    // Callbacks para atualizar a interface externa (Sliders e Labels)
    private java.util.function.Consumer<Double> timeUpdateCallback;
    private Runnable onPlaybackFinished;
    private int videoW, videoH;
    private double frameRate = 30.0;
    private double currentSeconds = 0.0;
    private boolean isSeeking = false;
    private volatile boolean isCurrentlySeeking = false;
    private double audioStartTimeOffset = 0.0;
    private volatile String currentSubtitleText = null;
    private volatile double subtitleEndTime = -1;
    private volatile boolean subtitlesEnabled = false;
    private Double subtitleClockOffset = null; // calibrado na primeira legenda exibida

    // Ajuste este valor em segundos se notar que precisa de mais ou menos correção
// 0.15 significa que o áudio físico está saindo 150ms atrás do relógio lógico
    private static final double AUDIO_LATENCY_OFFSET = 0.30;


    public VideoPreviewPanel() {
        setBackground(Color.BLACK);
        setOpaque(true);
    }

    public void setTimeUpdateCallback(java.util.function.Consumer<Double> callback) {
        this.timeUpdateCallback = callback;
    }

    public void setOnPlaybackFinished(Runnable callback) {
        this.onPlaybackFinished = callback;
    }

    public synchronized boolean open(String filepath) {
        stop(); // Garante o fechamento de arquivos anteriores

        videoCtx = bridge.openVideo(filepath);
        if (videoCtx == 0L) {
            System.err.println("[Player] Falha ao abrir o vídeo: " + filepath);
            return false;
        }

        videoW = bridge.getWidth(videoCtx);
        videoH = bridge.getHeight(videoCtx);
        frameRate = bridge.getFrameRate(videoCtx);
        if (frameRate <= 0) frameRate = 30.0;

        // ── ÁUDIO PURO: sem stream de vídeo válido ──
        boolean hasVideo = (videoW > 0 && videoH > 0);

        if (hasVideo) {
            // Inicializa o BufferedImage otimizado compartilhando o array de bytes interno
            currentFrame = new BufferedImage(videoW, videoH, BufferedImage.TYPE_3BYTE_BGR);
            frameTargetBuffer = ((DataBufferByte) currentFrame.getRaster().getDataBuffer()).getData();
        } else {
            // Sem stream de vídeo: não há frame para desenhar, só áudio
            currentFrame = null;
            frameTargetBuffer = null;
        }

        initAudio();
        currentSeconds = 0.0;

        // Só define tamanho preferido baseado no vídeo se ele existir de fato;
        // caso contrário, mantém o tamanho atual (o VideoPlayerFrame já define
        // 680x250 para áudio puro antes/depois do open())
        if (hasVideo) {
            setPreferredSize(new Dimension(videoW, videoH));
            revalidate();
        }

        repaint();
        return true;
    }


    private void initAudio() {
        int sampleRate = bridge.getSampleRate(videoCtx);
        // Força 2 canais no Java, combinando com o Downmix que o FFmpeg fará no C
        int channels = 2;

        if (sampleRate > 0) {
            try {
                AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                audioLine = (SourceDataLine) AudioSystem.getLine(info);
                audioLine.open(format);
                audioLine.start();
                setVolume(volume);
            } catch (LineUnavailableException e) {
                System.err.println("[Player] Linha de áudio indisponível: " + e.getMessage());
                audioLine = null;
            }
        }
    }

    public synchronized void play() {
        if (videoCtx == 0L || isPlaying) return;
        isPlaying = true;

        if (audioLine != null) audioLine.start();

        playerThread = new Thread(this::playbackLoop, "VideoPlayer-Thread");
        playerThread.setDaemon(true);
        playerThread.start();
    }

    public synchronized void pause() {
        isPlaying = false;
        if (audioLine != null) audioLine.stop();
    }

    public synchronized void stop() {
        isPlaying = false;
        audioStartTimeOffset = 0.0;
        if (playerThread != null) {
            playerThread.interrupt();
            try {
                playerThread.join(500);
            } catch (InterruptedException ignored) {
            }
            playerThread = null;
        }

        if (audioLine != null) {
            audioLine.stop();
            audioLine.flush();
            audioLine.close();
            //audioLine.clear();
        }

        if (videoCtx != 0L) {
            bridge.closeVideo(videoCtx);
            videoCtx = 0L;
        }

        currentFrame = null;
        currentSeconds = 0.0;
        repaint();
    }

    public void seek(double seconds) {
        if (videoCtx == 0L || isCurrentlySeeking) return;

        synchronized (this) {
            try {
                isCurrentlySeeking = true;
                isSeeking = true;

                bridge.seekToSeconds(videoCtx, seconds);
                currentSeconds = seconds;
                audioStartTimeOffset = seconds;

                // Legendas pendentes agora são inválidas, e o offset precisa recalibrar
                pendingSubStarts.clear();
                pendingSubTexts.clear();
                currentSubtitleText = null;
                subtitleClockOffset = null;

                if (audioLine != null) {
                    audioLine.stop();
                    audioLine.flush();
                    if (isPlaying) audioLine.start();
                }

                bridge.grabNextFrames(videoCtx);
                processNextVideoFrame();
                repaint();

            } catch (Exception e) {
                System.err.println("[Player] Erro durante o Seek: " + e.getMessage());
            } finally {
                isSeeking = false;
                isCurrentlySeeking = false;
            }
        }
    }

    public void setVolume(float vol) {
        this.volume = Math.max(0.0f, Math.min(1.0f, vol));
        if (audioLine != null && audioLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gainControl = (FloatControl) audioLine.getControl(FloatControl.Type.MASTER_GAIN);
            if (isMuted || volume == 0.0f) {
                gainControl.setValue(gainControl.getMinimum());
            } else {
                // Nova fórmula com curva logarítmica mais agressiva para compensar o downmix
                float dB = (float) (Math.log(volume) / Math.log(10.0) * 20.0);

                // Dá um "boost" de até +6 decibéis se o usuário colocar o slider no máximo
                if (volume > 0.7f) {
                    dB += (volume - 0.7f) * 20.0f;
                }

                gainControl.setValue(Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB)));
            }
        }
    }

    public void setMute(boolean mute) {
        this.isMuted = mute;
        setVolume(volume);
    }

    private void playbackLoop() {
        long delayMillis = (long) (1000.0 / frameRate);

        // Armazena se o arquivo possui uma trilha de áudio ativa inicializada
        boolean hasAudio = (audioLine != null);

        while (isPlaying && !Thread.currentThread().isInterrupted()) {
            if (videoCtx == 0L || isSeeking || isCurrentlySeeking) {
                sleep(15);
                continue;
            }

            if (bridge.isEOF(videoCtx)) {
                isPlaying = false;
                if (onPlaybackFinished != null) SwingUtilities.invokeLater(onPlaybackFinished);
                break;
            }

            long startTime = System.currentTimeMillis();

            // 1. Alimenta as filas nativas da DLL em C
            bridge.grabNextFrames(videoCtx);

            // 2. Despacha os bytes de áudio (Esta função consome tempo dependendo do buffer do Java Sound)
            processAudioFrames();

            processSubtitleFrames();

            // 3. Processa o frame gráfico de vídeo
            boolean frameUpdated = processNextVideoFrame();

            // ── CORREÇÃO DO RELÓGIO NATIVO ──
//            if (hasAudio && audioLine.isActive()) {
//                // Obtém quantos segundos de áudio a placa de som REALMENTE tocou desde que deu Play
//                double playedAudioSeconds = audioLine.getMicrosecondPosition() / 1_000_000.0;
//
//                // O tempo atual é o ponto onde o áudio começou (ou após o seek) + o que a placa já tocou
//                currentSeconds = audioStartTimeOffset + playedAudioSeconds;
//            } else if (frameUpdated) {
//                // Fallback caso seja um vídeo mudo (sem áudio): avança baseado na taxa de quadros (FPS)
//                currentSeconds += (1.0 / frameRate);
//            }

            // ── CORREÇÃO DO RELÓGIO NATIVO (COM OFFSET FIXO) ──
            if (hasAudio && audioLine.isActive()) {
                // Obtém quantos segundos de áudio a placa processou logicamente
                double playedAudioSeconds = audioLine.getMicrosecondPosition() / 1_000_000.0;

                // Subtrai o offset fixo de latência para sincronizar a imagem com o som real
                currentSeconds = audioStartTimeOffset + playedAudioSeconds - AUDIO_LATENCY_OFFSET;

                // Salvaguarda para o relógio nunca ficar abaixo do ponto inicial (especialmente no início ou após Seek)
                if (currentSeconds < audioStartTimeOffset) {
                    currentSeconds = audioStartTimeOffset;
                }
            } else if (frameUpdated) {
                // Fallback caso seja um vídeo mudo (sem áudio)
                currentSeconds += (1.0 / frameRate);
            }

            // Dispara o callback para atualizar o slider na interface principal com precisão absoluta
            if (timeUpdateCallback != null) {
                SwingUtilities.invokeLater(() -> timeUpdateCallback.accept(currentSeconds));
            }

            // Controle dinâmico de espera da Thread para não estourar 100% de uso de CPU
            long elapsedTime = System.currentTimeMillis() - startTime;
            long sleepTime = delayMillis - elapsedTime;
            if (sleepTime > 0) {
                sleep(sleepTime);
            } else {
                sleep(2); // Se o processamento de áudio travou por buffering, cede tempo mínimo para o SO
            }
        }
    }

    private void processSubtitleFrames() {
        String raw;
        while ((raw = bridge.pollSubtitleFrame(videoCtx)) != null) {
            if (!subtitlesEnabled) continue;

            String[] parts = raw.split(";", 3);
            if (parts.length == 3) {
                try {
                    double start = Double.parseDouble(parts[0]);
                    double end = Double.parseDouble(parts[1]);
                    pendingSubStarts.add(new double[]{start, end});
                    pendingSubTexts.add(parts[2]);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        boolean changed = false;

        // Só ativa a legenda quando a versão AJUSTADA do start já foi alcançada
        while (!pendingSubStarts.isEmpty()) {
            double[] times = pendingSubStarts.peek();
            double rawStart = times[0];
            double rawEnd = times[1];

            double offset = (subtitleClockOffset != null) ? subtitleClockOffset : 0.0;
            double adjStart = rawStart + offset;

            if (adjStart > currentSeconds) break; // ainda não é hora desta legenda

            pendingSubStarts.poll();
            String text = pendingSubTexts.poll();

            // Calibra o offset na PRIMEIRA legenda exibida (ou se ainda não calibrado)
            if (subtitleClockOffset == null) {
                subtitleClockOffset = currentSeconds - rawStart;
                offset = subtitleClockOffset;
            }

            double adjEnd = rawEnd + offset;

            // Salvaguarda: se por jitter a legenda ainda nascer "expirada", garante um mínimo curto
            if (adjEnd <= currentSeconds) {
                adjEnd = currentSeconds + 0.8;
            }

            currentSubtitleText = text;
            subtitleEndTime = adjEnd;
            changed = true;
        }

        if (currentSubtitleText != null && currentSeconds > subtitleEndTime) {
            currentSubtitleText = null;
            changed = true;
        }

        if (changed) repaint();
    }

    private boolean processNextVideoFrame() {
        if (frameTargetBuffer == null) {
            // Áudio puro: descarta qualquer frame de vídeo "fantasma" que porventura
            // ainda venha da fila nativa, sem tentar desenhar nada
            bridge.pollVideoFrame(videoCtx);
            return false;
        }

        byte[] rawRgb = bridge.pollVideoFrame(videoCtx);
        if (rawRgb == null) return false;

        synchronized (this) {
            if (frameTargetBuffer.length == rawRgb.length) {
                for (int i = 0; i < rawRgb.length; i += 3) {
                    frameTargetBuffer[i] = rawRgb[i + 2]; // B
                    frameTargetBuffer[i + 1] = rawRgb[i + 1]; // G
                    frameTargetBuffer[i + 2] = rawRgb[i];     // R
                }
            }
        }
        repaint();
        return true;
    }

    private void processAudioFrames() {
        if (isMuted) {
            // Se estiver mutado, consome os frames para limpar as filas nativas sem emitir som
            while (bridge.pollAudioFrame(videoCtx) != null) ;
            return;
        }

        byte[] pcmData;
        while ((pcmData = bridge.pollAudioFrame(videoCtx)) != null) {
            if (audioLine != null && audioLine.isOpen()) {
                audioLine.write(pcmData, 0, pcmData.length);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        synchronized (this) {
            if (currentFrame != null && videoW > 0 && videoH > 0) {
                int panelW = getWidth();
                int panelH = getHeight();
                double scale = Math.min((double) panelW / videoW, (double) panelH / videoH);
                int targetW = (int) (videoW * scale);
                int targetH = (int) (videoH * scale);
                int x = (panelW - targetW) / 2;
                int y = (panelH - targetH) / 2;

                g.drawImage(currentFrame, x, y, targetW, targetH, null);
                if (subtitlesEnabled && currentSubtitleText != null) {
                    drawSubtitleOverlay((Graphics2D) g);
                }
            }
            // Se for áudio puro (currentFrame == null), simplesmente não desenha nada
            // — o painel fica preto (setBackground(Color.BLACK) já cuida disso)
        }
    }

    private void drawSubtitleOverlay(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 30f));
        FontMetrics fm = g2.getFontMetrics();

        String[] lines = currentSubtitleText.split("\\\\N|\n");
        int lineHeight = fm.getHeight();
        int y = getHeight() - 40 - lineHeight * lines.length;

        for (String line : lines) {
            int textW = fm.stringWidth(line);
            int x = (getWidth() - textW) / 2;
            g2.setColor(new Color(0, 0, 0, 160));
            g2.fillRoundRect(x - 8, y - fm.getAscent(), textW + 16, lineHeight, 8, 8);
            g2.setColor(Color.YELLOW);
            g2.drawString(line, x, y);
            y += lineHeight;
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public long getVideoCtx() {
        return this.videoCtx;
    }

    public double getCurrentSeconds() {
        return this.currentSeconds;
    }

    public List<String> getAudioTracks() {
        List<String> list = new ArrayList<>();
        int count = bridge.getAudioTrackCount(videoCtx);
        for (int i = 0; i < count; i++) list.add(bridge.getAudioTrackLanguage(videoCtx, i));
        return list;
    }

    public List<String> getSubtitleTracks() {
        List<String> list = new ArrayList<>();
        int count = bridge.getSubtitleTrackCount(videoCtx);
        for (int i = 0; i < count; i++) list.add(bridge.getSubtitleTrackLanguage(videoCtx, i));
        return list;
    }

    public void setAudioTrack(int index) {
        if (videoCtx == 0L) return;
        bridge.setAudioTrack(videoCtx, index);
    }

    public void setSubtitleTrack(int index) {
        if (videoCtx == 0L) return;
        subtitlesEnabled = (index >= 0);
        currentSubtitleText = null;
        pendingSubStarts.clear();
        pendingSubTexts.clear();
        subtitleClockOffset = null; // recalibra na próxima legenda que chegar
        bridge.setSubtitleTrack(videoCtx, index);
    }
}
