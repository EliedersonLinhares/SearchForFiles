package com.esl.searchforfiles.actions.imageEditor;


import com.esl.searchforfiles.actions.imageEditor.actions.ImageAdjust.AdjustActionCardPanel;
import com.esl.searchforfiles.actions.imageEditor.actions.ImageAdjust.ImageAdjustAction;
import com.esl.searchforfiles.actions.imageEditor.actions.ImageCrop.CropActionCardPanel;
import com.esl.searchforfiles.actions.imageEditor.actions.ImageCrop.ImageCropAction;
import com.esl.searchforfiles.actions.imageEditor.actions.ImageCrop.ImagePreviewPanel;
import com.esl.searchforfiles.actions.imageEditor.actions.ImageResize.ImageResizeAction;
import com.esl.searchforfiles.actions.imageEditor.actions.ImageResize.ResizeActionCardPanel;
import com.esl.searchforfiles.actions.imageEditor.actions.ImageRotate.ImageRotateAction;
import com.esl.searchforfiles.actions.imageEditor.actions.ImageRotate.RotateActionCardPanel;
import com.esl.searchforfiles.actions.imageEditor.actions.ImageSketchFilter.ImageSketchAction;
import com.esl.searchforfiles.actions.imageEditor.actions.ImageSketchFilter.ImageSketchPanel;
import com.esl.searchforfiles.actions.imageEditor.saveActions.ActionPresetManager;
import com.esl.searchforfiles.configuration.UIConfig;
import com.esl.searchforfiles.ui.FileItemPanel;
import com.esl.searchforfiles.ui.ResultsPanel;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;

public class ImageEditorFrame extends JFrame {

    // Resolução máxima do proxy de preview (px no lado maior)
    private static final int PREVIEW_MAX_PX = 1200;
    private static final double ZOOM_STEP = 0.15;
    private static final double ZOOM_MIN = 0.1;
    private static final double ZOOM_MAX = 5.0;

    private final ResultsPanel resultsPanel;

    // ── Imagens ────────────────────────────────────────────────────
    private final List<File> imageFiles;
    private final List<BufferedImage> images = new ArrayList<>();  // originais
    private final List<BufferedImage> previews = new ArrayList<>();  // proxies reduzidos

    private final List<ActionCardPanel> actionCards = new ArrayList<>();
    private final ImageSaveManager saveManager = new ImageSaveManager(this);
    private final Set<File> editedFiles = new LinkedHashSet<>();

    private SwingWorker<BufferedImage, Void> previewWorker;
    private int currentIndex = 0;

    // ── Zoom ───────────────────────────────────────────────────────
    private double zoomFactor = 1.0;   // relativo ao fitScale calculado por imagem
    private double fitScale = 1.0;   // escala que faz a imagem caber no painel

    // ── Widgets ────────────────────────────────────────────────────
    private ImagePreviewPanel imagePreviewPanel;  // ← substitui JLabel
    private JLabel counterLabel;
    private JLabel zoomLabel;
    private JButton prevBtn, nextBtn;
    private JPanel actionsContainer;
    private JLabel lbl;

    private boolean editActionPerformed = false;
    private ActionPresetManager actionPresetManager;


    // ══════════════════════════════════════════════════════════════
    // Construtor
    // ══════════════════════════════════════════════════════════════
    public ImageEditorFrame(Window owner, ResultsPanel resultsPanel, List<File> imageFiles) {
        super("Editor de imagens — " + imageFiles.size() + " imagem(s) selecionada(s)");
        this.resultsPanel = resultsPanel;
        this.imageFiles = new ArrayList<>(imageFiles);

        actionPresetManager = new ActionPresetManager();

        if (owner != null) owner.setEnabled(false);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1400, 800);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (owner != null) owner.setEnabled(true);
                owner.toFront();
                invalidateEditedThumbs();
                if (editActionPerformed)
                    resultsPanel.getFileExplorerSwing().getSearchPanel().triggerSearch();
                resultsPanel.exitEditMode();
            }
        });



        loadImages();
        buildUI();
        showImage(0);
        setVisible(true);
        editActionPerformed = false;
    }

    // ══════════════════════════════════════════════════════════════
    // Carregamento de imagens
    // ══════════════════════════════════════════════════════════════
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

    private BufferedImage buildPreview(BufferedImage src) {
        int sw = src.getWidth(), sh = src.getHeight();
        int longest = Math.max(sw, sh);
        if (longest <= PREVIEW_MAX_PX) return src;

        double scale = (double) PREVIEW_MAX_PX / longest;
        int w = Math.max(1, (int) (sw * scale));
        int h = Math.max(1, (int) (sh * scale));

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

    // ══════════════════════════════════════════════════════════════
    // Construção da UI
    // ══════════════════════════════════════════════════════════════
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

        imagePreviewPanel = new ImagePreviewPanel();
        imagePreviewPanel.setBackground(new Color(30, 30, 30));

        // JScrollPane permite rolar quando zoom > fitScale
        JScrollPane imgScroll = new JScrollPane(imagePreviewPanel);
        imgScroll.setBorder(BorderFactory.createEmptyBorder());
        imgScroll.getVerticalScrollBar().setUnitIncrement(16);
        imgScroll.getHorizontalScrollBar().setUnitIncrement(16);
        imgScroll.setBackground(new Color(30, 30, 30));
        imgScroll.getViewport().setBackground(new Color(30, 30, 30));

        // Zoom pela rodinha — ignorado durante o modo crop
        imgScroll.addMouseWheelListener(e -> {
            if (imagePreviewPanel.isCropMode()) return;
            if (e.getWheelRotation() < 0) applyZoom(+ZOOM_STEP);
            else applyZoom(-ZOOM_STEP);
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
        counterLabel.setFont(UIConfig.FONT_DEFAULT);

        prevBtn.addActionListener(e -> navigate(-1));
        nextBtn.addActionListener(e -> navigate(+1));

        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 20));
        sep.setForeground(new Color(80, 80, 80));

        JButton zoomOutBtn = makeIconBtn("−");
        zoomOutBtn.setFont(UIConfig.FONT_DEFAULT);
        JButton zoomInBtn = makeIconBtn("+");
        zoomInBtn.setFont(UIConfig.FONT_DEFAULT);
        JButton zoomResetBtn = makeTextBtn("100%");
        zoomResetBtn.setPreferredSize(new Dimension(46, 28));
        zoomResetBtn.setFont(UIConfig.FONT_DEFAULT);

        zoomOutBtn.addActionListener(e -> applyZoom(-ZOOM_STEP));
        zoomInBtn.addActionListener(e -> applyZoom(+ZOOM_STEP));
        zoomResetBtn.addActionListener(e -> resetZoom());

        zoomLabel = new JLabel("100%");
        zoomLabel.setForeground(new Color(140, 140, 140));
        zoomLabel.setFont(UIConfig.FONT_DEFAULT);
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

        // ── Linha de ações (salvar / fechar) ──────────────────────
        JPanel closeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        closeRow.setBackground(new Color(42, 42, 42));
        closeRow.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(65, 65, 65)));

        JButton saveBtn = makeBarBtn("💾 Salvar", new Color(40, 130, 60));
        saveBtn.setFont(UIConfig.FONT_DEFAULT_BOLD);
        JButton saveAsBtn = makeBarBtn("💾 Salvar como", new Color(40, 100, 160));
        saveAsBtn.setFont(UIConfig.FONT_DEFAULT_BOLD);
        JButton closeBtn = new JButton("Fechar");
        closeBtn.setFont(UIConfig.FONT_DEFAULT_BOLD);

        closeBtn.setForeground(new Color(200, 200, 200));
        closeBtn.setBackground(new Color(60, 60, 60));
        closeBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(90, 90, 90)),
                BorderFactory.createEmptyBorder(3, 14, 3, 14)));
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        saveBtn.addActionListener(e -> saveAllImages2());
        saveAsBtn.addActionListener(e -> saveAllImagesAs2());
        closeBtn.addActionListener(e -> dispose());

        closeRow.add(saveBtn);
        closeRow.add(saveAsBtn);
        closeRow.add(closeBtn);

        wrapper.add(navRow, BorderLayout.CENTER);
        wrapper.add(closeRow, BorderLayout.SOUTH);
        return wrapper;
    }

    public ImagePreviewPanel getPreviewPanel() {
        return imagePreviewPanel;
    }

    // ── Painel direito ─────────────────────────────────────────────
    private JPanel buildRightPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(new Color(42, 42, 42));
        panel.setPreferredSize(new Dimension(120, 0));


        JPanel topBar = new JPanel(new GridLayout(2, 2, 6, 4));   // ← era (1,2,6,0)
        topBar.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        //  JPanel topBar = new JPanel(new GridLayout(1, 2, 6, 0));
        topBar.setBackground(new Color(42, 42, 42));
        // topBar.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

//        JButton addBtn = makeBarBtn("＋ Adicionar ação", new Color(33, 120, 200));
//        addBtn.setFont(UIConfig.FONT_DEFAULT_BOLD);
//        JButton clearBtn = makeBarBtn("🗑 Limpar todos", new Color(180, 60, 60));
//        clearBtn.setFont(UIConfig.FONT_DEFAULT_BOLD);
//
//        addBtn.addActionListener(e -> showAddActionMenu(addBtn));
//        clearBtn.addActionListener(e -> clearAllActions());
//
//        topBar.add(addBtn);
//        topBar.add(clearBtn);

        JButton addBtn = makeBarBtn("＋ Adicionar ação", new Color(33, 120, 200));
        addBtn.setFont(UIConfig.FONT_DEFAULT_BOLD);
        JButton clearBtn = makeBarBtn("🗑 Limpar todos", new Color(180, 60, 60));
        clearBtn.setFont(UIConfig.FONT_DEFAULT_BOLD);
        JButton savePreset = makeBarBtn("💾 Salvar preset", new Color(60, 110, 60));
        savePreset.setFont(UIConfig.FONT_DEFAULT_BOLD);
        JButton loadPreset = makeBarBtn("📂 Carregar preset", new Color(100, 80, 30));
        loadPreset.setFont(UIConfig.FONT_DEFAULT_BOLD);

        addBtn.addActionListener(e -> showAddActionMenu(addBtn));
        clearBtn.addActionListener(e -> clearAllActions());
        savePreset.addActionListener(e -> ActionPresetManager.savePreset(this, actionCards));
        loadPreset.addActionListener(e -> applyLoadedPreset());

        topBar.add(addBtn);
        topBar.add(clearBtn);
        topBar.add(savePreset);
        topBar.add(loadPreset);


        lbl = new JLabel("Ações");
        lbl.setForeground(new Color(120, 120, 120));
        lbl.setFont(UIConfig.FONT_DEFAULT);
        lbl.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        lbl.setBackground(new Color(42, 42, 42));
        lbl.setOpaque(true);

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
        north.add(lbl, BorderLayout.SOUTH);

        panel.add(north, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // ══════════════════════════════════════════════════════════════
    // Navegação
    // ══════════════════════════════════════════════════════════════
    private void navigate(int delta) {
        int next = currentIndex + delta;
        if (next < 0 || next >= imageFiles.size()) return;
        // Sai do modo crop ao trocar de imagem
        if (imagePreviewPanel.isCropMode()) imagePreviewPanel.exitCropMode();
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
            imagePreviewPanel.setImage(null);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            recalcFitScale(preview);
            updateZoomLabel();
            requestPreviewRefresh();
        });
    }

    // ══════════════════════════════════════════════════════════════
    // Zoom
    // ══════════════════════════════════════════════════════════════
    private void recalcFitScale(BufferedImage preview) {
        int availW = Math.max(1, imagePreviewPanel.getWidth() - 16);
        int availH = Math.max(1, imagePreviewPanel.getHeight() - 16);
        fitScale = Math.min(
                (double) availW / preview.getWidth(),
                (double) availH / preview.getHeight());
        fitScale = Math.min(fitScale, 1.0);
    }

    private void applyZoom(double delta) {
        zoomFactor = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoomFactor + delta));
        updateZoomLabel();
        // Atualiza apenas o zoom no painel — não precisa re-processar a imagem
        imagePreviewPanel.setZoom(fitScale * zoomFactor);
    }

    private void resetZoom() {
        zoomFactor = 1.0;
        BufferedImage preview = previews.get(currentIndex);
        if (preview != null) recalcFitScale(preview);
        updateZoomLabel();
        imagePreviewPanel.setZoom(fitScale * zoomFactor);
    }

    private void updateZoomLabel() {
        BufferedImage original = images.get(currentIndex);
        BufferedImage preview = previews.get(currentIndex);
        if (original == null || preview == null) return;

        double proxyRatio = (double) preview.getWidth() / original.getWidth();
        double realPercent = fitScale * zoomFactor / proxyRatio * 100;
        zoomLabel.setText(Math.round(realPercent) + "%");
    }

    // ══════════════════════════════════════════════════════════════
    // Preview (refresh assíncrono)
    // ══════════════════════════════════════════════════════════════
    public void requestPreviewRefresh() {
        if (previewWorker != null && !previewWorker.isDone())
            previewWorker.cancel(false);

        BufferedImage preview = previews.get(currentIndex);
        if (preview == null) return;

        previewWorker = new SwingWorker<>() {
            @Override
            protected BufferedImage doInBackground() {
                // Entrega a imagem SEM escalar — o painel aplica o zoom internamente
                return applyEnabledActions(preview);
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                try {
                    imagePreviewPanel.setImage(get());
                    imagePreviewPanel.setZoom(fitScale * zoomFactor);
                } catch (Exception ignored) {
                }
            }
        };
        previewWorker.execute();
    }

    // ══════════════════════════════════════════════════════════════
    // Pipeline de ações
    // ══════════════════════════════════════════════════════════════
    public BufferedImage applyEnabledActions(BufferedImage src) {
        BufferedImage result = src;
        for (ActionCardPanel card : actionCards) {
            ImageEditAction action = card.getAction();
            if (!action.isEnabled()) continue;
            result = switch (action) {
                case ImageAdjustAction adj -> adj.apply(result);
                case ImageRotateAction r -> r.apply(result);
                case ImageResizeAction s -> s.apply(result);
                case ImageCropAction c -> c.apply(result);
                case ImageSketchAction sk -> sk.apply(result);
                default -> result;
            };
        }
        return result;
    }

    public List<ImageEditAction> getEnabledActions() {
        return actionCards.stream()
                .map(ActionCardPanel::getAction)
                .filter(ImageEditAction::isEnabled)
                .toList();
    }

    // ══════════════════════════════════════════════════════════════
    // Menu de ações
    // ══════════════════════════════════════════════════════════════
    private void showAddActionMenu(Component anchor) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem adjustItem = new JMenuItem("Ajuste de imagem (brilho / contraste / gamma / saturação)");
        adjustItem.setFont(UIConfig.FONT_DEFAULT);
        adjustItem.addActionListener(e -> addAdjustAction());
        menu.add(adjustItem);

        JMenuItem rotateItem = new JMenuItem("Rotacionar / Inverter imagem");
        rotateItem.setFont(UIConfig.FONT_DEFAULT);
        rotateItem.addActionListener(e -> addImageRotateAction());
        menu.add(rotateItem);

        JMenuItem resizeItem = new JMenuItem("Redimensionar imagem");
        resizeItem.setFont(UIConfig.FONT_DEFAULT);
        resizeItem.addActionListener(e -> addImageResizeAction());
        menu.add(resizeItem);

        JMenuItem cropItem = new JMenuItem("Cortar (crop)");
        cropItem.setFont(UIConfig.FONT_DEFAULT);
        cropItem.addActionListener(e -> addImageCropAction());
        menu.add(cropItem);

        JMenuItem sketchItem = new JMenuItem("Filtro de desenho (sketch)");
        sketchItem.setFont(UIConfig.FONT_DEFAULT);
        sketchItem.addActionListener(e -> addImageSketchAction());
        menu.add(sketchItem);

        menu.addSeparator();

        JMenuItem sharpItem = new JMenuItem("Nitidez (sharpen)");
        sharpItem.addActionListener(e -> addAction(new ImageEditAction("Nitidez (sharpen)")));
        menu.add(sharpItem);

        JMenuItem waterItem = new JMenuItem("Aplicar marca d'água");
        waterItem.addActionListener(e -> addAction(new ImageEditAction("Aplicar marca d'água")));
        menu.add(waterItem);

        menu.show(anchor, 0, anchor.getHeight());
    }

    // ══════════════════════════════════════════════════════════════
    // Adicionar ações
    // ══════════════════════════════════════════════════════════════
    private void addAdjustAction() {
        if (actionCards.stream().anyMatch(c -> c.getAction() instanceof ImageAdjustAction)) return;

        ImageAdjustAction action = new ImageAdjustAction();
        AdjustActionCardPanel card = new AdjustActionCardPanel(action, this, this::removeAction);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setOnToggle(this::requestPreviewRefresh);
        registerCard(card);
    }

    private void addImageRotateAction() {
        if (actionCards.stream().anyMatch(c -> c.getAction() instanceof ImageRotateAction)) return;

        ImageRotateAction action = new ImageRotateAction();
        RotateActionCardPanel card = new RotateActionCardPanel(action, this, this::removeAction);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setOnToggle(this::requestPreviewRefresh);
        registerCard(card);
    }

    private void addImageResizeAction() {
        if (actionCards.stream().anyMatch(c -> c.getAction() instanceof ImageResizeAction)) return;

        ImageResizeAction action = new ImageResizeAction();
        ResizeActionCardPanel card = new ResizeActionCardPanel(action, this, this::removeAction);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setOnToggle(this::requestPreviewRefresh);
        registerCard(card);
    }

    private void addImageCropAction() {
        if (actionCards.stream().anyMatch(c -> c.getAction() instanceof ImageCropAction)) return;

        ImageCropAction action = new ImageCropAction();
        CropActionCardPanel card = new CropActionCardPanel(action, this, this::removeAction);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setOnToggle(this::requestPreviewRefresh);

        card.setCropModeCallback(cropAction -> {
            double aspect = cropAction.getAspectPreset().ratio();  // 0 = livre
            imagePreviewPanel.enterCropMode(aspect, (x, y, w, h) -> {
                // x,y,w,h estão em coords da imagem escalada exibida pelo painel;
                // precisamos converter de volta para coords do proxy original.
                BufferedImage displayed = imagePreviewPanel.getCurrentImage();
                BufferedImage proxy = previews.get(currentIndex);
                if (displayed == null || proxy == null) return;

                double scaleX = (double) proxy.getWidth() / displayed.getWidth();
                double scaleY = (double) proxy.getHeight() / displayed.getHeight();

                int px = (int) Math.round(x * scaleX);
                int py = (int) Math.round(y * scaleY);
                int pw = (int) Math.round(w * scaleX);
                int ph = (int) Math.round(h * scaleY);

                card.applyRegionFromPixels(px, py, pw, ph,
                        proxy.getWidth(), proxy.getHeight());
            });
        });

        registerCard(card);
    }

    private void addImageSketchAction() {
        if (actionCards.stream().anyMatch(c -> c.getAction() instanceof ImageSketchAction)) return;
        ImageSketchAction action = new ImageSketchAction();
        ImageSketchPanel card = new ImageSketchPanel(action, this, this::removeAction);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setOnToggle(this::requestPreviewRefresh);
        registerCard(card);
    }

    /**
     * Adiciona um card genérico (ações dummy / futuras).
     */
    private void addAction(ImageEditAction action) {
        ActionCardPanel card = new ActionCardPanel(action, this::removeAction);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        registerCard(card);
    }

    /**
     * Ponto único de registro: adiciona à lista, ao container e ao layout.
     */
    private void registerCard(ActionCardPanel card) {
        actionCards.add(card);
        actionsContainer.add(card);
        actionsContainer.add(Box.createVerticalStrut(6));
        actionsContainer.revalidate();
        actionsContainer.repaint();
        requestPreviewRefresh();
    }

    private void removeAction(ActionCardPanel card) {
        // Sai do modo crop se o card removido for de crop
        if (card.getAction() instanceof ImageCropAction && imagePreviewPanel.isCropMode())
            imagePreviewPanel.exitCropMode();

        actionCards.remove(card);
        Component[] comps = actionsContainer.getComponents();
        for (int i = 0; i < comps.length; i++) {
            if (comps[i] == card) {
                actionsContainer.remove(i);
                if (i < actionsContainer.getComponentCount())
                    actionsContainer.remove(i);   // strut
                break;
            }
        }
        actionsContainer.revalidate();
        actionsContainer.repaint();
        requestPreviewRefresh();

        if (actionCards.isEmpty()) {
           lbl.setText("Ações");
        }
    }

    private void clearAllActions() {
        if (actionCards.isEmpty()) return;
        int opt = JOptionPane.showConfirmDialog(this,
                "Remover todas as ações?", "Confirmar",
                JOptionPane.YES_NO_OPTION);
        if (opt != JOptionPane.YES_OPTION) return;

        if (imagePreviewPanel.isCropMode()) imagePreviewPanel.exitCropMode();
        actionCards.clear();
        actionsContainer.removeAll();
        actionsContainer.revalidate();
        actionsContainer.repaint();
        lbl.setText("Ações");
        requestPreviewRefresh();
    }

    // ══════════════════════════════════════════════════════════════
    // Salvar
    // ══════════════════════════════════════════════════════════════
    private void saveAllImages2() {
        saveManager.saveAll(images, imageFiles, this::applyEnabledActions, this::onSaveDone);
        editActionPerformed = true;
    }

    private void saveAllImagesAs2() {
        saveManager.saveAllAs(images, imageFiles,
                imageFiles.get(0).getParentFile(),
                this::applyEnabledActions, this::onSaveDone);
        editActionPerformed = true;
    }

    private void onSaveDone(List<File> savedFiles) {
        savedFiles.forEach(this::markAsEdited);
    }

    public void markAsEdited(File file) {
        if (file != null) editedFiles.add(file);
    }

    // ══════════════════════════════════════════════════════════════
    // Cache de thumbnails
    // ══════════════════════════════════════════════════════════════
    private void invalidateEditedThumbs() {
        if (editedFiles.isEmpty()) return;
        for (File f : editedFiles) {
            String absPath = f.getAbsolutePath();
            FileItemPanel.ICON_CACHE.keySet().removeIf(key -> key.contains(absPath));
            FileItemPanel.getThumbnailCacheManager()
                    .removeCachedThumbnail(f, FileItemPanel.ICON_CACHE.size());
        }
        if (resultsPanel != null)
            SwingUtilities.invokeLater(resultsPanel::refresh);
    }

    // ══════════════════════════════════════════════════════════════
    // Helpers de estilo
    // ══════════════════════════════════════════════════════════════
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


    /**
     * Carrega um preset do disco e reconstrói os cards na interface,
     * substituindo os que existem atualmente.
     */
    private void applyLoadedPreset() {
        List<ImageEditAction> loaded = ActionPresetManager.loadPreset(this);
        if (loaded == null || loaded.isEmpty()) return;

        // Pergunta se deve substituir ou somar às actions existentes
        int opt = JOptionPane.YES_OPTION;
        if (!actionCards.isEmpty()) {
            opt = JOptionPane.showConfirmDialog(this,
                    "Substituir as ações atuais pelas do preset?\n" +
                            "Escolha \"Não\" para adicionar ao final.",
                    "Carregar preset",
                    JOptionPane.YES_NO_CANCEL_OPTION);
            if (opt == JOptionPane.CANCEL_OPTION) return;
        }

        if (opt == JOptionPane.YES_OPTION) {
            // Limpa sem perguntar (já perguntamos acima)
            if (imagePreviewPanel.isCropMode()) imagePreviewPanel.exitCropMode();
            actionCards.clear();
            actionsContainer.removeAll();
        }

        // Recria cada card a partir da action desserializada
        for (ImageEditAction action : loaded) {
            switch (action) {
                case ImageAdjustAction a -> {
                    AdjustActionCardPanel card = new AdjustActionCardPanel(a, this, this::removeAction);
                    card.setAlignmentX(Component.LEFT_ALIGNMENT);
                    card.setOnToggle(this::requestPreviewRefresh);
                    registerCard(card);
                }
                case ImageRotateAction r -> {
                    RotateActionCardPanel card = new RotateActionCardPanel(r, this, this::removeAction);
                    card.setAlignmentX(Component.LEFT_ALIGNMENT);
                    card.setOnToggle(this::requestPreviewRefresh);
                    registerCard(card);
                }
                case ImageResizeAction s -> {
                    ResizeActionCardPanel card = new ResizeActionCardPanel(s, this, this::removeAction);
                    card.setAlignmentX(Component.LEFT_ALIGNMENT);
                    card.setOnToggle(this::requestPreviewRefresh);
                    registerCard(card);
                }
                case ImageCropAction c -> {
                    CropActionCardPanel card = new CropActionCardPanel(c, this, this::removeAction);
                    card.setAlignmentX(Component.LEFT_ALIGNMENT);
                    card.setOnToggle(this::requestPreviewRefresh);
                    card.setCropModeCallback(cropAction -> {
                        double aspect = cropAction.getAspectPreset().ratio();
                        imagePreviewPanel.enterCropMode(aspect, (x, y, w, h) -> {
                            BufferedImage displayed = imagePreviewPanel.getSourceImage();
                            BufferedImage proxy = previews.get(currentIndex);
                            if (displayed == null || proxy == null) return;
                            double scaleX = (double) proxy.getWidth() / displayed.getWidth();
                            double scaleY = (double) proxy.getHeight() / displayed.getHeight();
                            card.applyRegionFromPixels(
                                    (int) Math.round(x * scaleX), (int) Math.round(y * scaleY),
                                    (int) Math.round(w * scaleX), (int) Math.round(h * scaleY),
                                    proxy.getWidth(), proxy.getHeight());
                        });
                    });
                    registerCard(card);
                }
                case ImageSketchAction sk -> {
                    ImageSketchPanel card = new ImageSketchPanel(sk, this, this::removeAction);
                    card.setAlignmentX(Component.LEFT_ALIGNMENT);
                    card.setOnToggle(this::requestPreviewRefresh);
                    registerCard(card);
                }
                default -> { /* tipo desconhecido — ignora */ }
            }
        }
        lbl.setText("Ações - " + actionPresetManager.getFile().getName());
        requestPreviewRefresh();

    }

}
