package com.esl.searchforfiles.preview;


import com.esl.searchforfiles.Video.FFmpegBridge;
import com.esl.searchforfiles.configuration.UIConfig;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

public class VideoPlayerFrame extends JFrame {

    private final VideoPreviewPanel playerPanel;
    private final JSlider timeSlider;
    private final JSlider volumeSlider;
    private final JButton btnPlayPause;
    private final JButton btnRewind;
    private final JButton btnFastForward;
    private final JButton btnStop;
    private final JToggleButton btnMute;
    private final JLabel lblTime;

    private final File currentVideoFile;
    // Variáveis de controle para restaurar a janela antes de entrar em tela cheia
    private int prevW = 800, prevH = 600, prevX = 0, prevY = 0;
    private boolean isFullscreen = false;
    // Adicione no topo da classe VideoPlayerFrame junto com os outros componentes:
    private final JPanel controlPanel;
    private boolean videoHasFinished = false;

    private boolean sliderIsChanging = false;
    private double videoDuration = 120.0; // Fallback padrao

    final javax.swing.Timer clickTimer = new javax.swing.Timer(250, null);


    public VideoPlayerFrame(Window owner, File videoFile) {
        this.currentVideoFile = videoFile;
        setTitle("Visualizador de Vídeo — " + videoFile.getName());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        if (owner != null) owner.setEnabled(false);

        playerPanel = new VideoPreviewPanel();
        add(playerPanel, BorderLayout.CENTER);

        // Barra Inferior de Controles
        controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Painel do Slider de Linha de Tempo
        JPanel timePanel = new JPanel(new BorderLayout(5, 0));
        timeSlider = new JSlider(0, 1000, 0);
        lblTime = new JLabel("00:00 / 00:00");
        timePanel.add(timeSlider, BorderLayout.CENTER);
        timePanel.add(lblTime, BorderLayout.EAST);

        // Painel dos Botões operacionais
        JPanel buttonPanel = new JPanel(new BorderLayout());
        JPanel leftButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnPlayPause = new JButton("Pause");
        btnRewind = new JButton("<");
        btnFastForward = new JButton(">");
        btnStop = new JButton("Stop");
        btnMute = new JToggleButton("Mute");

        volumeSlider = new JSlider(0, 100, 80);
        volumeSlider.setPreferredSize(new Dimension(120, 20));
        JLabel lblVolume = new JLabel("Vol:");


        JLabel lblVideoInfo = new JLabel("");
        lblVideoInfo.setForeground(Color.GRAY); // Cor cinza discreta para os metadados
        lblVideoInfo.setFont(UIConfig.FONT_SMALL);

        btnPlayPause.setFont(UIConfig.FONT_DEFAULT);
        btnRewind.setFont(UIConfig.FONT_DEFAULT);
        btnFastForward.setFont(UIConfig.FONT_DEFAULT);
        btnStop.setFont(UIConfig.FONT_DEFAULT);
        btnMute.setFont(UIConfig.FONT_DEFAULT);
        lblVolume.setFont(UIConfig.FONT_SMALL);
        lblTime.setFont(UIConfig.FONT_DEFAULT);


        leftButtonsPanel.add(btnPlayPause);
        leftButtonsPanel.add(btnRewind);
        leftButtonsPanel.add(btnFastForward);
        leftButtonsPanel.add(btnStop);
        leftButtonsPanel.add(btnMute);
        leftButtonsPanel.add(lblVolume);
        leftButtonsPanel.add(volumeSlider);

        JPanel rightInfoPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 2));
        rightInfoPanel.add(lblVideoInfo);

        buttonPanel.add(leftButtonsPanel, BorderLayout.WEST);
        buttonPanel.add(rightInfoPanel, BorderLayout.EAST);

        controlPanel.add(timePanel);
        controlPanel.add(Box.createVerticalStrut(5));
        controlPanel.add(buttonPanel);
        add(controlPanel, BorderLayout.SOUTH);

        // Lógicas e Listeners de Interação
        setupListeners();

        // Configura o atalho de teclado para a Barra de Espaço
        setupKeyboardShortcuts();

//        // Abre e inicializa o arquivo de vídeo
//        if (playerPanel.open(videoFile.getAbsolutePath())) {
//            // Estima a duração total aproximada baseando-se nas propriedades nativas extraídas
//            FFmpegBridge tempBridge = new FFmpegBridge();
//            long nativeCtx = tempBridge.openVideo(videoFile.getAbsolutePath());
//            int nativeW = 800; // Resoluções padrão de fallback caso a lib falhe
//            int nativeH = 600;
//
//            if (nativeCtx != 0L) {
//                long totalFrames = tempBridge.getTotalFrames(nativeCtx);
//                double fps = tempBridge.getFrameRate(nativeCtx);
//                nativeW = tempBridge.getWidth(nativeCtx);
//                nativeH = tempBridge.getHeight(nativeCtx);
//
//                if (totalFrames > 0 && fps > 0) {
//                    videoDuration = (double) totalFrames / fps;
//                }
//                tempBridge.closeVideo(nativeCtx);
//            }
//
//            updateTimeLabel(0.0);
//            playerPanel.setVolume(0.8f);
//
//            // ── REDIMENSIONAMENTO AUTOMÁTICO ──
//            // Calcula o tamanho necessário somando a altura do painel de controle inferior
//            playerPanel.setPreferredSize(new Dimension(nativeW, nativeH));
//            pack(); // Ajusta a janela perfeitamente ao tamanho dos componentes internos

        if (playerPanel.open(videoFile.getAbsolutePath())) {
            FFmpegBridge tempBridge = new FFmpegBridge();
            long nativeCtx = tempBridge.openVideo(videoFile.getAbsolutePath());
            int nativeW = 800;
            int nativeH = 600;
            double fps = 0.0;
            String codecName = "Desconhecido";
            String containerFormat = "Desconhecido";

            if (nativeCtx != 0L) {
                nativeW = tempBridge.getWidth(nativeCtx);
                nativeH = tempBridge.getHeight(nativeCtx);
                fps = tempBridge.getFrameRate(nativeCtx);
                codecName = tempBridge.getVideoCodecName(nativeCtx).toUpperCase();
                containerFormat = tempBridge.getContainerFormatName(nativeCtx).toUpperCase();

                // Simplifica nomes longos de formatos retornados pelo FFmpeg
                if (containerFormat.contains("MATROSKA")) containerFormat = "MKV";
                if (containerFormat.contains("MOV,MP4")) containerFormat = "MP4";

                long totalFrames = tempBridge.getTotalFrames(nativeCtx);
                double duration = tempBridge.getDurationInSeconds(nativeCtx);

                if (duration > 0) {
                    videoDuration = duration;
                } else if (totalFrames > 0 && fps > 0) {
                    videoDuration = (double) totalFrames / fps;
                }
                tempBridge.closeVideo(nativeCtx);
            }

            // ── CÁLCULO DO TAMANHO DO ARQUIVO EM MB ──
            // Obtém o tamanho em bytes e converte para Megabytes (1 MB = 1024 * 1024 bytes)
            double fileSizeInMB = (double) videoFile.length() / (1024 * 1024);

            // ── ATUALIZAÇÃO DO LABEL COM METADADOS EXPANDIDOS ──
            // Formata o FPS para exibir sem casas decimais supérfluas (ex: 23.97 -> 24 ou mantém se preferir %.2f)
            lblVideoInfo.setText(String.format("%d×%d | %.0f FPS | Codec: %s | Formato: %s | %.1f MB",
                    nativeW, nativeH, fps, codecName, containerFormat, fileSizeInMB));

            updateTimeLabel(0.0);
            playerPanel.setVolume(0.8f);

            // Configurações padrão de redimensionamento e exibição da janela
            playerPanel.setPreferredSize(new Dimension(nativeW, nativeH));
            pack();

            // ── SOLUÇÃO DO TAMANHO MÍNIMO ──
            // Define uma barreira física na janela. O usuário ou vídeos verticais nunca
            // conseguirão esmagar a tela abaixo de 680 pixels de largura e 400 de altura.
            // Isso garante espaço suficiente para todas as suas Labels e Botões operacionais.
            setMinimumSize(new Dimension(850, 400));


            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            if (getWidth() > screenSize.width || getHeight() > screenSize.height) {
                setSize(Math.min(nativeW, (int)(screenSize.width * 0.8)),
                        Math.min(nativeH + 80, (int)(screenSize.height * 0.8)));
            }
            // Se o vídeo for gigante (ex: 4K), restringe a 80% do monitor para não estourar a tela
//            int idealW = Math.max(680, Math.min(nativeW, (int)(screenSize.width * 0.8)));
//            int idealH = Math.max(400, Math.min(nativeH + 80, (int)(screenSize.height * 0.8)));
//            setSize(idealW, idealH);


            setLocationRelativeTo(null);
            playerPanel.play();
        } else {
            // Caso falhe ao abrir, define um tamanho padrão e exibe aviso
            setSize(800, 600);
            setLocationRelativeTo(null);
            btnPlayPause.setText("Play");
        }
        setVisible(true);

        // Listener de fechamento para liberar ponteiros de memória C
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                playerPanel.stop();
                if (owner != null) { owner.setEnabled(true); owner.toFront(); }
            }
        });

    }

    private void setupListeners() {
        btnPlayPause.addActionListener(e -> togglePlayPause());

        btnRewind.addActionListener(e -> stepSeconds(-5.0));
        btnFastForward.addActionListener(e -> stepSeconds(5.0));

        btnStop.addActionListener(e -> {
            playerPanel.stop();
            btnPlayPause.setText("Play");
            timeSlider.setValue(0);
            updateTimeLabel(0.0);
        });

        btnMute.addActionListener(e -> playerPanel.setMute(btnMute.isSelected()));

        volumeSlider.addChangeListener(e -> {
            float volValue = volumeSlider.getValue() / 100.0f;
            playerPanel.setVolume(volValue);
        });
        clickTimer.setRepeats(false); // Garante que o timer execute apenas uma vez por

        // Eventos do Slider de Linha de Tempo (Seek)
        timeSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                sliderIsChanging = true;
            }
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                double ratio = timeSlider.getValue() / 1000.0;
                double targetSeconds = ratio * videoDuration;
                playerPanel.seek(targetSeconds);
                sliderIsChanging = false;
            }

        });
//        playerPanel.addMouseListener(new java.awt.event.MouseAdapter() {
//            @Override
//            public void mouseClicked(java.awt.event.MouseEvent e) {
//                if (e.getClickCount() == 2) { // Detecta clique duplo
//                    toggleWindowMode();
//                }
//            }
//        });
        playerPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 1) {
                    // Configura a ação que será executada se for APENAS um clique simples
                    clickTimer.addActionListener(new java.awt.event.ActionListener() {
                        @Override
                        public void actionPerformed(java.awt.event.ActionEvent ae) {
                            togglePlayPause();
                            // Remove o listener para não acumular ações em cliques futuros
                            clickTimer.removeActionListener(this);
                        }
                    });
                    clickTimer.start(); // Inicia a contagem regressiva de 250ms
                } else if (e.getClickCount() == 2) {
                    // Se for um clique duplo, cancela o temporizador imediatamente
                    // impedindo que o vídeo pause/toque por acidente
                    if (clickTimer.isRunning()) {
                        clickTimer.stop();
                        // Limpa quaisquer listeners pendentes acumulados na fila do temporizador
                        for (java.awt.event.ActionListener al : clickTimer.getActionListeners()) {
                            clickTimer.removeActionListener(al);
                        }
                    }
                    toggleWindowMode(); // Executa a alternância de tela cheia
                }
            }
        });



        // Feedback contínuo da thread de reprodução nativa para atualizar os elementos visuais
        playerPanel.setTimeUpdateCallback(currentSeconds -> {
            if (!sliderIsChanging) {
                int sliderValue = (int) ((currentSeconds / videoDuration) * 1000.0);
                timeSlider.setValue(sliderValue);
                updateTimeLabel(currentSeconds);
            }
        });

//        playerPanel.setOnPlaybackFinished(() -> {
//            btnPlayPause.setText("Play");
//            timeSlider.setValue(0);
//            updateTimeLabel(0.0);
//
//        });
        playerPanel.setOnPlaybackFinished(() -> {
            btnPlayPause.setText("Play");
            timeSlider.setValue(0);
            updateTimeLabel(0.0);
            videoHasFinished = true; // MARCA QUE O VÍDEO CHEGOU AO FIM
        });
    }

    /**
     * Alterna o estado de reprodução entre Play e Pause de forma centralizada.
     */
//    private void togglePlayPause() {
//        if (btnPlayPause.getText().equals("Play")) {
//            playerPanel.play();
//            btnPlayPause.setText("Pause");
//        } else {
//            playerPanel.pause();
//            btnPlayPause.setText("Play");
//        }
//    }
    private void togglePlayPause() {
        if (btnPlayPause.getText().equals("Play")) {
            // CORREÇÃO: Se o contexto foi fechado pelo Stop OU se o vídeo chegou ao fim (EOF)
            if (playerPanel.getVideoCtx() == 0L || videoHasFinished) {

                // Garante que o painel feche qualquer rastro do vídeo anterior antes de reabrir
                playerPanel.stop();

                // Reabre o arquivo do início
                if (playerPanel.open(currentVideoFile.getAbsolutePath())) {
                    playerPanel.setVolume(volumeSlider.getValue() / 100.0f);
                    videoHasFinished = false; // Reseta a flag, pois o vídeo começou de novo
                }
            }

            playerPanel.play();
            btnPlayPause.setText("Pause");
        } else {
            playerPanel.pause();
            btnPlayPause.setText("Play");
        }
    }

    /**
     * Configura o mapeamento global da barra de espaço para alternar entre play e pause.
     */
//    private void setupKeyboardShortcuts() {
//        // Obtém o painel raiz para mapear o atalho de forma global na janela ativa
//        JComponent rootComponent = getRootPane();
//        InputMap inputMap = rootComponent.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
//        ActionMap actionMap = rootComponent.getActionMap();
//
//        // Vincula a tecla SPACE a uma chave identificadora
//        inputMap.put(KeyStroke.getKeyStroke("SPACE"), "togglePlayPauseAction");
//
//        // Define a ação executada ao disparar a chave identificadora
//        actionMap.put("togglePlayPauseAction", new AbstractAction() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                togglePlayPause();
//            }
//        });
//    }
    // Dentro do método setupKeyboardShortcuts() já existente:
    private void setupKeyboardShortcuts() {
        JComponent rootComponent = getRootPane();
        InputMap inputMap = rootComponent.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootComponent.getActionMap();

        // [Atalho Existente] Barra de Espaço
        inputMap.put(KeyStroke.getKeyStroke("SPACE"), "togglePlayPauseAction");
        actionMap.put("togglePlayPauseAction", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { togglePlayPause(); }
        });

        // [Atalho Existente] Tecla ESC
        inputMap.put(KeyStroke.getKeyStroke("ESCAPE"), "exitFullscreenAction");
        actionMap.put("exitFullscreenAction", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { if (isFullscreen) exitFullscreen(); }
        });

        // ── NOVO ATALHO: Seta para a ESQUERDA (Retrocede 5s) ──
        inputMap.put(KeyStroke.getKeyStroke("LEFT"), "rewindAction");
        actionMap.put("rewindAction", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { stepSeconds(-5.0); }
        });

        // ── NOVO ATALHO: Seta para a DIREITA (Avança 5s) ──
        inputMap.put(KeyStroke.getKeyStroke("RIGHT"), "fastForwardAction");
        actionMap.put("fastForwardAction", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { stepSeconds(5.0); }
        });
    }

    private void updateTimeLabel(double currentSeconds) {
        lblTime.setText(formatTime(currentSeconds) + " / " + formatTime(videoDuration));
    }

    private String formatTime(double seconds) {
        int totalSec = (int) seconds;
        int mins = totalSec / 60;
        int secs = totalSec % 60;
        return String.format("%02d:%02d", mins, secs);
    }

//    private void toggleWindowMode() {
//        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
//
//        if (!isFullscreen) {
//            // Salva o estado atual da janela para retornar depois
//            prevW = getWidth();
//            prevH = getHeight();
//            prevX = getX();
//            prevY = getY();
//
//            // 1ª Ação: Se a janela atual não corresponder à resolução nativa do vídeo,
//            // ajusta primeiro para a resolução nativa (Proporção 1:1)
//            int videoW = playerPanel.getPreferredSize().width;
//            int videoH = playerPanel.getPreferredSize().height;
//
//            // Verifica se a janela já está próxima do tamanho nativo
//            if (playerPanel.getWidth() != videoW || playerPanel.getHeight() != videoH) {
//                pack(); // Ajusta ao tamanho nativo determinado pelo preferredSize do painel
//                setLocationRelativeTo(null);
//                isFullscreen = false; // Permanece em modo janela nativa
//                // Força uma flag temporária para que o PRÓXIMO clique duplo vá para tela cheia
//                playerPanel.putClientProperty("wasNative", true);
//                return;
//            }
//
//            // 2ª Ação: Se já estava na resolução nativa, entra em Tela Cheia (Fullscreen)
//            dispose(); // O Swing exige desmontar a janela temporariamente para mudar propriedades de borda
//            setUndecorated(true); // Remove barras de título e bordas do Windows
//
//            if (gd.isFullScreenSupported()) {
//                gd.setFullScreenWindow(this);
//            } else {
//                // Fallback caso o sistema operacional não suporte o modo exclusivo
//                setExtendedState(JFrame.MAXIMIZED_BOTH);
//                setVisible(true);
//            }
//            isFullscreen = true;
//        } else {
//            // Retorna ao Modo Janela Normal restaurando as dimensões salvas
//            dispose();
//            setUndecorated(false); // Devolve as bordas e botões padrão da janela
//            gd.setFullScreenWindow(null);
//
//            setSize(prevW, prevH);
//            setLocation(prevX, prevY);
//            setVisible(true);
//            isFullscreen = false;
//            playerPanel.putClientProperty("wasNative", null);
//        }
//    }
private void toggleWindowMode() {
    if (!isFullscreen) {
        // Salva a posição e o tamanho atual da janela para poder voltar depois
        prevW = getWidth();
        prevH = getHeight();
        prevX = getX();
        prevY = getY();

        dispose(); // Desmanta temporariamente para aplicar propriedades visuais
        setUndecorated(true); // Remove barras de título, bordas e botões do Windows (X, -, [])
        controlPanel.setVisible(false); // Oculta a barra de tempo e botões de controle inferiores

        // Força a janela a ocupar as dimensões exatas de toda a tela atual
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setBounds(0, 0, screenSize.width, screenSize.height);

        setVisible(true);
        isFullscreen = true;
    } else {
        exitFullscreen();
    }
}

    /**
     * Centraliza o retorno para o modo janela padrão.
     */
    private void exitFullscreen() {
        if (!isFullscreen) return;

        dispose();
        setUndecorated(false); // Restaura as bordas padrões do sistema operacional
        controlPanel.setVisible(true); // Exibe os controles de vídeo novamente

        // Restaura o tamanho e posição originais que a janela tinha
        setSize(prevW, prevH);
        setLocation(prevX, prevY);

        setVisible(true);
        isFullscreen = false;
    }

    /**
     * Avança ou retrocede o vídeo em uma quantidade fixa de segundos de forma segura.
     */
    private void stepSeconds(double delta) {
        // Bloqueia a ação se o controle do slider principal estiver ativo manualmente
        if (sliderIsChanging) return;

        // Obtém o progresso atual do painel de vídeo
        double currentPos = playerPanel.getCurrentSeconds(); // Certifique-se de ter esse getter no VideoPreviewPanel
        double targetPos = Math.max(0.0, Math.min(videoDuration, currentPos + delta));

        // Atualiza imediatamente o slider visual para dar resposta instantânea ao usuário
        int sliderValue = (int) ((targetPos / videoDuration) * 1000.0);
        timeSlider.setValue(sliderValue);
        updateTimeLabel(targetPos);

        // Executa o processo de busca pesado em segundo plano para não congelar o Swing
        new Thread(() -> {
            playerPanel.seek(targetPos);
        }).start();
    }
}