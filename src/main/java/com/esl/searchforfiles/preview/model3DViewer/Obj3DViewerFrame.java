package com.esl.searchforfiles.preview.model3DViewer;


import com.esl.searchforfiles.configuration.UIConfig;
import com.jme3.math.ColorRGBA;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;

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
    private final ScreenshotManager screenshotManager;

    // Toolbar (ferramentas de mouse, sempre visíveis)
    private final JToggleButton btnToolOrbit;
    private final JToggleButton btnToolZoom;
    private final JToggleButton btnToolPan;

    // Status bar
    private final JLabel lblModelInfo;
    private final JLabel lblHint;

    // Menu - Câmera
    private final JComboBox<String> hiddenCameraSync = null; // não usado; mantido só p/ referência de sync
    private JRadioButtonMenuItem[] cameraViewItems;

    // Menu - Renderização / Estilo
    private JRadioButtonMenuItem[] styleItems;
    private JCheckBoxMenuItem chkWireframe;
    private JCheckBoxMenuItem chkGrid;

    // Menu - Iluminação
    private JCheckBoxMenuItem chkShowLightGizmo;
    private JCheckBoxMenuItem chkFillLight;
    private JSlider keyLightSlider;
    private JSlider fillLightSlider;
    private JLabel lblKeyLightValue;
    private JLabel lblFillLightValue;

    // Menu - Pen (crease/outline)
    private JSlider creaseAngleSlider;
    private JSlider outlineThicknessSlider;
    private JLabel lblCreaseAngleValue;
    private JLabel lblOutlineThicknessValue;

    private static final String[] VIEW_LABELS = {
            "Livre", "Frontal", "Traseira", "Superior", "Inferior", "Esquerda", "Direita"
    };
    private static final Obj3DApp.CameraMode[] VIEW_MODES = {
            Obj3DApp.CameraMode.FREE, Obj3DApp.CameraMode.FRONT, Obj3DApp.CameraMode.BACK,
            Obj3DApp.CameraMode.TOP, Obj3DApp.CameraMode.BOTTOM,
            Obj3DApp.CameraMode.LEFT, Obj3DApp.CameraMode.RIGHT
    };

    private static final String[] STYLE_LABELS = {
            "Cinza Sólido", "Material Original", "Caneta (Pen)", "Monocromático"
    };
    private static final Obj3DApp.RenderStyle[] STYLE_VALUES = {
            Obj3DApp.RenderStyle.FLAT_GRAY, Obj3DApp.RenderStyle.ORIGINAL_MATERIAL,
            Obj3DApp.RenderStyle.PEN, Obj3DApp.RenderStyle.MONOCHROME
    };

    private static final String[] RESOLUTION_LABELS = {
            "Viewport (atual)", "640×480", "800×600", "1024×768",
            "1280×960", "1600×1200", "2048×1536", "2560×1920", "3200×2400"
    };
    private static final int[][] RESOLUTION_VALUES = {
            null,
            {640, 480}, {800, 600}, {1024, 768},
            {1280, 960}, {1600, 1200}, {2048, 1536}, {2560, 1920}, {3200, 2400}
    };
    private final JComboBox<String> resolutionCombo = new JComboBox<>(RESOLUTION_LABELS);

    private boolean syncingCameraMenu = false;
    private File currentObjFile;

    private final GroupVisibilityPanel groupVisibilityPanel;
    private final MaterialsPanel materialsPanel;
    private final JSplitPane leftSplit;
    private final JSplitPane mainSplit;

    public Obj3DViewerFrame(Window owner, File objFile) {
        this.currentObjFile = objFile;
        setTitle("Visualizador 3D — " + objFile.getName());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        setIconImages(UIConfig.IconsConfig(this));

        if (owner != null) owner.setEnabled(false);

        // ── Canvas jMonkeyEngine ──
        AppSettings settings = new AppSettings(true);
        settings.setWidth(900);
        settings.setHeight(650);
        settings.setSamples(4);
        settings.setGammaCorrection(false);

        app = new Obj3DApp();
        app.setSettings(settings);
        app.setShowSettings(false);
        app.setPauseOnLostFocus(false);
        app.createCanvas();

        screenshotManager = new ScreenshotManager(app);

        JmeCanvasContext ctx = (JmeCanvasContext) app.getContext();
        jmeCanvas = ctx.getCanvas();
        jmeCanvas.setMinimumSize(new Dimension(200, 150));

        app.startCanvas();

        // Canvas ocupa 100% do espaço central — é o que dá a "responsividade"
        JPanel canvasHolder = new JPanel(new BorderLayout());
        canvasHolder.add(jmeCanvas, BorderLayout.CENTER);
      //  add(canvasHolder, BorderLayout.CENTER);

        // ── Painel esquerdo: GroupVisibility (topo) + Dummy (baixo), divididos verticalmente ──
        groupVisibilityPanel = new GroupVisibilityPanel(app);
        materialsPanel = new MaterialsPanel(app);;

        leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, groupVisibilityPanel, materialsPanel);
        leftSplit.setResizeWeight(0.6); // 60% do espaço vertical para Grupos, 40% para o painel Dummy
        leftSplit.setContinuousLayout(true); // redesenha durante o arrasto, em vez de só ao soltar — mais responsivo
        leftSplit.setOneTouchExpandable(true); // pequenas setas para colapsar rapidamente cada metade

// ── Divisão principal: painel esquerdo (grupos+dummy) | canvas 3D à direita ──
        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,canvasHolder, leftSplit );
        mainSplit.setResizeWeight(0.75); // ao redimensionar a janela, o espaço extra vai inteiro para o canvas 3D
        mainSplit.setContinuousLayout(true);
        mainSplit.setOneTouchExpandable(true);
        mainSplit.setDividerLocation(750); // largura inicial do painel esquerdo, em pixels

        add(mainSplit, BorderLayout.CENTER); // ANTES: add(canvasHolder, BorderLayout.CENTER)


        // ── Toolbar (mouse tools) ──
        btnToolOrbit = new JToggleButton("🧭 Orbitar", true);
        btnToolZoom = new JToggleButton("🔍 Zoom", false);
        btnToolPan = new JToggleButton("✋ Mover", false);
        ButtonGroup toolGroup = new ButtonGroup();
        toolGroup.add(btnToolOrbit);
        toolGroup.add(btnToolZoom);
        toolGroup.add(btnToolPan);
        for (AbstractButton b : new AbstractButton[]{btnToolOrbit, btnToolZoom, btnToolPan}) {
            b.setFont(UIConfig.FONT_DEFAULT);
            b.setFocusable(false);
        }

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setRollover(true);
        toolBar.add(btnToolOrbit);
        toolBar.add(btnToolZoom);
        toolBar.add(btnToolPan);
        toolBar.addSeparator();
        JButton btnQuickReset = new JButton("Resetar Câmera");
        btnQuickReset.setFocusable(false);
        btnQuickReset.addActionListener(e -> app.resetCamera());
        toolBar.add(btnQuickReset);

        // ── Menu bar ──
        setJMenuBar(buildMenuBar());

        // Empilha menubar + toolbar no NORTH
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(toolBar, BorderLayout.NORTH);
        add(northPanel, BorderLayout.NORTH);

        // ── Status bar ──
        lblModelInfo = new JLabel("");
        lblModelInfo.setForeground(Color.GRAY);
        lblModelInfo.setFont(UIConfig.FONT_SMALL);

        lblHint = new JLabel(
                "Ctrl + botão esquerdo: rotacionar luz  |  Botão direito: mover  |  Scroll: zoom");
        lblHint.setFont(UIConfig.FONT_SMALL);
        lblHint.setForeground(Color.GRAY);

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusBar.add(lblHint, BorderLayout.WEST);
        statusBar.add(lblModelInfo, BorderLayout.EAST);
        add(statusBar, BorderLayout.SOUTH);

        setupListeners();

        setMinimumSize(new Dimension(900, 500));
        setSize(1000, 750);
        setLocationRelativeTo(null);

        SwingUtilities.invokeLater(() -> loadModel(currentObjFile));

        setVisible(true);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                app.stop();
                if (owner != null) {
                    owner.setEnabled(true);
                    owner.toFront();
                }
            }
        });
    }

    // ──────────────────────────────────────────────────────────
    // Construção da barra de menus
    // ──────────────────────────────────────────────────────────
    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        menuBar.add(buildArquivoMenu());
        menuBar.add(buildCameraMenu());
        menuBar.add(buildIluminacaoMenu());
        menuBar.add(buildRenderizacaoMenu());

        return menuBar;
    }

    private JMenu buildArquivoMenu() {
        JMenu menu = new JMenu("Arquivo");

        JMenuItem itemLocateTextures = new JMenuItem("Localizar Texturas...");
        itemLocateTextures.addActionListener(e -> locateTextures());

        JMenuItem itemScreenshot = new JMenuItem("Capturar Tela...");
        itemScreenshot.addActionListener(e -> showScreenshotDialog());

        menu.add(itemLocateTextures);
        menu.add(itemScreenshot);
        return menu;
    }

    private JMenu buildCameraMenu() {
        JMenu menu = new JMenu("Câmera");

        JMenuItem itemReset = new JMenuItem("Resetar Câmera");
        itemReset.addActionListener(e -> app.resetCamera());
        menu.add(itemReset);
        menu.addSeparator();

        ButtonGroup group = new ButtonGroup();
        cameraViewItems = new JRadioButtonMenuItem[VIEW_LABELS.length];
        for (int i = 0; i < VIEW_LABELS.length; i++) {
            int index = i;
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(VIEW_LABELS[i], i == 0);
            item.addActionListener(e -> {
                if (syncingCameraMenu) return;
                app.setCameraMode(VIEW_MODES[index]);
            });
            group.add(item);
            menu.add(item);
            cameraViewItems[i] = item;
        }

        app.setOnCameraModeChanged(mode -> SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < VIEW_MODES.length; i++) {
                if (VIEW_MODES[i] == mode) {
                    syncingCameraMenu = true;
                    cameraViewItems[i].setSelected(true);
                    syncingCameraMenu = false;
                    break;
                }
            }
        }));

        return menu;
    }

    private JMenu buildIluminacaoMenu() {
        JMenu menu = new JMenu("Iluminação");

        JMenuItem itemResetLight = new JMenuItem("Resetar Luz");
        itemResetLight.addActionListener(e -> app.resetLight());
        menu.add(itemResetLight);

        chkShowLightGizmo = new JCheckBoxMenuItem("Indicador de Luz", false);
        chkShowLightGizmo.addActionListener(e -> app.setLightGizmoVisible(chkShowLightGizmo.isSelected()));
        menu.add(chkShowLightGizmo);

        chkFillLight = new JCheckBoxMenuItem("Luz de Preenchimento", true);
        chkFillLight.addActionListener(e -> app.setFillLightEnabled(chkFillLight.isSelected()));
        menu.add(chkFillLight);

        menu.addSeparator();

        // Sliders embutidos diretamente no menu
        keyLightSlider = new JSlider(0, 300, 110);
        lblKeyLightValue = new JLabel("1.10");
        menu.add(buildSliderMenuPanel("Luz Principal:", keyLightSlider, lblKeyLightValue));

        fillLightSlider = new JSlider(0, 150, 35);
        lblFillLightValue = new JLabel("0.35");
        menu.add(buildSliderMenuPanel("Luz de Preench.:", fillLightSlider, lblFillLightValue));

        return menu;
    }

    private JMenu buildRenderizacaoMenu() {
        JMenu menu = new JMenu("Renderização");

        JMenu subEstilo = new JMenu("Estilo");
        ButtonGroup styleGroup = new ButtonGroup();
        styleItems = new JRadioButtonMenuItem[STYLE_LABELS.length];
        for (int i = 0; i < STYLE_LABELS.length; i++) {
            int idx = i;
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(STYLE_LABELS[i], i == 0);
            item.addActionListener(e -> app.setRenderStyle(STYLE_VALUES[idx]));
            styleGroup.add(item);
            subEstilo.add(item);
            styleItems[i] = item;
        }
        menu.add(subEstilo);

        menu.addSeparator();

        chkWireframe = new JCheckBoxMenuItem("Wireframe", false);
        chkWireframe.addActionListener(e -> app.toggleWireframe());
        menu.add(chkWireframe);

        chkGrid = new JCheckBoxMenuItem("Grade", true);
        chkGrid.addActionListener(e -> app.setGridVisible(chkGrid.isSelected()));
        menu.add(chkGrid);

        JMenuItem itemBgColor = new JMenuItem("Cor de Fundo...");
        itemBgColor.addActionListener(e -> chooseBackgroundColor());
        menu.add(itemBgColor);

        menu.addSeparator();

        JMenu subPen = new JMenu("Contorno / Vinco (estilo Caneta)");
        creaseAngleSlider = new JSlider(1, 90, 19);
        lblCreaseAngleValue = new JLabel("35°");
        subPen.add(buildSliderMenuPanel("Ângulo de Vinco:", creaseAngleSlider, lblCreaseAngleValue));

        outlineThicknessSlider = new JSlider(0, 50, 2);
        lblOutlineThicknessValue = new JLabel("0.015");
        subPen.add(buildSliderMenuPanel("Espessura Contorno:", outlineThicknessSlider, lblOutlineThicknessValue));
        menu.add(subPen);

        return menu;
    }

    /** Cria um painel com label + slider + valor, para ser embutido dentro de um JMenu. */
    private JPanel buildSliderMenuPanel(String labelText, JSlider slider, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));

        JLabel label = new JLabel(labelText);
        label.setFont(UIConfig.FONT_SMALL);
        valueLabel.setFont(UIConfig.FONT_SMALL);
        valueLabel.setPreferredSize(new Dimension(45, 20));

        slider.setPreferredSize(new Dimension(160, slider.getPreferredSize().height));

        row.add(label, BorderLayout.WEST);
        row.add(slider, BorderLayout.CENTER);
        row.add(valueLabel, BorderLayout.EAST);
        return row;
    }

    // ──────────────────────────────────────────────────────────
    // Listeners
    // ──────────────────────────────────────────────────────────
    private void setupListeners() {
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
    }

    private void chooseBackgroundColor() {
        Color chosen = JColorChooser.showDialog(this, "Escolha a cor de fundo", Color.DARK_GRAY);
        if (chosen != null) {
            ColorRGBA rgba = new ColorRGBA(
                    chosen.getRed() / 255f, chosen.getGreen() / 255f,
                    chosen.getBlue() / 255f, 1f);
            app.setBackgroundColor(rgba);
        }
    }

    private void locateTextures() {
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
    }

    private void showScreenshotDialog() {
        JCheckBox chkTransparent = new JCheckBox("Fundo transparente", false);
        JCheckBox chkShowGridOpt = new JCheckBox("Mostrar grade", chkGrid.isSelected());

        JPanel resolutionRow = new JPanel(new BorderLayout(8, 0));
        resolutionRow.add(new JLabel("Resolução:"), BorderLayout.WEST);
        resolutionCombo.setSelectedIndex(0);
        resolutionRow.add(resolutionCombo, BorderLayout.CENTER);

        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.add(resolutionRow);
        optionsPanel.add(Box.createVerticalStrut(8));
        optionsPanel.add(chkTransparent);
        optionsPanel.add(chkShowGridOpt);

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
        screenshotManager.captureScreenshot(outputFile, chkTransparent.isSelected(), chkShowGridOpt.isSelected(),
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
        g2.drawOval(4, 4, 14, 14);
        g2.drawLine(16, 16, 24, 24);
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
                    groupVisibilityPanel.refresh();
                    materialsPanel.refresh();
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