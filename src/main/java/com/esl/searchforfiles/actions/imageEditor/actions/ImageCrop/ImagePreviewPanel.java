package com.esl.searchforfiles.actions.imageEditor.actions.ImageCrop;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * Substitui o JLabel de preview do ImageEditorFrame.
 *
 * Responsabilidades:
 *  – Exibir a imagem escalada (igual ao scaledIcon() anterior).
 *  – Quando em modo crop, desenhar o overlay de seleção com handles e
 *    escurecer a área fora da seleção.
 *  – Converter coordenadas do mouse (painel) ↔ coordenadas da imagem.
 *  – Ao confirmar, chamar o callback com o rect em pixels da imagem.
 */
public class ImagePreviewPanel extends JPanel implements Scrollable {

    // ── Imagem e zoom ─────────────────────────────────────────────
    private BufferedImage sourceImage;   // imagem recebida (sem escala)
    private double        zoomScale = 1.0;

    // Dimensões da imagem desenhada no painel (sourceImage × zoomScale)
    private int imgW, imgH;

    // ── Modo crop ─────────────────────────────────────────────────
    private boolean cropMode    = false;
    private double  lockedAspect = 0;
    private ImagePreviewPanel.QuadConsumer<Integer,Integer,Integer,Integer> cropConfirmCallback;

    private final CropTool cropTool = new CropTool();

    private static final Color OVERLAY_COLOR = new Color(0, 0, 0, 120);
    private static final Color BORDER_COLOR  = new Color(255, 255, 255, 220);
    private static final Color HANDLE_COLOR  = Color.WHITE;
    private static final Color HANDLE_BORDER = new Color(80, 80, 80);
    private static final int   HANDLE_SIZE   = 8;

    // ── Construtor ────────────────────────────────────────────────
    public ImagePreviewPanel() {
        setBackground(new Color(30, 30, 30));
        setOpaque(true);

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { onPress(e);  }
            @Override public void mouseDragged(MouseEvent e)  { onDrag(e);   }
            @Override public void mouseReleased(MouseEvent e) { onRelease(e);}
            @Override public void mouseMoved(MouseEvent e)    { onMove(e);   }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    // ── API pública ───────────────────────────────────────────────

    public BufferedImage getCurrentImage() { return sourceImage; }

    /**
     * Define a imagem a exibir. Não escala — o zoom é aplicado na pintura.
     * Chame setZoom() depois para atualizar o tamanho do painel.
     */
    public void setImage(BufferedImage img) {
        this.sourceImage = img;
        updatePanelSize();
        repaint();
    }

    /**
     * Atualiza o fator de escala de exibição.
     * Deve ser chamado pelo frame sempre que fitScale ou zoomFactor mudar.
     */
    public void setZoom(double scale) {
        this.zoomScale = Math.max(0.01, scale);
        updatePanelSize();
        repaint();
    }

    public double         getZoom()         { return zoomScale; }
    public BufferedImage  getSourceImage()  { return sourceImage; }
    public boolean        isCropMode()      { return cropMode; }
    public boolean        hasSelection()    { return cropTool.hasSelection(); }

    // ── Modo crop ─────────────────────────────────────────────────

    public void enterCropMode(double aspect,
                              QuadConsumer<Integer,Integer,Integer,Integer> onConfirm) {
        cropMode = true;
        lockedAspect = aspect;
        cropConfirmCallback = onConfirm;
        cropTool.clearSelection();
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        repaint();
    }

    public void confirmCrop() {
        Rectangle sel = cropTool.getSelection();
        if (sel == null || sel.width <= 0 || sel.height <= 0) return;
        if (cropConfirmCallback != null)
            cropConfirmCallback.accept(sel.x, sel.y, sel.width, sel.height);
        exitCropMode();
    }

    public void exitCropMode() {
        cropMode = false;
        cropTool.clearSelection();
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    // ── Scrollable (permite scroll suave no JScrollPane) ──────────

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle vr, int o, int d)  { return 16; }
    @Override public int getScrollableBlockIncrement(Rectangle vr, int o, int d) {
        return o == SwingConstants.VERTICAL ? vr.height : vr.width;
    }
    @Override public boolean getScrollableTracksViewportWidth()  {
        // Deixa o painel encolher até o viewport quando a imagem é menor
        return imgW <= getParent().getWidth();
    }
    @Override public boolean getScrollableTracksViewportHeight() {
        return imgH <= getParent().getHeight();
    }

    // ── Pintura ───────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (sourceImage == null) return;

        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                zoomScale >= 1.0
                        ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                        : RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Centraliza quando a imagem é menor que o painel
        int pw = getWidth(), ph = getHeight();
        int drawX = Math.max(0, (pw - imgW) / 2);
        int drawY = Math.max(0, (ph - imgH) / 2);

        g.drawImage(sourceImage, drawX, drawY, imgW, imgH, null);

        if (cropMode && cropTool.hasSelection())
            drawCropOverlay(g, drawX, drawY);
    }

    /** Recalcula imgW/imgH e ajusta o preferredSize para o JScrollPane. */
    private void updatePanelSize() {
        if (sourceImage == null) return;
        imgW = Math.max(1, (int)(sourceImage.getWidth()  * zoomScale));
        imgH = Math.max(1, (int)(sourceImage.getHeight() * zoomScale));
        setPreferredSize(new Dimension(imgW, imgH));
        revalidate();
    }

    // ── Overlay de crop ───────────────────────────────────────────

    private void drawCropOverlay(Graphics2D g, int offX, int offY) {
        Rectangle sel = selectionInPanel(offX, offY);
        if (sel == null) return;

        // Escurece fora
        g.setColor(OVERLAY_COLOR);
        g.fillRect(offX, offY,               imgW, sel.y - offY);
        g.fillRect(offX, sel.y + sel.height, imgW, (offY + imgH) - (sel.y + sel.height));
        g.fillRect(offX, sel.y,              sel.x - offX, sel.height);
        g.fillRect(sel.x + sel.width, sel.y, (offX + imgW) - (sel.x + sel.width), sel.height);

        // Borda
        g.setColor(BORDER_COLOR);
        g.setStroke(new BasicStroke(1.2f));
        g.drawRect(sel.x, sel.y, sel.width, sel.height);

        // Grade de terços
        g.setColor(new Color(255, 255, 255, 60));
        g.setStroke(new BasicStroke(0.8f));
        int tx = sel.width / 3, ty = sel.height / 3;
        g.drawLine(sel.x + tx,     sel.y, sel.x + tx,     sel.y + sel.height);
        g.drawLine(sel.x + tx * 2, sel.y, sel.x + tx * 2, sel.y + sel.height);
        g.drawLine(sel.x, sel.y + ty,     sel.x + sel.width, sel.y + ty);
        g.drawLine(sel.x, sel.y + ty * 2, sel.x + sel.width, sel.y + ty * 2);

        // Handles
        int hh = HANDLE_SIZE / 2;
        int cx = sel.x + sel.width / 2, cy = sel.y + sel.height / 2;
        int r  = sel.x + sel.width,     b  = sel.y + sel.height;
//        int[][] pts = {
//                {sel.x,cx,r,sel.x,r,sel.x,cx,r}[0], // placeholder — usa loop abaixo
//        };
        // Pontos reais dos handles
        int[][] handlePts = {
                {sel.x, sel.y}, {cx, sel.y}, {r, sel.y},
                {sel.x, cy},                 {r, cy},
                {sel.x, b},     {cx, b},     {r, b}
        };
        for (int[] hp : handlePts) {
            g.setColor(HANDLE_BORDER);
            g.fillRect(hp[0]-hh-1, hp[1]-hh-1, HANDLE_SIZE+2, HANDLE_SIZE+2);
            g.setColor(HANDLE_COLOR);
            g.fillRect(hp[0]-hh,   hp[1]-hh,   HANDLE_SIZE,   HANDLE_SIZE);
        }

        // Label de dimensões
        Rectangle imgSel = cropTool.getSelection();
        if (imgSel != null) {
            String dim = imgSel.width + " × " + imgSel.height + " px";
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            FontMetrics fm = g.getFontMetrics();
            int lw  = fm.stringWidth(dim) + 8;
            int ly  = sel.y - 20;
            if (ly < offY + 4) ly = sel.y + 4;
            g.setColor(new Color(0,0,0,160));
            g.fillRoundRect(sel.x, ly - fm.getAscent(), lw, fm.getHeight()+2, 4, 4);
            g.setColor(Color.WHITE);
            g.drawString(dim, sel.x + 4, ly);
        }
    }

    // ── Eventos do mouse ──────────────────────────────────────────

    private void onPress(MouseEvent e) {
        if (!cropMode) return;
        Point img = panelToImage(e.getX(), e.getY());
        if (img == null) return;

        if (cropTool.hasSelection()) {
            CropTool.Handle h = cropTool.getHandleAt(img.x, img.y, 100);
            if (h != CropTool.Handle.NONE) { cropTool.startResize(img.x, img.y, h); return; }
        }
        cropTool.startSelection(img.x, img.y,
                sourceImage.getWidth(), sourceImage.getHeight());
    }

    private void onDrag(MouseEvent e) {
        if (!cropMode) return;
        Point img = panelToImage(e.getX(), e.getY());
        if (img == null) return;
        cropTool.updateSelection(img.x, img.y,
                sourceImage.getWidth(), sourceImage.getHeight());
        if (lockedAspect > 0 && cropTool.getActiveHandle() == CropTool.Handle.NONE)
            enforceAspect();
        repaint();
    }

    private void onRelease(MouseEvent e) {
        if (!cropMode) return;
        cropTool.endOperation();
        repaint();
    }

    private void onMove(MouseEvent e) {
        if (!cropMode || !cropTool.hasSelection()) {
            if (cropMode) setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            return;
        }
        Point img = panelToImage(e.getX(), e.getY());
        if (img == null) return;
        setCursor(cropTool.getCursorForHandle(
                cropTool.getHandleAt(img.x, img.y, 100)));
    }

    // ── Aspecto ───────────────────────────────────────────────────

    private void enforceAspect() {
        Rectangle sel = cropTool.getSelection();
        if (sel == null || sel.width == 0) return;
        int newH = (int) Math.round(sel.width / lockedAspect);
        newH = Math.max(20, Math.min(newH, sourceImage.getHeight() - sel.y));
        cropTool.setSelection(new Rectangle(sel.x, sel.y, sel.width, newH));
    }

    // ── Coordenadas ───────────────────────────────────────────────

    /** Painel → coords da sourceImage (leva em conta o offset de centralização). */
    private Point panelToImage(int px, int py) {
        if (sourceImage == null || imgW == 0 || imgH == 0) return null;
        int offX = Math.max(0, (getWidth()  - imgW) / 2);
        int offY = Math.max(0, (getHeight() - imgH) / 2);
        int ix = (int)((px - offX) * (double) sourceImage.getWidth()  / imgW);
        int iy = (int)((py - offY) * (double) sourceImage.getHeight() / imgH);
        ix = Math.max(0, Math.min(ix, sourceImage.getWidth()  - 1));
        iy = Math.max(0, Math.min(iy, sourceImage.getHeight() - 1));
        return new Point(ix, iy);
    }

    /** Seleção (coords da sourceImage) → coords do painel. */
    private Rectangle selectionInPanel(int offX, int offY) {
        Rectangle sel = cropTool.getSelection();
        if (sel == null || sourceImage == null) return null;
        double sx = (double) imgW / sourceImage.getWidth();
        double sy = (double) imgH / sourceImage.getHeight();
        return new Rectangle(
                offX + (int)(sel.x      * sx), offY + (int)(sel.y       * sy),
                Math.max(1, (int)(sel.width * sx)), Math.max(1, (int)(sel.height * sy)));
    }

    // ── Interface auxiliar ────────────────────────────────────────

    @FunctionalInterface
    public interface QuadConsumer<A,B,C,D> { void accept(A a, B b, C c, D d); }
}