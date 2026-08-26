package com.esl.searchforfiles.preview;

import com.esl.searchforfiles.Video.FFmpegBridge;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

public class VideoPreviewPanel extends JPanel {

    private final FFmpegBridge bridge = new FFmpegBridge();
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

//    public synchronized boolean open(String filepath) {
//        stop(); // Garante o fechamento de arquivos anteriores
//
//        videoCtx = bridge.openVideo(filepath);
//        if (videoCtx == 0L) {
//            System.err.println("[Player] Falha ao abrir o vídeo: " + filepath);
//            return false;
//        }
//
//        videoW = bridge.getWidth(videoCtx);
//        videoH = bridge.getHeight(videoCtx);
//        frameRate = bridge.getFrameRate(videoCtx);
//        if (frameRate <= 0) frameRate = 30.0;
//
//        // Inicializa o BufferedImage otimizado compartilhando o array de bytes interno
//        currentFrame = new BufferedImage(videoW, videoH, BufferedImage.TYPE_3BYTE_BGR);
//        frameTargetBuffer = ((DataBufferByte) currentFrame.getRaster().getDataBuffer()).getData();
//
//        initAudio();
//        currentSeconds = 0.0;
//
//        setPreferredSize(new Dimension(videoW, videoH));
//        revalidate();
//        repaint();
//        return true;
//    }
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
        // Se já houver um comando de seek rodando na DLL, ignora cliques repetidos muito rápidos
        if (videoCtx == 0L || isCurrentlySeeking) return;

        // Sincroniza no objeto do painel para travar o playbackLoop enquanto limpa os buffers na DLL
        synchronized (this) {
            try {
                isCurrentlySeeking = true;
                isSeeking = true; // Flag existente do seu loop

                // Executa o pulo de tempo de forma segura na biblioteca C
                bridge.seekToSeconds(videoCtx, seconds);
                currentSeconds = seconds;
                audioStartTimeOffset = seconds;

                if (audioLine != null) {
                    audioLine.stop();
                    audioLine.flush(); // Limpa resíduos de áudio do ponto anterior
                    if (isPlaying) audioLine.start();
                }

                // Descarrega as filas de frames antigos e puxa o primeiro frame do ponto novo
                bridge.grabNextFrames(videoCtx);
                processNextVideoFrame();
                repaint();

            } catch (Exception e) {
                System.err.println("[Player] Erro durante o Seek: " + e.getMessage());
            } finally {
                // Libera a trava para aceitar o próximo clique no slider
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

    //    private void playbackLoop() {
//        long delayMillis = (long) (1000.0 / frameRate);
//        while (isPlaying && !Thread.currentThread().isInterrupted()) {
//            // Se o slider estiver operando, cede tempo de processamento e aguarda
//            if (videoCtx == 0L || isSeeking || isCurrentlySeeking) {
//                sleep(15);
//                continue;
//            }
//
//            if (bridge.isEOF(videoCtx)) {
//                isPlaying = false;
//                if (onPlaybackFinished != null) SwingUtilities.invokeLater(onPlaybackFinished);
//                break;
//            }
//
//            long startTime = System.currentTimeMillis();
//
//            // Alimenta as filas internas do seu módulo C
//            bridge.grabNextFrames(videoCtx);
//
//            // Processa o áudio concorrente
//            processAudioFrames();
//
//            // Processa e atualiza o frame gráfico de vídeo
//            boolean frameUpdated = processNextVideoFrame();
//
//            if (frameUpdated) {
//                currentSeconds += (1.0 / frameRate);
//                if (timeUpdateCallback != null) {
//                    SwingUtilities.invokeLater(() -> timeUpdateCallback.accept(currentSeconds));
//                }
//            }
//
//            long elapsedTime = System.currentTimeMillis() - startTime;
//            long sleepTime = delayMillis - elapsedTime;
//            if (sleepTime > 0) {
//                sleep(sleepTime);
//            }
//        }
//    }
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

            // 3. Processa o frame gráfico de vídeo
            boolean frameUpdated = processNextVideoFrame();

            // ── CORREÇÃO DO RELÓGIO NATIVO ──
            if (hasAudio && audioLine.isActive()) {
                // Obtém quantos segundos de áudio a placa de som REALMENTE tocou desde que deu Play
                double playedAudioSeconds = audioLine.getMicrosecondPosition() / 1_000_000.0;

                // O tempo atual é o ponto onde o áudio começou (ou após o seek) + o que a placa já tocou
                currentSeconds = audioStartTimeOffset + playedAudioSeconds;
            } else if (frameUpdated) {
                // Fallback caso seja um vídeo mudo (sem áudio): avança baseado na taxa de quadros (FPS)
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

//    private boolean processNextVideoFrame() {
//        byte[] rawRgb = bridge.pollVideoFrame(videoCtx);
//        if (rawRgb == null) return false;
//
//        // Converte RGB24 (FFmpeg) para BGR (estrutura de memória do BufferedImage padrão no Windows)
//        // Isso evita overheads de renderização e redesenho
//        synchronized (this) {
//            if (frameTargetBuffer != null && frameTargetBuffer.length == rawRgb.length) {
//                for (int i = 0; i < rawRgb.length; i += 3) {
//                    frameTargetBuffer[i] = rawRgb[i + 2]; // B
//                    frameTargetBuffer[i + 1] = rawRgb[i + 1]; // G
//                    frameTargetBuffer[i + 2] = rawRgb[i];     // R
//                }
//            }
//        }
//        repaint();
//        return true;
//    }
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

//    @Override
//    protected void paintComponent(Graphics g) {
//        super.paintComponent(g);
//        synchronized (this) {
//            if (currentFrame != null) {
//                // Desenha mantendo o aspecto centralizado na janela
//                int panelW = getWidth();
//                int panelH = getHeight();
//                double scale = Math.min((double) panelW / videoW, (double) panelH / videoH);
//                int targetW = (int) (videoW * scale);
//                int targetH = (int) (videoH * scale);
//                int x = (panelW - targetW) / 2;
//                int y = (panelH - targetH) / 2;
//
//                g.drawImage(currentFrame, x, y, targetW, targetH, null);
//            }
//        }
//    }
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
        }
        // Se for áudio puro (currentFrame == null), simplesmente não desenha nada
        // — o painel fica preto (setBackground(Color.BLACK) já cuida disso)
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
}
