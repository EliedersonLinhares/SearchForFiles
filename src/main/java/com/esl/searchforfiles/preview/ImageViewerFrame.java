package com.esl.searchforfiles.preview;


import com.esl.searchforfiles.Video.ImageCodec;
import com.esl.searchforfiles.actions.imageEditor.ImagePreviewPanel;
import com.esl.searchforfiles.configuration.UIConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Visualizador de imagem única — sem navegação entre arquivos.
 * <p>
 * Suporta todos os formatos do ImageCodec (PNG, JPEG, WebP, TIFF,
 * PSD, EXR, HDR, AVIF, …) + zoom.
 * <p>
 * Barra inferior: resolução, tamanho em disco, DPI e bit depth.
 * <p>
 * Uso:
 * new ImageViewerFrame(owner, new File("foto.psd"));
 */
public class ImageViewerFrame extends JFrame {

    // ── Constantes ─────────────────────────────────────────────────
    private static final double ZOOM_STEP = 0.15;
    private static final double ZOOM_MIN = 0.05;
    private static final double ZOOM_MAX = 8.0;
    private static final int PREVIEW_MAX = 1400; // px no lado maior

    // ── Estado ─────────────────────────────────────────────────────
    private final File file;
    private BufferedImage preview;   // proxy reduzido para exibição
    private ImageMeta meta;

    private double zoomFactor = 1.0;
    private double fitScale = 1.0;

    // ── Widgets ────────────────────────────────────────────────────
    private ImagePreviewPanel imagePreviewPanel;
    private JLabel zoomLabel;
    private JLabel lblFilename;
    private JLabel lblResolution;
    private JLabel lblFileSize;
    private JLabel lblDpi;
    private JLabel lblBitDepth;
    private JLabel lblFormat;

    // ══════════════════════════════════════════════════════════════
    // Construtor
    // ══════════════════════════════════════════════════════════════
    public ImageViewerFrame(Window owner, File file) {
        super("Visualizador — " + file.getName());
        this.file = file;

        if (owner != null) owner.setEnabled(false);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1000, 680);
        setMinimumSize(new Dimension(500, 380));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (owner != null) {
                    owner.setEnabled(true);
                    owner.toFront();
                }
            }
        });

        buildUI();
        registerKeyBindings();
        setVisible(true);

        // Carregar imagem após a janela estar visível (precisa do tamanho do painel)
        SwingUtilities.invokeLater(this::loadImage);
    }

    private static String humanSize(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format("%.1f KB", b / 1024.0);
        if (b < 1024L * 1024 * 1024) return String.format("%.2f MB", b / 1_048_576.0);
        return String.format("%.2f GB", b / 1_073_741_824.0);
    }

    /**
     * HTML de chip: rótulo cinza pequeno + valor em negrito.
     */
    private static String chip(String key, String value) {
        return "<html><span style='font-size:9px;color:gray'>"
                + key + "</span><br><b>" + value + "</b></html>";
    }

    // ══════════════════════════════════════════════════════════════
    // UI
    // ══════════════════════════════════════════════════════════════
    private void buildUI() {
        add(buildCenterPanel(), BorderLayout.CENTER);
        add(buildBottomBar(), BorderLayout.SOUTH);
    }

    private JPanel buildCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        imagePreviewPanel = new ImagePreviewPanel();

        JScrollPane scroll = new JScrollPane(imagePreviewPanel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getHorizontalScrollBar().setUnitIncrement(16);

        scroll.addMouseWheelListener(e -> {
            double delta = e.getWheelRotation() < 0 ? +ZOOM_STEP : -ZOOM_STEP;
            applyZoom(delta);
        });

        panel.add(scroll, BorderLayout.CENTER);
        panel.add(buildZoomBar(), BorderLayout.SOUTH);
        return panel;
    }

    // ── Barra de zoom ──────────────────────────────────────────────
    private JPanel buildZoomBar() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
                UIManager.getColor("Separator.foreground")));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 5));

        JButton zoomOut = iconBtn("−");
        JButton zoomReset = textBtn("100%");
        JButton zoomIn = iconBtn("+");
        zoomReset.setPreferredSize(new Dimension(75, 26));

        zoomOut.addActionListener(e -> applyZoom(-ZOOM_STEP));
        zoomIn.addActionListener(e -> applyZoom(+ZOOM_STEP));
        zoomReset.addActionListener(e -> resetZoom());

        zoomLabel = new JLabel("—");
        zoomLabel.setFont(UIConfig.FONT_DEFAULT);
        zoomLabel.setPreferredSize(new Dimension(50, 16));
        zoomLabel.setHorizontalAlignment(SwingConstants.CENTER);

        row.add(zoomOut);
        row.add(zoomReset);
        row.add(zoomIn);
        row.add(zoomLabel);

        wrapper.add(row, BorderLayout.CENTER);
        return wrapper;
    }

    // ── Barra de informações ───────────────────────────────────────
    private JPanel buildBottomBar() {
        JPanel bar = new JPanel(new BorderLayout(16, 0));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0,
                        UIManager.getColor("Separator.foreground")),
                new EmptyBorder(5, 12, 5, 12)));

        // Esquerda: nome do arquivo
        lblFilename = new JLabel(file.getName());
        lblFilename.setFont(UIConfig.FONT_DEFAULT_BOLD);

        // Centro: chips de metadados
        JPanel chips = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        chips.setOpaque(false);

        lblResolution = chipLabel("Resolução", "—");
        lblFileSize = chipLabel("Tamanho", "—");
        lblDpi = chipLabel("DPI", "—");
        lblBitDepth = chipLabel("Profundidade", "—");
        lblFormat = chipLabel("Formato", "—");

        chips.add(lblResolution);
        chips.add(vSep());
        chips.add(lblFileSize);
        chips.add(vSep());
        chips.add(lblDpi);
        chips.add(vSep());
        chips.add(lblBitDepth);
        chips.add(vSep());
        chips.add(lblFormat);

        bar.add(lblFilename, BorderLayout.WEST);
        bar.add(chips, BorderLayout.CENTER);
        return bar;
    }

    // ══════════════════════════════════════════════════════════════
    // Carregamento
    // ══════════════════════════════════════════════════════════════
    private void loadImage() {
        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() {
                String name = file.getName().toLowerCase();
                // Se for GIF, tratamos de forma diferente para não perder a animação
                if (name.endsWith(".gif")) {
                    try {
                        // Carrega os metadados básicos para a barra inferior
                        meta = readGifMetadata();
                        // Retorna o ImageIcon diretamente
                        return new ImageIcon(file.getAbsolutePath());
                    } catch (Exception e) {
                        System.err.println("[Viewer] Erro ao processar metadados do GIF: " + e.getMessage());
                        return new ImageIcon(file.getAbsolutePath());
                    }
                }
                // Para outros formatos, mantém a lógica original
                return readFile();
            }

            @Override
            protected void done() {
                try {
                    Object result = get();
                    if (result == null) {
                        lblFilename.setText(file.getName() + " — formato não suportado");
                        return;
                    }

                    if (result instanceof ImageIcon gifIcon) {
                        // Configura o painel para exibir o GIF animado
                        // NOTA: Para o zoom funcionar no GIF, seu ImagePreviewPanel precisará
                        // aceitar um Icon/ImageIcon em vez de apenas BufferedImage.
                        imagePreviewPanel.setImageIcon(gifIcon);

                        // Ajusta o zoom inicial
                        preview = null; // GIFs animados não usam o proxy BufferedImage
                        fitScale = 1.0;
                        zoomFactor = 1.0;
                        imagePreviewPanel.setZoom(1.0);
                        updateZoomLabel();
                        if (meta != null) updateInfoBar();

                    } else if (result instanceof BufferedImage bimg) {
                        preview = bimg;
                        recalcFitScale();
                        imagePreviewPanel.setImage(preview);

                        // Começa ajustado à janela, mas define o zoomFactor com essa escala inicial
                        zoomFactor = fitScale;
                        imagePreviewPanel.setZoom(zoomFactor);

                        updateZoomLabel();
                        if (meta != null) updateInfoBar();
                    }
                } catch (Exception e) {
                    lblFilename.setText(file.getName() + " — erro ao carregar");
                    System.err.println("[Viewer] " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    /**
     * Lê o arquivo e preenche {@code meta}.
     * Executado fora da EDT.
     */
    private BufferedImage readFile() {
        String name = file.getName().toLowerCase();
        BufferedImage original = null;

        try {
            if (name.endsWith(".psd") || name.endsWith(".psb")) {
                ImageCodec.PSDResult psd =
                        ImageCodec.readPSDFile(file.getAbsolutePath());
                if (psd != null && psd.image() != null) {
                    original = psd.image();
                    meta = new ImageMeta(
                            psd.width(), psd.height(),
                            file.length(),
                            72, 72,
                            psd.depth(),
                            "PSD · " + psd.colorMode().name()
                    );
                }
            } else {
                // Preferir RGBA para não perder transparência no preview
                ImageCodec.Result r = ImageCodec.builder(file.getAbsolutePath())
                        .pixelFormat(ImageCodec.PixelFormat.RGBA32)
                        .read();

                if (r != null && r.image() != null) {
                    original = r.image();
                    int[] dpi = readDpi();
                    int depth = switch (r.format()) {
                        case GRAY16, RGB48, RGBA64 -> 16;
                        default -> 8;
                    };
                    meta = new ImageMeta(
                            r.width(), r.height(),
                            file.length(),
                            dpi[0], dpi[1],
                            depth,
                            r.codecName().toUpperCase()
                    );
                }
            }
        } catch (ExceptionInInitializerError e) {
            // Causa raiz — é isso que precisamos ver
            System.err.println("[Viewer] ExceptionInInitializerError: "
                    + e.getCause());
            e.getCause().printStackTrace();
            return null;
        } catch (Exception e) {
            System.err.println("[Viewer] Erro: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
        return original != null ? buildProxy(original) : null;


    }

    private ImageMeta readGifMetadata() {
        int w = 0, h = 0;
        try (var stream = javax.imageio.ImageIO.createImageInputStream(file)) {
            var readers = javax.imageio.ImageIO.getImageReaders(stream);
            if (readers.hasNext()) {
                var reader = readers.next();
                reader.setInput(stream);
                w = reader.getWidth(0);
                h = reader.getHeight(0);
                reader.dispose();
            }
        } catch (Exception ignored) {
        }

        int[] dpi = readDpi();
        return new ImageMeta(
                w > 0 ? w : 100,
                h > 0 ? h : 100,
                file.length(),
                dpi[0], dpi[1],
                8, // GIFs são limitados a 8 bits por canal
                "GIF ANIMADO"
        );
    }

    /**
     * Reduz para proxy de preview se a imagem for muito grande.
     */
    private BufferedImage buildProxy(BufferedImage src) {
        int longest = Math.max(src.getWidth(), src.getHeight());
        if (longest <= PREVIEW_MAX) return src;

        double s = (double) PREVIEW_MAX / longest;
        int w = Math.max(1, (int) (src.getWidth() * s));
        int h = Math.max(1, (int) (src.getHeight() * s));

        boolean alpha = src.getColorModel().hasAlpha();
        BufferedImage out = new BufferedImage(w, h,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    // ══════════════════════════════════════════════════════════════
    // Zoom
    // ══════════════════════════════════════════════════════════════
    private void recalcFitScale() {
        if (preview == null) return;
        int availW = Math.max(1, imagePreviewPanel.getWidth() - 8);
        int availH = Math.max(1, imagePreviewPanel.getHeight() - 8);
        fitScale = Math.min(
                (double) availW / preview.getWidth(),
                (double) availH / preview.getHeight());
        fitScale = Math.min(fitScale, 1.0);
    }

    private void applyZoom(double delta) {
        // Restringe o zoom estritamente entre o mínimo (0.05) e o máximo (8.0)
        zoomFactor = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoomFactor + delta));
        imagePreviewPanel.setZoom(zoomFactor);
        updateZoomLabel();
    }

    private void resetZoom() {
        // 1.0 significa 100% do tamanho real nativo da imagem
        zoomFactor = 1.0;

        // Define o zoom diretamente como 1.0 (1 pixel da imagem = 1 pixel da tela)
        imagePreviewPanel.setZoom(zoomFactor);

        updateZoomLabel();
    }
    private void updateZoomLabel() {
        if (meta == null) return;
        // O zoomFactor agora rastreia diretamente a escala absoluta
        zoomLabel.setText(Math.round(zoomFactor * 100.0) + "%");
    }

    // ══════════════════════════════════════════════════════════════
    // Barra de informações
    // ══════════════════════════════════════════════════════════════
    private void updateInfoBar() {
        lblResolution.setText(chip("Resolução",
                meta.width() + " × " + meta.height() + " px"));
        lblFileSize.setText(chip("Tamanho", humanSize(meta.fileSizeBytes())));
        lblDpi.setText(chip("DPI",
                meta.dpiX() + (meta.dpiX() != meta.dpiY()
                        ? " × " + meta.dpiY() : "") + " dpi"));
        lblBitDepth.setText(chip("Profundidade", meta.bitDepth() + " bit"));
        lblFormat.setText(chip("Formato", meta.format()));
    }


    // ══════════════════════════════════════════════════════════════
    // Atalhos de teclado
    // ══════════════════════════════════════════════════════════════
    private void registerKeyBindings() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(e -> {
                    if (!isActive() || e.getID() != KeyEvent.KEY_PRESSED) return false;
                    return switch (e.getKeyCode()) {
                        case KeyEvent.VK_EQUALS, KeyEvent.VK_ADD -> {
                            applyZoom(+ZOOM_STEP);
                            yield true;
                        }
                        case KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> {
                            applyZoom(-ZOOM_STEP);
                            yield true;
                        }
                        case KeyEvent.VK_0 -> {
                            resetZoom();
                            yield true;
                        }
                        case KeyEvent.VK_ESCAPE -> {
                            dispose();
                            yield true;
                        }
                        default -> false;
                    };
                });
    }

    /**
     * Lê DPI via javax.imageio; retorna {72,72} se indisponível.
     */
    private int[] readDpi() {
        try (var stream = javax.imageio.ImageIO.createImageInputStream(file)) {
            var readers = javax.imageio.ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) return new int[]{72, 72};
            var reader = readers.next();
            reader.setInput(stream);
            var iioMeta = reader.getImageMetadata(0);
            reader.dispose();
            if (iioMeta == null) return new int[]{72, 72};

            var root = (javax.imageio.metadata.IIOMetadataNode)
                    iioMeta.getAsTree("javax_imageio_1.0");
            var hNodes = root.getElementsByTagName("HorizontalPixelSize");
            var vNodes = root.getElementsByTagName("VerticalPixelSize");
            if (hNodes.getLength() > 0 && vNodes.getLength() > 0) {
                float h = Float.parseFloat(
                        ((org.w3c.dom.Element) hNodes.item(0)).getAttribute("value"));
                float v = Float.parseFloat(
                        ((org.w3c.dom.Element) vNodes.item(0)).getAttribute("value"));
                int dx = Math.round(h * 25.4f);
                int dy = Math.round(v * 25.4f);
                return new int[]{dx > 0 ? dx : 72, dy > 0 ? dy : 72};
            }
        } catch (Exception ignored) {
        }
        return new int[]{72, 72};
    }

    private JLabel chipLabel(String key, String value) {
        JLabel l = new JLabel(chip(key, value));
        l.setFont(UIConfig.FONT_DEFAULT);
        return l;
    }

    private JButton iconBtn(String icon) {
        JButton b = new JButton(icon);
        b.setPreferredSize(new Dimension(30, 26));
        b.setFont(UIConfig.FONT_DEFAULT_LARGE);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusPainted(false);
        return b;
    }

    private JButton textBtn(String text) {
        JButton b = new JButton(text);
        b.setFont(UIConfig.FONT_DEFAULT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusPainted(false);
        return b;
    }

    private JSeparator vSep() {
        JSeparator s = new JSeparator(SwingConstants.VERTICAL);
        s.setPreferredSize(new Dimension(1, 28));
        return s;
    }

    // ── Metadados ─────────────────────────────────────────────────
    private record ImageMeta(
            int width,
            int height,
            long fileSizeBytes,
            int dpiX,
            int dpiY,
            int bitDepth,
            String format
    ) {
    }
}