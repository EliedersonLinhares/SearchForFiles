package com.esl.searchforfiles.preview;


import com.esl.searchforfiles.configuration.UIConfig;
import com.jme3.math.ColorRGBA;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;
import jnafilechooser.api.JnaFileChooser;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.awt.image.BufferedImage;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.BasicStroke;

public class Obj3DViewerFrame extends JFrame {

    private final Obj3DApp app;
    private final Canvas jmeCanvas;
    private final JPanel controlPanel;
    private final JButton btnResetCamera;
    private final JToggleButton btnWireframe;
    private final JToggleButton btnGrid;
    private final JButton btnBackgroundColor;
    private final JLabel lblModelInfo;
    private final JButton btnResetLight;
    private final JButton btnLocateTextures;
    private final JToggleButton btnShowLightGizmo;
    private final JToggleButton btnFillLight;
    private final JToggleButton btnToolOrbit;
    private final JToggleButton btnToolZoom;
    private final JToggleButton btnToolPan;
    private final JButton btnScreenshot;
    private final JSlider keyLightSlider;
    private final JSlider fillLightSlider;
    private final JLabel lblKeyLightValue;
    private final JLabel lblFillLightValue;
    private final JComboBox<String> cameraViewCombo;
    private final JSlider creaseAngleSlider;
    private final JSlider outlineThicknessSlider;
    private final JLabel lblCreaseAngleValue;
    private final JLabel lblOutlineThicknessValue;
    private static final String[] VIEW_LABELS = {
            "Livre", "Frontal", "Traseira", "Superior", "Inferior", "Esquerda", "Direita"
    };
    private static final Obj3DApp.CameraMode[] VIEW_MODES = {
            Obj3DApp.CameraMode.FREE, Obj3DApp.CameraMode.FRONT, Obj3DApp.CameraMode.BACK,
            Obj3DApp.CameraMode.TOP, Obj3DApp.CameraMode.BOTTOM,
            Obj3DApp.CameraMode.LEFT, Obj3DApp.CameraMode.RIGHT
    };
    private boolean syncingCombo = false;
    private File currentObjFile;
    private static final String[] STYLE_LABELS = {
            "Cinza Sólido", "Material Original", "Caneta (Pen)", "Monocromático"
    };
    private static final Obj3DApp.RenderStyle[] STYLE_VALUES = {
            Obj3DApp.RenderStyle.FLAT_GRAY, Obj3DApp.RenderStyle.ORIGINAL_MATERIAL,
            Obj3DApp.RenderStyle.PEN, Obj3DApp.RenderStyle.MONOCHROME
    };
    private final JComboBox<String> renderStyleCombo;

    private static final String[] RESOLUTION_LABELS = {
            "Viewport (atual)", "640×480", "800×600", "1024×768",
            "1280×960", "1600×1200", "2048×1536", "2560×1920", "3200×2400"
    };

    // null significa "usar a resolução atual do viewport" — tratado especialmente no listener
    private static final int[][] RESOLUTION_VALUES = {
            null,
            {640, 480}, {800, 600}, {1024, 768},
            {1280, 960}, {1600, 1200}, {2048, 1536}, {2560, 1920}, {3200, 2400}
    };
    private final JComboBox<String> resolutionCombo = new JComboBox<>(RESOLUTION_LABELS);


    public Obj3DViewerFrame(Window owner, File objFile) {
        this.currentObjFile = objFile;
        setTitle("Visualizador 3D — " + objFile.getName());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        setIconImages(UIConfig.IconsConfig(this));

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

        JLabel lblRenderStyle = new JLabel("Estilo:");
        lblRenderStyle.setFont(UIConfig.FONT_SMALL);
        renderStyleCombo = new JComboBox<>(STYLE_LABELS);
        renderStyleCombo.setFont(UIConfig.FONT_DEFAULT);
        renderStyleCombo.setSelectedIndex(0);


        JLabel lblCameraView = new JLabel("Câmera:");
        lblCameraView.setFont(UIConfig.FONT_SMALL);

        cameraViewCombo = new JComboBox<>(VIEW_LABELS);
        cameraViewCombo.setFont(UIConfig.FONT_DEFAULT);
        cameraViewCombo.setSelectedIndex(0); // "Livre"

        btnFillLight = new JToggleButton("Luz de Preenchimento", true); // ligada por padrão
        btnFillLight.setFont(UIConfig.FONT_DEFAULT);

        btnResetLight = new JButton("Resetar Luz");
        btnShowLightGizmo = new JToggleButton("Indicador de Luz", false);
      //  btnMaterialMode = new JToggleButton("Textura Original", false); // false = começa em modo cinza
        btnResetCamera = new JButton("Resetar Câmera");
        btnWireframe = new JToggleButton("Wireframe");
        btnGrid = new JToggleButton("Grade", true);
        btnBackgroundColor = new JButton("Cor de Fundo");

        // ── Painel de controle de iluminação ──
        JPanel lightPanel = new JPanel(new GridLayout(2, 1, 0, 2));

        JPanel keyLightRow = new JPanel(new BorderLayout(8, 0));
        JLabel lblKeyLight = new JLabel("Luz Principal:");
        lblKeyLight.setFont(UIConfig.FONT_SMALL);
        keyLightSlider = new JSlider(0, 300, 110); // 0-300 representa 0.0 a 3.0 (passo de 0.01)
        lblKeyLightValue = new JLabel("1.10");
        lblKeyLightValue.setFont(UIConfig.FONT_SMALL);
        lblKeyLightValue.setPreferredSize(new Dimension(40, 20));
        keyLightRow.add(lblKeyLight, BorderLayout.WEST);
        keyLightRow.add(keyLightSlider, BorderLayout.CENTER);
        keyLightRow.add(lblKeyLightValue, BorderLayout.EAST);

        JPanel fillLightRow = new JPanel(new BorderLayout(8, 0));
        JLabel lblFillLight = new JLabel("Luz de Preench.:");
        lblFillLight.setFont(UIConfig.FONT_SMALL);
        fillLightSlider = new JSlider(0, 150, 35); // 0-150 representa 0.0 a 1.5
        lblFillLightValue = new JLabel("0.35");
        lblFillLightValue.setFont(UIConfig.FONT_SMALL);
        lblFillLightValue.setPreferredSize(new Dimension(40, 20));
        fillLightRow.add(lblFillLight, BorderLayout.WEST);
        fillLightRow.add(fillLightSlider, BorderLayout.CENTER);
        fillLightRow.add(lblFillLightValue, BorderLayout.EAST);

        lightPanel.add(keyLightRow);
        lightPanel.add(fillLightRow);

        for (AbstractButton b : new AbstractButton[]{ btnResetCamera, btnWireframe, btnGrid,
                btnBackgroundColor, btnResetLight, btnShowLightGizmo}) {
            b.setFont(UIConfig.FONT_DEFAULT);
        }

        btnLocateTextures = new JButton("Localizar Texturas...");
        btnLocateTextures.setFont(UIConfig.FONT_DEFAULT);


        ButtonGroup toolGroup = new ButtonGroup();
        btnToolOrbit = new JToggleButton("🧭 Orbitar", true); // ferramenta padrão ao abrir
        btnToolZoom = new JToggleButton("🔍 Zoom", false);
        btnToolPan = new JToggleButton("✋ Mover", false);

        toolGroup.add(btnToolOrbit);
        toolGroup.add(btnToolZoom);
        toolGroup.add(btnToolPan);

        for (AbstractButton b : new AbstractButton[]{btnToolOrbit, btnToolZoom, btnToolPan}) {
            b.setFont(UIConfig.FONT_DEFAULT);
        }
        btnScreenshot = new JButton("Capturar Tela...");
        btnScreenshot.setFont(UIConfig.FONT_DEFAULT);



       // leftButtonsPanel.add(btnMaterialMode);
        leftButtonsPanel.add(lblRenderStyle);
        leftButtonsPanel.add(renderStyleCombo);
        leftButtonsPanel.add(btnToolOrbit);
        leftButtonsPanel.add(btnToolZoom);
        leftButtonsPanel.add(btnToolPan);
        leftButtonsPanel.add(btnResetCamera);
        leftButtonsPanel.add(lblCameraView);
        leftButtonsPanel.add(cameraViewCombo);
        leftButtonsPanel.add(btnLocateTextures);
        leftButtonsPanel.add(btnScreenshot);
        leftButtonsPanel.add(btnResetLight);
        leftButtonsPanel.add(btnShowLightGizmo);
        leftButtonsPanel.add(btnFillLight);
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

        JLabel lblHint = new JLabel(
                "Selecione uma ferramenta acima  |  Ctrl + botão esquerdo: rotacionar luz  |  Botão direito: mover  |  Scroll: zoom");
        lblHint.setFont(UIConfig.FONT_SMALL);
        lblHint.setForeground(Color.GRAY);


        JPanel penPanel = new JPanel(new GridLayout(2, 1, 0, 2));

        JPanel creaseRow = new JPanel(new BorderLayout(8, 0));
        JLabel lblCrease = new JLabel("Ângulo de Vinco:");
        lblCrease.setFont(UIConfig.FONT_SMALL);
        creaseAngleSlider = new JSlider(1, 90, 35);
        lblCreaseAngleValue = new JLabel("35°");
        lblCreaseAngleValue.setFont(UIConfig.FONT_SMALL);
        lblCreaseAngleValue.setPreferredSize(new Dimension(35, 20));
        creaseRow.add(lblCrease, BorderLayout.WEST);
        creaseRow.add(creaseAngleSlider, BorderLayout.CENTER);
        creaseRow.add(lblCreaseAngleValue, BorderLayout.EAST);

        JPanel outlineRow = new JPanel(new BorderLayout(8, 0));
        JLabel lblOutline = new JLabel("Espessura Contorno:");
        lblOutline.setFont(UIConfig.FONT_SMALL);
        outlineThicknessSlider = new JSlider(0, 50, 15); // 0-50 representa 0.000 a 0.050
        lblOutlineThicknessValue = new JLabel("0.015");
        lblOutlineThicknessValue.setFont(UIConfig.FONT_SMALL);
        lblOutlineThicknessValue.setPreferredSize(new Dimension(45, 20));
        outlineRow.add(lblOutline, BorderLayout.WEST);
        outlineRow.add(outlineThicknessSlider, BorderLayout.CENTER);
        outlineRow.add(lblOutlineThicknessValue, BorderLayout.EAST);

        penPanel.add(creaseRow);
        penPanel.add(outlineRow);



        controlPanel.add(lblHint);
        controlPanel.add(Box.createVerticalStrut(5));
        controlPanel.add(lightPanel);        // NOVO
        controlPanel.add(Box.createVerticalStrut(5));
        controlPanel.add(penPanel);
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

        btnResetLight.addActionListener(e -> app.resetLight());
        btnShowLightGizmo.addActionListener(e -> app.setLightGizmoVisible(btnShowLightGizmo.isSelected()));
        btnFillLight.addActionListener(e -> app.setFillLightEnabled(btnFillLight.isSelected()));
        keyLightSlider.addChangeListener(e -> {
            float value = keyLightSlider.getValue() / 100.0f;
            lblKeyLightValue.setText(String.format("%.2f", value));
            app.setKeyLightIntensity(value);
        });

        fillLightSlider.addChangeListener(e -> {
            float value = fillLightSlider.getValue() / 100.0f;
            lblFillLightValue.setText(String.format("%.2f", value));
            app.setFillLightIntensity(value);
        });

        // dentro do ActionListener do combo:
        cameraViewCombo.addActionListener(e -> {
            if (syncingCombo) return;
            int index = cameraViewCombo.getSelectedIndex();
            if (index >= 0) app.setCameraMode(VIEW_MODES[index]);
        });

// dentro do callback onCameraModeChanged:
        app.setOnCameraModeChanged(mode -> SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < VIEW_MODES.length; i++) {
                if (VIEW_MODES[i] == mode) {
                    syncingCombo = true;
                    cameraViewCombo.setSelectedIndex(i);
                    syncingCombo = false;
                    break;
                }
            }
        }));
        renderStyleCombo.addActionListener(e -> {
            int idx = renderStyleCombo.getSelectedIndex();
            if (idx >= 0) app.setRenderStyle(STYLE_VALUES[idx]);
        });

        creaseAngleSlider.addChangeListener(e -> {
            int degrees = creaseAngleSlider.getValue();
            lblCreaseAngleValue.setText(degrees + "°");
            app.setCreaseAngle(degrees);
        });

        outlineThicknessSlider.addChangeListener(e -> {
            float ratio = outlineThicknessSlider.getValue() / 1000.0f;
            lblOutlineThicknessValue.setText(String.format("%.3f", ratio));
            app.setOutlineThickness(ratio);
        });

        btnToolOrbit.addActionListener(e -> selectTool(Obj3DApp.ToolMode.ORBIT, Cursor.getDefaultCursor()));
        btnToolZoom.addActionListener(e -> selectTool(Obj3DApp.ToolMode.ZOOM, createZoomCursor()));
        btnToolPan.addActionListener(e -> selectTool(Obj3DApp.ToolMode.PAN, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)));

        btnScreenshot.addActionListener(e -> showScreenshotDialog());

        btnLocateTextures.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setCurrentDirectory(currentObjFile);
            chooser.setApproveButtonText("Selecionar");
            chooser.setDialogTitle("Selecione a pasta que contém as texturas");

            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File folder = chooser.getSelectedFile();
                lblModelInfo.setText("Recarregando com texturas de: " + folder.getName() + "...");

                app.addTextureSearchPathAndReload(folder,
                        () -> SwingUtilities.invokeLater(() -> {
                            lblModelInfo.setText(String.format("Triângulos: %d", app.countTriangles()));
                            JOptionPane.showMessageDialog(this,
                                    "Modelo recarregado. Verifique se as texturas apareceram corretamente.",
                                    "Texturas", JOptionPane.INFORMATION_MESSAGE);
                        }),
                        (errorMsg) -> SwingUtilities.invokeLater(() -> {
                            lblModelInfo.setText("Erro ao recarregar");
                            JOptionPane.showMessageDialog(this,
                                    "Não foi possível recarregar o modelo:\n" + errorMsg,
                                    "Erro", JOptionPane.ERROR_MESSAGE);
                        })
                );
            }

        });
    }

    private void showScreenshotDialog() {
        JCheckBox chkTransparent = new JCheckBox("Fundo transparente", false);
        JCheckBox chkShowGrid = new JCheckBox("Mostrar grade", btnGrid.isSelected());

        JPanel resolutionRow = new JPanel(new BorderLayout(8, 0));
        resolutionRow.add(new JLabel("Resolução:"), BorderLayout.WEST);
        resolutionCombo.setSelectedIndex(0); // "Viewport (atual)" como padrão
        resolutionRow.add(resolutionCombo, BorderLayout.CENTER);

        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.add(resolutionRow);
        optionsPanel.add(Box.createVerticalStrut(8));
        optionsPanel.add(chkTransparent);
        optionsPanel.add(chkShowGrid);

        int result = JOptionPane.showConfirmDialog(this, optionsPanel,
                "Opções de Captura", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return;

        JFileChooser saveChooser = new JFileChooser();
        saveChooser.setDialogTitle("Salvar captura como PNG");
        saveChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Imagem PNG", "png"));
        saveChooser.setSelectedFile(new File(currentObjFile.getName().replaceAll("\\.obj$", "") + "_captura.png"));

        if (saveChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File outputFile = saveChooser.getSelectedFile();
        if (!outputFile.getName().toLowerCase().endsWith(".png")) {
            outputFile = new File(outputFile.getParentFile(), outputFile.getName() + ".png");
        }

        int selectedIndex = resolutionCombo.getSelectedIndex();
        int[] resolution = RESOLUTION_VALUES[selectedIndex];
        Integer captureWidth = (resolution != null) ? resolution[0] : null;
        Integer captureHeight = (resolution != null) ? resolution[1] : null;

        File finalOutputFile = outputFile;
        app.captureScreenshot(outputFile, chkTransparent.isSelected(), chkShowGrid.isSelected(),
                captureWidth, captureHeight,
                () -> SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this,
                                "Captura salva em:\n" + finalOutputFile.getAbsolutePath(),
                                "Sucesso", JOptionPane.INFORMATION_MESSAGE)),
                (errorMsg) -> SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this,
                                "Erro ao salvar a captura:\n" + errorMsg,
                                "Erro", JOptionPane.ERROR_MESSAGE))
        );
    }

    private void selectTool(Obj3DApp.ToolMode mode, Cursor cursor) {
        app.setToolMode(mode);
        jmeCanvas.setCursor(cursor);
    }
    private Cursor createZoomCursor() {
        int size = 32;
        BufferedImage cursorImg = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = cursorImg.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2f));

        g2.drawOval(4, 4, 14, 14);       // lente da lupa
        g2.drawLine(16, 16, 24, 24);     // cabo da lupa

        // Sinal de "+" dentro da lente, indicando zoom
        g2.drawLine(8, 11, 14, 11);
        g2.drawLine(11, 8, 11, 14);

        g2.dispose();
        return Toolkit.getDefaultToolkit().createCustomCursor(cursorImg, new Point(11, 11), "zoomCursor");
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