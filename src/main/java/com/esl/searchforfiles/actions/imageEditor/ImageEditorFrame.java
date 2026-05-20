package com.esl.searchforfiles.actions.imageEditor;


import com.esl.searchforfiles.actions.imageEditor.actions.AdjustActionCardPanel;
import com.esl.searchforfiles.actions.imageEditor.actions.ImageAdjustAction;
import com.esl.searchforfiles.ui.ResultsPanel;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

public class ImageEditorFrame extends JFrame {

    private final ResultsPanel resultsPanel;

    // Resolução máxima do proxy de preview (px no lado maior)
    private static final int PREVIEW_MAX_PX = 1200;
    private static final double ZOOM_STEP = 0.15;
    private static final double ZOOM_MIN = 0.1;
    private static final double ZOOM_MAX = 5.0;
    // ── Imagens ────────────────────────────────────────────────────
    private final List<File> imageFiles;
    private final List<BufferedImage> images = new ArrayList<>(); // originais
    private final List<BufferedImage> previews = new ArrayList<>(); // proxies reduzidos
    private final List<ActionCardPanel> actionCards = new ArrayList<>();
    // ── Campo novo (junto aos outros campos da classe) ─────────────────
    private SwingWorker<ImageIcon, Void> previewWorker; // worker em curso
    private int currentIndex = 0;
    // ── Zoom ───────────────────────────────────────────────────────
    private double zoomFactor = 1.0;   // relativo ao fitScale calculado por imagem
    private double fitScale = 1.0;   // escala que faz a imagem caber no painel
    // ── Painel esquerdo ────────────────────────────────────────────
    private JLabel imageLabel;
    private JLabel counterLabel;
    private JLabel zoomLabel;
    private JButton prevBtn, nextBtn;
    // ── Painel direito (ações) ─────────────────────────────────────
    private JPanel actionsContainer;   // BoxLayout vertical
    private final ImageSaveManager saveManager = new ImageSaveManager(this);

    public ImageEditorFrame(Window owner, ResultsPanel resultsPanel, List<File> imageFiles) {
        super("Editor de imagens — " + imageFiles.size() + " imagem(s) selecionada(s)");
        this.resultsPanel = resultsPanel;
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
//                if(!resultsPanel.getFileExplorerSwing().getController().getMonitoringService().isMonitoring()){
//                    resultsPanel.getFileExplorerSwing().getController().getMonitoringService().startMonitoring();
//                }
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
                else applyZoom(-ZOOM_STEP);
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
        JButton zoomInBtn = makeIconBtn("+");
        zoomOutBtn.addActionListener(e -> applyZoom(-ZOOM_STEP));
        zoomInBtn.addActionListener(e -> applyZoom(+ZOOM_STEP));

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

        JPanel closeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        closeRow.setBackground(new Color(42, 42, 42));
        closeRow.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(65, 65, 65)));

        JButton saveBtn     = makeBarBtn("💾 Salvar",      new Color(40, 130, 60));
        JButton saveAsBtn   = makeBarBtn("💾 Salvar como", new Color(40, 100, 160));
        JButton closeBtn    = new JButton("Fechar");

        // estilo do closeBtn (igual ao original)
        closeBtn.setForeground(new Color(200, 200, 200));
        closeBtn.setBackground(new Color(60, 60, 60));
        closeBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(90, 90, 90)),
                BorderFactory.createEmptyBorder(3, 14, 3, 14)));
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        saveBtn  .addActionListener(e -> saveAllImages2());
        saveAsBtn.addActionListener(e -> saveAllImagesAs2());
        closeBtn .addActionListener(e -> dispose());

        closeRow.add(saveBtn);
        closeRow.add(saveAsBtn);
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
        north.add(lbl, BorderLayout.SOUTH);

        panel.add(north, BorderLayout.NORTH);
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

//        imageLabel.setText(null);
//        SwingUtilities.invokeLater(() -> {
//            recalcFitScale(preview);
//            imageLabel.setIcon(scaledIcon(preview));
//            updateZoomLabel();
//        });
        imageLabel.setText(null);
        SwingUtilities.invokeLater(() -> {
            recalcFitScale(preview);
            updateZoomLabel();
            requestPreviewRefresh();   // ← linha adicionada (substitui scaledIcon direto)
        });
    }

    /**
     * Recalcula a escala que faz a imagem caber inteiramente no viewport.
     */
    private void recalcFitScale(BufferedImage preview) {
        Container viewport = imageLabel.getParent();
        int availW = viewport != null ? viewport.getWidth() - 16 : 600;
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
        BufferedImage preview = previews.get(currentIndex);
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
        int w = Math.max(1, (int) (preview.getWidth() * effective));
        int h = Math.max(1, (int) (preview.getHeight() * effective));

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

    /**
     * Popup com as ações disponíveis (dummies por enquanto).
     */
    private void showAddActionMenu(Component anchor) {
        JPopupMenu menu = new JPopupMenu();

        // ── Ação concreta já implementada ─────────────────────────────
        JMenuItem adjustItem = new JMenuItem("Ajuste de imagem (brilho / contraste / gamma / saturação)");
        adjustItem.addActionListener(e -> addAdjustAction());
        menu.add(adjustItem);

        menu.addSeparator();

        String[] dummies = {
                "Redimensionar",
                "Converter formato",
                "Aplicar marca d'água",
                "Rotacionar / inverter",
                "Cortar (crop)",
                "Nitidez (sharpen)"
        };


        for (String name : dummies) {
            JMenuItem item = new JMenuItem(name);
            item.addActionListener(e -> addAction(new ImageEditAction(name)));
            menu.add(item);
        }
        menu.show(anchor, 0, anchor.getHeight());
    }


    /**
     * Cria e adiciona um card de ajuste de imagem.
     * Só permite uma instância por vez (basta descomentar o guard se desejar isso).
     */
    private void addAdjustAction() {
        // Guard opcional: impede duplicatas
        boolean alreadyHas = actionCards.stream()
                .anyMatch(c -> c.getAction() instanceof ImageAdjustAction);
        if (alreadyHas) {
            JOptionPane.showMessageDialog(this, "Já existe um ajuste de imagem na lista.");
            return;
        }

        ImageAdjustAction    action = new ImageAdjustAction();
        AdjustActionCardPanel card  = new AdjustActionCardPanel(
                action, this, this::removeAction);   // ← "this" é o frame
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setOnToggle(this::requestPreviewRefresh);
        actionCards.add(card);
        actionsContainer.add(card);
        actionsContainer.add(Box.createVerticalStrut(6));
        actionsContainer.revalidate();
        actionsContainer.repaint();
    }


// ── Método público chamado pelos cards ao mudar qualquer parâmetro ─

    /**
     * Agenda um refresh do preview da imagem atual.
     * Cancela o worker anterior para evitar enfileiramento de tarefas.
     */
    public void requestPreviewRefresh() {
        if (previewWorker != null && !previewWorker.isDone()) {
            previewWorker.cancel(false);   // descarta resultado, não interrompe thread
        }

        BufferedImage preview = previews.get(currentIndex);
        if (preview == null) return;

        previewWorker = new SwingWorker<>() {
            @Override
            protected ImageIcon doInBackground() {
                // Aplica ações sobre o proxy (rápido o suficiente para tempo real)
                BufferedImage result = applyEnabledActions(preview);
                return scaledIcon(result);
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                try {
                    imageLabel.setIcon(get());
                } catch (Exception ignored) {
                }
            }
        };
        previewWorker.execute();
    }


// ── applyEnabledActions  (substitui o método que estava no artifact anterior) ─

    /**
     * Aplica, em ordem, todas as ações habilitadas sobre a imagem fornecida.
     * Usado tanto no preview (sobre o proxy) quanto no salvamento (sobre o original).
     */
    public BufferedImage applyEnabledActions(BufferedImage src) {
        BufferedImage result = src;
        for (ActionCardPanel card : actionCards) {
            ImageEditAction action = card.getAction();
            if (!action.isEnabled()) continue;

            if (action instanceof ImageAdjustAction adj) {
                result = adj.apply(result);
            }
            // else if (action instanceof ResizeAction r) { result = r.apply(result); }
        }
        return result;
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

        requestPreviewRefresh();
    }

    /**
     * Retorna as ações habilitadas para uso futuro pelo pipeline de edição.
     */
    public List<ImageEditAction> getEnabledActions() {
        return actionCards.stream()
                .map(ActionCardPanel::getAction)
                .filter(ImageEditAction::isEnabled)
                .toList();
    }


// ═══════════════════════════════════════════════════════════════════
// 2. saveCurrentImage()  —  sobrescreve o arquivo original
// ═══════════════════════════════════════════════════════════════════

    private void saveCurrentImage() {
        File target = imageFiles.get(currentIndex);

        int confirm = JOptionPane.showConfirmDialog(this,
                "<html>Sobrescrever o arquivo original?<br><b>" + target.getName() + "</b></html>",
                "Confirmar salvamento",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        // Detecta formato pelo nome do arquivo original
        String fmt = extensionOf(target.getName());
        if (!fmt.equals("jpg") && !fmt.equals("jpeg") && !fmt.equals("png")) {
            JOptionPane.showMessageDialog(this,
                    "Formato não suportado para escrita: " + fmt.toUpperCase()
                            + ".\nUse \"Salvar como\" para escolher JPEG ou PNG.",
                    "Formato inválido", JOptionPane.ERROR_MESSAGE);
            return;
        }

        writeImage(images.get(currentIndex), target, fmt.equals("png") ? "png" : "jpeg");
    }


// ═══════════════════════════════════════════════════════════════════
// 3. saveCurrentImageAs()  —  escolha de destino e formato
// ═══════════════════════════════════════════════════════════════════

    private void saveCurrentImageAs() {
        // ── Diálogo de formato ─────────────────────────────────────────
        String[] options = {"JPEG", "PNG"};
        int fmtChoice = JOptionPane.showOptionDialog(this,
                "Escolha o formato de saída:",
                "Salvar como",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        if (fmtChoice < 0) return;                      // cancelado
        String fmt      = fmtChoice == 0 ? "jpeg" : "png";
        String ext      = fmtChoice == 0 ? "jpg"  : "png";

        // ── Seletor de arquivo ─────────────────────────────────────────
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Salvar como");
        chooser.setSelectedFile(new File(
                imageFiles.get(currentIndex).getParent(),
                baseName(imageFiles.get(currentIndex).getName()) + "_edit." + ext));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                options[fmtChoice] + " (*." + ext + ")", ext));
        chooser.setAcceptAllFileFilterUsed(false);

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File chosen = chooser.getSelectedFile();

        // Garante extensão correta
        if (!chosen.getName().toLowerCase().endsWith("." + ext)) {
            chosen = new File(chosen.getAbsolutePath() + "." + ext);
        }

        // ── Verifica sobrescrita na pasta dos originais ────────────────
        File originalDir = imageFiles.get(currentIndex).getParentFile();
        if (chosen.getParentFile().equals(originalDir) && chosen.exists()) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "<html>O arquivo já existe na pasta dos originais:<br><b>"
                            + chosen.getName() + "</b><br>Deseja sobrescrever?</html>",
                    "Confirmar sobrescrita",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
        } else if (chosen.exists()) {
            // Arquivo existe fora da pasta dos originais — aviso genérico
            int confirm = JOptionPane.showConfirmDialog(this,
                    "<html>O arquivo já existe:<br><b>" + chosen.getName()
                            + "</b><br>Deseja sobrescrever?</html>",
                    "Confirmar sobrescrita",
                    JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
        }

        writeImage(images.get(currentIndex), chosen, fmt);
    }


// ═══════════════════════════════════════════════════════════════════
// 4. writeImage()  —  aplica ações e grava em disco (SwingWorker)
// ═══════════════════════════════════════════════════════════════════

    /**
     * Aplica as ações habilitadas sobre a imagem ORIGINAL (não o proxy)
     * e salva no arquivo de destino. Roda em background para não travar a EDT.
     */
    private void writeImage(BufferedImage original, File dest, String format) {
        // Diálogo de progresso simples (sem barra — só bloqueia interação)
        JDialog progress = new JDialog(this, "Salvando…", false);
        progress.setSize(280, 80);
        progress.setLocationRelativeTo(this);
        progress.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        progress.setResizable(false);
        JLabel lbl = new JLabel("Aplicando efeitos e salvando…", SwingConstants.CENTER);
        lbl.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        progress.add(lbl);
        progress.setVisible(true);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                BufferedImage result = applyEnabledActions(original);

                // ImageIO exige TYPE_INT_RGB para JPEG (sem canal alpha)
                if (format.equals("jpeg")) {
                    BufferedImage rgb = new BufferedImage(
                            result.getWidth(), result.getHeight(),
                            BufferedImage.TYPE_INT_RGB);
                    Graphics2D g2 = rgb.createGraphics();
                    g2.drawImage(result, 0, 0, null);
                    g2.dispose();
                    result = rgb;
                }

                if (!ImageIO.write(result, format, dest)) {
                    throw new IOException("ImageIO não encontrou writer para: " + format);
                }
                return null;
            }

            @Override
            protected void done() {
                progress.dispose();
                try {
                    get();   // relança exceção se houver
                    JOptionPane.showMessageDialog(ImageEditorFrame.this,
                            "Arquivo salvo:\n" + dest.getAbsolutePath(),
                            "Salvo com sucesso", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ImageEditorFrame.this,
                            "Erro ao salvar:\n" + ex.getCause().getMessage(),
                            "Erro", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }


// ═══════════════════════════════════════════════════════════════════
// 5. Helpers de nome de arquivo
// ═══════════════════════════════════════════════════════════════════

    /** "foto.JPG" → "jpg" */
    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    /** "foto.jpg" → "foto" */
    private static String baseName(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(0, dot) : filename;
    }


    private void saveAllImages() {
        int total = imageFiles.size();

        int confirm = JOptionPane.showConfirmDialog(this,
                "<html>Sobrescrever <b>" + total + "</b> arquivo(s) original(is) com as alterações?</html>",
                "Confirmar salvamento",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        // Valida formatos antes de começar
        List<String> unsupported = new ArrayList<>();
        for (File f : imageFiles) {
            String ext = extensionOf(f.getName());
            if (!ext.equals("jpg") && !ext.equals("jpeg") && !ext.equals("png"))
                unsupported.add(f.getName());
        }
        if (!unsupported.isEmpty()) {
            int proceed = JOptionPane.showConfirmDialog(this,
                    "<html>Os arquivos abaixo têm formato não suportado para escrita"
                            + " e serão <b>ignorados</b>:<br><br>"
                            + String.join("<br>", unsupported)
                            + "<br><br>Continuar com os demais?</html>",
                    "Formatos não suportados",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (proceed != JOptionPane.YES_OPTION) return;
        }

        writeBatch(dest -> {
            // Para cada índice, destino = próprio arquivo original
            String ext = extensionOf(imageFiles.get(dest).getName());
            String fmt = ext.equals("png") ? "png" : "jpeg";
            return new SaveTarget(imageFiles.get(dest), fmt);
        }, total, unsupported);
    }


// ═══════════════════════════════════════════════════════════════════
// 2. saveCurrentImageAs()  →  saveAllImagesAs()
//    Usuário escolhe pasta e formato; salva todos lá
// ═══════════════════════════════════════════════════════════════════

    private void saveAllImagesAs() {
        // ── Escolha de formato ─────────────────────────────────────────
        String[] fmtOptions = {"JPEG", "PNG"};
        int fmtChoice = JOptionPane.showOptionDialog(this,
                "Escolha o formato de saída para todas as imagens:",
                "Salvar como",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, fmtOptions, fmtOptions[0]);
        if (fmtChoice < 0) return;
        String fmt = fmtChoice == 0 ? "jpeg" : "png";
        String ext = fmtChoice == 0 ? "jpg"  : "png";

        // ── Escolha de pasta de destino ────────────────────────────────
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Escolha a pasta de destino");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setCurrentDirectory(imageFiles.get(0).getParentFile());
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File destDir = chooser.getSelectedFile();
        File originalDir = imageFiles.get(0).getParentFile();

        // ── Verifica colisões na pasta escolhida ───────────────────────
        List<String> willOverwrite = new ArrayList<>();
        for (File src : imageFiles) {
            File candidate = new File(destDir, baseName(src.getName()) + "." + ext);
            if (candidate.exists()) willOverwrite.add(candidate.getName());
        }

        if (!willOverwrite.isEmpty()) {
            String warning = destDir.equals(originalDir)
                    ? "A pasta escolhida é a mesma dos originais."
                    : "A pasta de destino já contém arquivos com o mesmo nome.";
            int confirm = JOptionPane.showConfirmDialog(this,
                    "<html>" + warning + " Os seguintes arquivos serão sobrescritos:<br><br>"
                            + String.join("<br>", willOverwrite)
                            + "<br><br>Deseja continuar?</html>",
                    "Confirmar sobrescrita",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
        }

        int total = imageFiles.size();
        writeBatch(idx -> {
            File src  = imageFiles.get(idx);
            File dest = new File(destDir, baseName(src.getName()) + "." + ext);
            return new SaveTarget(dest, fmt);
        }, total, List.of());
    }


// ═══════════════════════════════════════════════════════════════════
// 3. writeBatch()  —  processa e grava todas as imagens em background
// ═══════════════════════════════════════════════════════════════════

    /** Associa arquivo de destino e formato para cada índice do lote. */
    private record SaveTarget(File file, String format) {}

    /**
     * @param targetResolver  dado o índice, retorna destino + formato
     * @param total           número de imagens a processar
     * @param skip            nomes a pular (formatos não suportados no saveAllImages)
     */
    private void writeBatch(IntFunction<SaveTarget> targetResolver,
                            int total, List<String> skip) {

        // ── Diálogo de progresso ───────────────────────────────────────
        JDialog progress = new JDialog(this, "Salvando…", false);
        progress.setSize(360, 110);
        progress.setLocationRelativeTo(this);
        progress.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        progress.setResizable(false);

        JLabel statusLabel = new JLabel("Iniciando…", SwingConstants.CENTER);
        JProgressBar bar   = new JProgressBar(0, total);
        bar.setStringPainted(true);

        JPanel pp = new JPanel(new BorderLayout(8, 8));
        pp.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        pp.add(statusLabel, BorderLayout.NORTH);
        pp.add(bar,         BorderLayout.CENTER);
        progress.add(pp);
        progress.setVisible(true);

        // ── Worker ─────────────────────────────────────────────────────
        new SwingWorker<List<String>, Integer>() {    // publica índice concluído

            final List<String> errors = new ArrayList<>();

            @Override
            protected List<String> doInBackground() {
                for (int i = 0; i < total; i++) {
                    File src = imageFiles.get(i);
                    if (skip.contains(src.getName())) { publish(i); continue; }

                    SaveTarget t = targetResolver.apply(i);
                    try {
                        BufferedImage result = applyEnabledActions(images.get(i));

                        if (t.format().equals("jpeg")) {
                            BufferedImage rgb = new BufferedImage(
                                    result.getWidth(), result.getHeight(),
                                    BufferedImage.TYPE_INT_RGB);
                            Graphics2D g2 = rgb.createGraphics();
                            g2.drawImage(result, 0, 0, null);
                            g2.dispose();
                            result = rgb;
                        }

                        if (!ImageIO.write(result, t.format(), t.file()))
                            throw new IOException("Sem writer para: " + t.format());

                    } catch (Exception ex) {
                        errors.add(src.getName() + ": " + ex.getMessage());
                    }
                    publish(i);   // notifica progresso
                }
                return errors;
            }

            @Override
            protected void process(List<Integer> chunks) {
                int last = chunks.get(chunks.size() - 1);
                bar.setValue(last + 1);
                statusLabel.setText("Salvando " + (last + 1) + " de " + total + "…");
            }

            @Override
            protected void done() {
                progress.dispose();
                try {
                    List<String> errs = get();
                    if (errs.isEmpty()) {
                        JOptionPane.showMessageDialog(ImageEditorFrame.this,
                                total + " imagem(s) salva(s) com sucesso.",
                                "Concluído", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(ImageEditorFrame.this,
                                "<html>Concluído com " + errs.size() + " erro(s):<br><br>"
                                        + String.join("<br>", errs) + "</html>",
                                "Erros ao salvar", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ImageEditorFrame.this,
                            "Erro inesperado:\n" + ex.getMessage(),
                            "Erro", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void saveAllImages2() {
        saveManager.saveAll(
                images,
                imageFiles,
                this::applyEnabledActions,
                null   // onDone: nenhuma ação extra necessária após salvar
        );
    }
    private void saveAllImagesAs2() {
        saveManager.saveAllAs(
                images,
                imageFiles,
                imageFiles.get(0).getParentFile(),
                this::applyEnabledActions,
                null
        );
    }

}

