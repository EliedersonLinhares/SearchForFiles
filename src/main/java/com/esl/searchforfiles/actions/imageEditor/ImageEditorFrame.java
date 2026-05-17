package com.esl.searchforfiles.actions.imageEditor;


import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class ImageEditorFrame extends JFrame {

    // ── Imagens ────────────────────────────────────────────────────
    private final List<File>          imageFiles;
    private final List<BufferedImage> images      = new ArrayList<>(); // originais
    private final List<BufferedImage> previews    = new ArrayList<>(); // proxies reduzidos
    private int currentIndex = 0;

    // Resolução máxima do proxy de preview (px no lado maior)
    private static final int PREVIEW_MAX_PX = 1200;

    // ── Zoom ───────────────────────────────────────────────────────
    private double zoomFactor = 1.0;   // relativo ao fitScale calculado por imagem
    private double fitScale   = 1.0;   // escala que faz a imagem caber no painel
    private static final double ZOOM_STEP = 0.15;
    private static final double ZOOM_MIN  = 0.1;
    private static final double ZOOM_MAX  = 5.0;

    // ── Painel esquerdo ────────────────────────────────────────────
    private JLabel  imageLabel;
    private JLabel  counterLabel;
    private JLabel  zoomLabel;
    private JButton prevBtn, nextBtn;

    // ── Painel direito (ações) ─────────────────────────────────────
    private JPanel actionsContainer;   // BoxLayout vertical
    private final List<ActionCardPanel> actionCards = new ArrayList<>();

    public ImageEditorFrame(Window owner, List<File> imageFiles) {
        super("Editor de imagens — " + imageFiles.size() + " imagem(s) selecionada(s)");
        this.imageFiles = new ArrayList<>(imageFiles);

        // 1 — bloqueia a janela pai enquanto este frame estiver aberto
        if (owner != null) owner.setEnabled(false);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1400, 800);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        // Reabilita o owner ao fechar
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (owner != null) owner.setEnabled(true);
                owner.toFront();
            }
        });

        loadImages();
        buildUI();
        showImage(0);
        setVisible(true);
    }

    // ── Carregamento assíncrono das imagens ────────────────────────
    private void loadImages() {
        for (File f : imageFiles) {
            try {
                BufferedImage img = ImageIO.read(f);
                images.add(img);
                previews.add(img != null ? buildPreview(img) : null);
            } catch (Exception e) {
                images.add(null);
                previews.add(null);
            }
        }
    }

    /**
     * Gera um proxy reduzido para uso durante o zoom/navegação.
     * O original permanece intacto para aplicação das ações.
     */
    private BufferedImage buildPreview(BufferedImage src) {
        int sw = src.getWidth(), sh = src.getHeight();
        int longest = Math.max(sw, sh);
        if (longest <= PREVIEW_MAX_PX) return src; // já é pequena, reutiliza

        double scale = (double) PREVIEW_MAX_PX / longest;
        int w = Math.max(1, (int)(sw * scale));
        int h = Math.max(1, (int)(sh * scale));

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        return out;
    }

    // ── Construção da UI ───────────────────────────────────────────
    private void buildUI() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildLeftPanel(), buildRightPanel());
        split.setDividerLocation(640);
        split.setResizeWeight(1.0);
        split.setDividerSize(4);
        add(split, BorderLayout.CENTER);
    }

    // ── Painel esquerdo ────────────────────────────────────────────
    private JPanel buildLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(38, 38, 38));

        // Área da imagem com scroll (necessário para zoom > 1)
        imageLabel = new JLabel("", SwingConstants.CENTER);
        imageLabel.setBackground(new Color(30, 30, 30));
        imageLabel.setOpaque(true);
        imageLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JScrollPane imgScroll = new JScrollPane(imageLabel);
        imgScroll.setBorder(BorderFactory.createEmptyBorder());
        imgScroll.getVerticalScrollBar().setUnitIncrement(16);
        imgScroll.getHorizontalScrollBar().setUnitIncrement(16);
        imgScroll.setBackground(new Color(30, 30, 30));
        imgScroll.getViewport().setBackground(new Color(30, 30, 30));

        // Zoom pela rodinha do mouse
        imgScroll.addMouseWheelListener(e -> {
            if (e.isControlDown() || true) { // sempre ativa no painel de imagem
                if (e.getWheelRotation() < 0) applyZoom(+ZOOM_STEP);
                else                           applyZoom(-ZOOM_STEP);
            }
        });

        panel.add(imgScroll, BorderLayout.CENTER);
        panel.add(buildNavBar(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildNavBar() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(new Color(42, 42, 42));
        wrapper.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(65, 65, 65)));

        // ── Linha de navegação + zoom ──────────────────────────────
        JPanel navRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 6));
        navRow.setBackground(new Color(42, 42, 42));

        prevBtn = makeIconBtn("◀");
        nextBtn = makeIconBtn("▶");

        counterLabel = new JLabel("1 / " + imageFiles.size());
        counterLabel.setForeground(new Color(160, 160, 160));
        counterLabel.setFont(counterLabel.getFont().deriveFont(12f));

        prevBtn.addActionListener(e -> navigate(-1));
        nextBtn.addActionListener(e -> navigate(+1));

        // Separador visual
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 20));
        sep.setForeground(new Color(80, 80, 80));

        // Botões de zoom
        JButton zoomOutBtn = makeIconBtn("−");
        JButton zoomInBtn  = makeIconBtn("+");
        zoomOutBtn.addActionListener(e -> applyZoom(-ZOOM_STEP));
        zoomInBtn .addActionListener(e -> applyZoom(+ZOOM_STEP));

        JButton zoomResetBtn = makeTextBtn("100%");
        zoomResetBtn.setPreferredSize(new Dimension(46, 28));
        zoomResetBtn.addActionListener(e -> resetZoom());

        zoomLabel = new JLabel("100%");
        zoomLabel.setForeground(new Color(140, 140, 140));
        zoomLabel.setFont(zoomLabel.getFont().deriveFont(11f));
        zoomLabel.setPreferredSize(new Dimension(40, 16));
        zoomLabel.setHorizontalAlignment(SwingConstants.CENTER);

        navRow.add(prevBtn);
        navRow.add(counterLabel);
        navRow.add(nextBtn);
        navRow.add(sep);
        navRow.add(zoomOutBtn);
        navRow.add(zoomResetBtn);
        navRow.add(zoomInBtn);
        navRow.add(zoomLabel);

        // ── Linha de fechar ────────────────────────────────────────
        JPanel closeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        closeRow.setBackground(new Color(42, 42, 42));
        closeRow.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(65, 65, 65)));

        JButton closeBtn = new JButton("Fechar");
        closeBtn.setForeground(new Color(200, 200, 200));
        closeBtn.setBackground(new Color(60, 60, 60));
        closeBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(90, 90, 90)),
                BorderFactory.createEmptyBorder(3, 14, 3, 14)));
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dispose());
        closeRow.add(closeBtn);

        wrapper.add(navRow,   BorderLayout.CENTER);
        wrapper.add(closeRow, BorderLayout.SOUTH);
        return wrapper;
    }

    private JButton makeIconBtn(String icon) {
        JButton btn = new JButton(icon);
        btn.setPreferredSize(new Dimension(30, 28));
        btn.setFont(btn.getFont().deriveFont(13f));
        btn.setForeground(new Color(200, 200, 200));
        btn.setBackground(new Color(60, 60, 60));
        btn.setBorder(BorderFactory.createLineBorder(new Color(90, 90, 90)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton makeTextBtn(String text) {
        JButton btn = new JButton(text);
        btn.setFont(btn.getFont().deriveFont(11f));
        btn.setForeground(new Color(180, 180, 180));
        btn.setBackground(new Color(55, 55, 55));
        btn.setBorder(BorderFactory.createLineBorder(new Color(85, 85, 85)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // ── Painel direito ─────────────────────────────────────────────
    private JPanel buildRightPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(new Color(42, 42, 42));
        panel.setPreferredSize(new Dimension(120, 0));

        // Botões do topo
        JPanel topBar = new JPanel(new GridLayout(1, 2, 6, 0));
        topBar.setBackground(new Color(42, 42, 42));
        topBar.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JButton addBtn = makeBarBtn("＋ Adicionar ação", new Color(33, 120, 200));
        addBtn.addActionListener(e -> showAddActionMenu(addBtn));

        JButton clearBtn = makeBarBtn("🗑 Limpar todos", new Color(180, 60, 60));
        clearBtn.addActionListener(e -> clearAllActions());

        topBar.add(addBtn);
        topBar.add(clearBtn);

        // Label
        JLabel lbl = new JLabel("  Ações");
        lbl.setForeground(new Color(120, 120, 120));
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 10f));
        lbl.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        lbl.setBackground(new Color(42, 42, 42));
        lbl.setOpaque(true);

        // Container de ações com BoxLayout
        actionsContainer = new JPanel();
        actionsContainer.setLayout(new BoxLayout(actionsContainer, BoxLayout.Y_AXIS));
        actionsContainer.setBackground(new Color(45, 45, 45));

        JScrollPane scroll = new JScrollPane(actionsContainer);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(12);
        scroll.setBackground(new Color(45, 45, 45));
        scroll.getViewport().setBackground(new Color(45, 45, 45));

        JPanel north = new JPanel(new BorderLayout());
        north.setBackground(new Color(42, 42, 42));
        north.add(topBar, BorderLayout.NORTH);
        north.add(lbl,    BorderLayout.SOUTH);

        panel.add(north,  BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JButton makeBarBtn(String text, Color borderColor) {
        JButton btn = new JButton(text);
        btn.setFont(btn.getFont().deriveFont(12f));
        btn.setForeground(Color.WHITE);
        btn.setBackground(new Color(55, 55, 55));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void navigate(int delta) {
        int next = currentIndex + delta;
        if (next < 0 || next >= imageFiles.size()) return;
        zoomFactor = 1.0;
        showImage(next);
    }

    private void showImage(int index) {
        currentIndex = index;
        counterLabel.setText((index + 1) + " / " + imageFiles.size());
        prevBtn.setEnabled(index > 0);
        nextBtn.setEnabled(index < imageFiles.size() - 1);

        BufferedImage preview = previews.get(index);
        if (preview == null) {
            imageLabel.setIcon(null);
            imageLabel.setText("Não foi possível carregar: " + imageFiles.get(index).getName());
            imageLabel.setForeground(new Color(180, 80, 80));
            return;
        }

        imageLabel.setText(null);
        SwingUtilities.invokeLater(() -> {
            recalcFitScale(preview);
            imageLabel.setIcon(scaledIcon(preview));
            updateZoomLabel();
        });
    }

    /** Recalcula a escala que faz a imagem caber inteiramente no viewport. */
    private void recalcFitScale(BufferedImage preview) {
        Container viewport = imageLabel.getParent();
        int availW = viewport != null ? viewport.getWidth()  - 16 : 600;
        int availH = viewport != null ? viewport.getHeight() - 16 : 460;
        if (availW <= 0) availW = 600;
        if (availH <= 0) availH = 460;

        fitScale = Math.min(
                (double) availW / preview.getWidth(),
                (double) availH / preview.getHeight());
        fitScale = Math.min(fitScale, 1.0);
    }

    // ── Zoom ───────────────────────────────────────────────────────
    private void applyZoom(double delta) {
        zoomFactor = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoomFactor + delta));
        BufferedImage preview = previews.get(currentIndex);
        if (preview != null) imageLabel.setIcon(scaledIcon(preview));
        updateZoomLabel();
    }

    private void resetZoom() {
        zoomFactor = 1.0;
        BufferedImage preview = previews.get(currentIndex);
        if (preview != null) {
            recalcFitScale(preview);
            imageLabel.setIcon(scaledIcon(preview));
        }
        updateZoomLabel();
    }

    private void updateZoomLabel() {
        // Mostra o percentual em relação ao tamanho ORIGINAL, não ao proxy
        BufferedImage original = images.get(currentIndex);
        BufferedImage preview  = previews.get(currentIndex);
        if (original == null || preview == null) return;

        double proxyRatio = (double) preview.getWidth() / original.getWidth();
        double realPercent = fitScale * zoomFactor / proxyRatio * 100;
        zoomLabel.setText(Math.round(realPercent) + "%");
    }

    /**
     * Escala o PROXY para exibição — muito mais rápido que escalar o original.
     * Escala efetiva = fitScale × zoomFactor aplicada sobre o preview.
     */
    private ImageIcon scaledIcon(BufferedImage preview) {
        double effective = fitScale * zoomFactor;
        int w = Math.max(1, (int)(preview.getWidth()  * effective));
        int h = Math.max(1, (int)(preview.getHeight() * effective));

        // Para reduções usa getScaledInstance (melhor qualidade);
        // para ampliações desenha direto com BILINEAR (mais rápido).
        Image scaled;
        if (effective < 1.0) {
            scaled = preview.getScaledInstance(w, h, Image.SCALE_SMOOTH);
        } else {
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = out.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(preview, 0, 0, w, h, null);
            g2.dispose();
            scaled = out;
        }
        return new ImageIcon(scaled);
    }

    // ── Ações ──────────────────────────────────────────────────────

    /** Popup com as ações disponíveis (dummies por enquanto). */
    private void showAddActionMenu(Component anchor) {
        String[] dummies = {
                "Redimensionar",
                "Converter formato",
                "Ajustar brilho / contraste",
                "Aplicar marca d'água",
                "Rotacionar / inverter",
                "Cortar (crop)",
                "Ajustar saturação",
                "Nitidez (sharpen)"
        };

        JPopupMenu menu = new JPopupMenu();
        for (String name : dummies) {
            JMenuItem item = new JMenuItem(name);
            item.addActionListener(e -> addAction(new ImageEditAction(name)));
            menu.add(item);
        }
        menu.show(anchor, 0, anchor.getHeight());
    }

    private void addAction(ImageEditAction action) {
        ActionCardPanel card = new ActionCardPanel(action, this::removeAction);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        actionCards.add(card);
        actionsContainer.add(card);
        actionsContainer.add(Box.createVerticalStrut(6));
        actionsContainer.revalidate();
        actionsContainer.repaint();
    }

    private void removeAction(ActionCardPanel card) {
        actionCards.remove(card);
        // Remove o card e o strut que vem após ele
        Component[] comps = actionsContainer.getComponents();
        for (int i = 0; i < comps.length; i++) {
            if (comps[i] == card) {
                actionsContainer.remove(i);                     // card
                if (i < actionsContainer.getComponentCount())
                    actionsContainer.remove(i);                 // strut
                break;
            }
        }
        actionsContainer.revalidate();
        actionsContainer.repaint();
    }

    private void clearAllActions() {
        if (actionCards.isEmpty()) return;
        int opt = JOptionPane.showConfirmDialog(this,
                "Remover todas as ações?", "Confirmar",
                JOptionPane.YES_NO_OPTION);
        if (opt != JOptionPane.YES_OPTION) return;
        actionCards.clear();
        actionsContainer.removeAll();
        actionsContainer.revalidate();
        actionsContainer.repaint();
    }

    /** Retorna as ações habilitadas para uso futuro pelo pipeline de edição. */
    public List<ImageEditAction> getEnabledActions() {
        return actionCards.stream()
                .map(ActionCardPanel::getAction)
                .filter(ImageEditAction::isEnabled)
                .toList();
    }
}

