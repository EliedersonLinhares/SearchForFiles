package com.esl.searchforfiles.preview;


import com.esl.searchforfiles.configuration.UIConfig;
import com.jme3.math.ColorRGBA;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

public class Obj3DViewerFrame extends JFrame {

    private final Obj3DApp app;
    private final Canvas jmeCanvas;
    private final JPanel controlPanel;
    private final JButton btnResetCamera;
    private final JToggleButton btnWireframe;
    private final JToggleButton btnGrid;
    private final JButton btnBackgroundColor;
    private final JButton btnLoadModel;
    private final JLabel lblModelInfo;
    private final JToggleButton btnMaterialMode;
    private File currentObjFile;

    public Obj3DViewerFrame(Window owner, File objFile) {
        this.currentObjFile = objFile;
        setTitle("Visualizador 3D — " + objFile.getName());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        if (owner != null) owner.setEnabled(false);

        // ── Configuração e inicialização do canvas jMonkeyEngine ──
        AppSettings settings = new AppSettings(true);
        settings.setWidth(900);
        settings.setHeight(650);
        settings.setSamples(4); // anti-aliasing
        settings.setGammaCorrection(false);

        app = new Obj3DApp();
        app.setSettings(settings);
        app.setShowSettings(false);
        app.setPauseOnLostFocus(false);
        app.createCanvas();

        JmeCanvasContext ctx = (JmeCanvasContext) app.getContext();
        jmeCanvas = ctx.getCanvas();
        jmeCanvas.setPreferredSize(new Dimension(900, 650));

        app.startCanvas();

        JPanel canvasHolder = new JPanel(new BorderLayout());
        canvasHolder.add(jmeCanvas, BorderLayout.CENTER);
        add(canvasHolder, BorderLayout.CENTER);

        // ── Barra Inferior de Controles (mesmo padrão do player de vídeo) ──
        controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel buttonPanel = new JPanel(new BorderLayout());
        JPanel leftButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));

        btnMaterialMode = new JToggleButton("Textura Original", false); // false = começa em modo cinza
        btnLoadModel = new JButton("Abrir OBJ...");
        btnResetCamera = new JButton("Resetar Câmera");
        btnWireframe = new JToggleButton("Wireframe");
        btnGrid = new JToggleButton("Grade", true);
        btnBackgroundColor = new JButton("Cor de Fundo");

        for (AbstractButton b : new AbstractButton[]{btnLoadModel, btnResetCamera, btnWireframe, btnGrid, btnBackgroundColor, btnMaterialMode}) {
            b.setFont(UIConfig.FONT_DEFAULT);
        }

        leftButtonsPanel.add(btnLoadModel);
        leftButtonsPanel.add(btnMaterialMode);
        leftButtonsPanel.add(btnResetCamera);
        leftButtonsPanel.add(btnWireframe);
        leftButtonsPanel.add(btnGrid);
        leftButtonsPanel.add(btnBackgroundColor);

        lblModelInfo = new JLabel("");
        lblModelInfo.setForeground(Color.GRAY);
        lblModelInfo.setFont(UIConfig.FONT_SMALL);
        JPanel rightInfoPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 2));
        rightInfoPanel.add(lblModelInfo);

        buttonPanel.add(leftButtonsPanel, BorderLayout.WEST);
        buttonPanel.add(rightInfoPanel, BorderLayout.EAST);

        JLabel lblHint = new JLabel("Botão esquerdo: orbitar   |   Botão direito: mover (pan)   |   Scroll: zoom");
        lblHint.setFont(UIConfig.FONT_SMALL);
        lblHint.setForeground(Color.GRAY);

        controlPanel.add(lblHint);
        controlPanel.add(Box.createVerticalStrut(5));
        controlPanel.add(buttonPanel);
        add(controlPanel, BorderLayout.SOUTH);

        setupListeners();

        setMinimumSize(new Dimension(680, 420));
        setSize(1000, 750);
        setLocationRelativeTo(null);

        // Carrega o modelo inicial depois que o canvas já estiver pronto
        SwingUtilities.invokeLater(() -> loadModel(currentObjFile));

        setVisible(true);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                app.stop(); // encerra a thread de renderização do jME corretamente
                if (owner != null) {
                    owner.setEnabled(true);
                    owner.toFront();
                }
            }
        });
    }

    private void setupListeners() {
        btnResetCamera.addActionListener(e -> app.resetCamera());

        btnWireframe.addActionListener(e -> app.toggleWireframe());

        btnGrid.addActionListener(e -> app.setGridVisible(btnGrid.isSelected()));

        btnBackgroundColor.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, "Escolha a cor de fundo", Color.DARK_GRAY);
            if (chosen != null) {
                ColorRGBA rgba = new ColorRGBA(
                        chosen.getRed() / 255f, chosen.getGreen() / 255f,
                        chosen.getBlue() / 255f, 1f);
                app.setBackgroundColor(rgba);
            }
        });

        btnLoadModel.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Arquivos OBJ", "obj"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                loadModel(chooser.getSelectedFile());
            }
        });
        btnMaterialMode.addActionListener(e ->
                app.setFlatGrayMode(!btnMaterialMode.isSelected()));
    }

    private void loadModel(File objFile) {
        currentObjFile = objFile;
        setTitle("Visualizador 3D — " + objFile.getName() + " (carregando...)");
        lblModelInfo.setText("Carregando...");

        app.loadObjFile(objFile,
                () -> SwingUtilities.invokeLater(() -> {
                    setTitle("Visualizador 3D — " + objFile.getName());
                    double fileSizeInMB = (double) objFile.length() / (1024 * 1024);
                    lblModelInfo.setText(String.format("Triângulos: %d | %.2f MB",
                            app.countTriangles(), fileSizeInMB));
                }),
                (errorMsg) -> SwingUtilities.invokeLater(() -> {
                    setTitle("Visualizador 3D — Erro ao carregar " + objFile.getName());
                    lblModelInfo.setText("Erro ao carregar o modelo");
                    JOptionPane.showMessageDialog(this,
                            "Não foi possível carregar o arquivo OBJ.\n\nDetalhe técnico:\n" + errorMsg,
                            "Erro", JOptionPane.ERROR_MESSAGE);
                })
        );
    }
}