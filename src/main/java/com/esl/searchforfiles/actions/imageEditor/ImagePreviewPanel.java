package com.esl.searchforfiles.actions.imageEditor;

import com.esl.searchforfiles.actions.imageEditor.actions.ImageCrop.CropTool;

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
    private BufferedImage sourceImage;
    private double        zoomScale = 1.0;
    private int           imgW, imgH;

    // ── Modo crop ─────────────────────────────────────────────────
    private boolean cropMode     = false;
    private double  lockedAspect = 0;
    private QuadConsumer<Integer,Integer,Integer,Integer> cropConfirmCallback;
    private final CropTool cropTool = new CropTool();

    // ── Modo brush ────────────────────────────────────────────────
    private boolean                            brushMode            = false;
    private BrushStrokeCallback                brushCallback        = null;
    private Runnable                           brushReleaseCallback = null;
    private java.util.function.IntSupplier     brushSizeSupplier    = null;
    private java.util.function.Supplier<Color> brushColorSupplier   = null;
    private int lastMouseX = -1, lastMouseY = -1;

    // ── Cores do overlay crop ─────────────────────────────────────
    private static final Color OVERLAY_COLOR = new Color(0, 0, 0, 120);
    private static final Color BORDER_COLOR  = new Color(255, 255, 255, 220);
    private static final Color HANDLE_COLOR  = Color.WHITE;
    private static final Color HANDLE_BORDER = new Color(80, 80, 80);
    private static final int   HANDLE_SIZE   = 8;

    // ══════════════════════════════════════════════════════════════
    // Construtor
    // ══════════════════════════════════════════════════════════════
    public ImagePreviewPanel() {
        setBackground(new Color(30, 30, 30));
        setOpaque(true);

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { onPress(e);   }
            @Override public void mouseDragged(MouseEvent e)  { onDrag(e);    }
            @Override public void mouseReleased(MouseEvent e) { onRelease(e); }
            @Override public void mouseMoved(MouseEvent e)    { onMove(e);    }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    // ══════════════════════════════════════════════════════════════
    // API pública — imagem e zoom
    // ══════════════════════════════════════════════════════════════
    public void setImage(BufferedImage img) {
        this.sourceImage = img;
        updatePanelSize();
        repaint();
    }

    public void setZoom(double scale) {
        this.zoomScale = Math.max(0.01, scale);
        updatePanelSize();
        repaint();
    }

    public double        getZoom()        { return zoomScale; }
    public BufferedImage getSourceImage() { return sourceImage; }

    private void updatePanelSize() {
        if (sourceImage == null) return;
        imgW = Math.max(1, (int)(sourceImage.getWidth()  * zoomScale));
        imgH = Math.max(1, (int)(sourceImage.getHeight() * zoomScale));
        setPreferredSize(new Dimension(imgW, imgH));
        revalidate();
    }

    // ══════════════════════════════════════════════════════════════
    // API pública — modo crop
    // ══════════════════════════════════════════════════════════════
    public boolean isCropMode()   { return cropMode; }
    public boolean hasSelection() { return cropTool.hasSelection(); }

    public void enterCropMode(double aspect,
                              QuadConsumer<Integer,Integer,Integer,Integer> onConfirm) {
        if (brushMode) exitBrushMode();
        cropMode             = true;
        lockedAspect         = aspect;
        cropConfirmCallback  = onConfirm;
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

    // ══════════════════════════════════════════════════════════════
    // API pública — modo brush
    // ══════════════════════════════════════════════════════════════
    public boolean isBrushMode() { return brushMode; }

    /** Sobrecarga básica — sem cor de cursor nem callback de release. */
    public void enterBrushMode(BrushStrokeCallback strokeCallback,
                               java.util.function.IntSupplier sizeSupplier) {
        enterBrushMode(strokeCallback, sizeSupplier, null, null);
    }

    /** Sobrecarga com cor de cursor, sem callback de release. */
    public void enterBrushMode(BrushStrokeCallback strokeCallback,
                               java.util.function.IntSupplier sizeSupplier,
                               java.util.function.Supplier<Color> colorSupplier) {
        enterBrushMode(strokeCallback, sizeSupplier, colorSupplier, null);
    }

    /**
     * Ativa o modo brush completo.
     *
     * @param strokeCallback   chamado a cada ponto arrastado — deve ser rápido
     *                         (atualiza máscara + pinta direto no sourceImage)
     * @param sizeSupplier     tamanho atual do brush em px da imagem fonte
     * @param colorSupplier    cor exibida no cursor circular (null = branco)
     * @param releaseCallback  chamado ao soltar o mouse — aqui vai o refresh
     *                         pesado (requestPreviewRefresh) com todas as actions
     */
    public void enterBrushMode(BrushStrokeCallback strokeCallback,
                               java.util.function.IntSupplier sizeSupplier,
                               java.util.function.Supplier<Color> colorSupplier,
                               Runnable releaseCallback) {
        if (cropMode) exitCropMode();
        brushMode            = true;
        brushCallback        = strokeCallback;
        brushSizeSupplier    = sizeSupplier;
        brushColorSupplier   = colorSupplier;
        brushReleaseCallback = releaseCallback;
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        repaint();
    }

    public void exitBrushMode() {
        brushMode            = false;
        brushCallback        = null;
        brushReleaseCallback = null;
        brushSizeSupplier    = null;
        brushColorSupplier   = null;
        lastMouseX           = -1;
        lastMouseY           = -1;
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    // ══════════════════════════════════════════════════════════════
    // Pintura
    // ══════════════════════════════════════════════════════════════
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (sourceImage == null) return;

        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                zoomScale >= 1.0
                        ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                        : RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Centraliza quando menor que o painel
        int pw = getWidth(), ph = getHeight();
        int drawX = Math.max(0, (pw - imgW) / 2);
        int drawY = Math.max(0, (ph - imgH) / 2);

        g.drawImage(sourceImage, drawX, drawY, imgW, imgH, null);

        if (cropMode && cropTool.hasSelection())
            drawCropOverlay(g, drawX, drawY);

        if (brushMode && lastMouseX >= 0 && brushSizeSupplier != null)
            drawBrushCursor(g);
    }

    // ── Cursor do brush ───────────────────────────────────────────
    private void drawBrushCursor(Graphics2D g) {
        int r = (int)(brushSizeSupplier.getAsInt() * zoomScale / 2);
        Color ringColor = (brushColorSupplier != null)
                ? brushColorSupplier.get() : Color.WHITE;

        // Anel externo escuro para contraste em qualquer fundo
        g.setColor(new Color(0, 0, 0, 160));
        g.setStroke(new BasicStroke(2.5f));
        g.drawOval(lastMouseX - r - 1, lastMouseY - r - 1, (r+1)*2, (r+1)*2);

        // Anel colorido
        g.setColor(ringColor);
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(lastMouseX - r, lastMouseY - r, r * 2, r * 2);

        // Ponto central
        g.fillOval(lastMouseX - 2, lastMouseY - 2, 4, 4);
    }

    // ── Overlay do crop ───────────────────────────────────────────
    private void drawCropOverlay(Graphics2D g, int offX, int offY) {
        Rectangle sel = selectionInPanel(offX, offY);
        if (sel == null) return;

        // Escurece fora da seleção
        g.setColor(OVERLAY_COLOR);
        g.fillRect(offX, offY,               imgW, sel.y - offY);
        g.fillRect(offX, sel.y + sel.height, imgW, (offY + imgH) - (sel.y + sel.height));
        g.fillRect(offX, sel.y,              sel.x - offX, sel.height);
        g.fillRect(sel.x + sel.width, sel.y, (offX + imgW) - (sel.x + sel.width), sel.height);

        // Borda da seleção
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
        int r  = sel.x + sel.width,      b  = sel.y + sel.height;
        int[][] pts = {
                {sel.x, sel.y}, {cx, sel.y}, {r, sel.y},
                {sel.x, cy},                 {r, cy},
                {sel.x, b},     {cx, b},     {r, b}
        };
        for (int[] hp : pts) {
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
            int lw = fm.stringWidth(dim) + 8;
            int ly = sel.y - 20;
            if (ly < offY + 4) ly = sel.y + 4;
            g.setColor(new Color(0, 0, 0, 160));
            g.fillRoundRect(sel.x, ly - fm.getAscent(), lw, fm.getHeight()+2, 4, 4);
            g.setColor(Color.WHITE);
            g.drawString(dim, sel.x + 4, ly);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Eventos do mouse
    // ══════════════════════════════════════════════════════════════
    private void onPress(MouseEvent e) {
        if (brushMode) { applyBrush(e); return; }
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
        if (brushMode) { applyBrush(e); return; }
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
        if (brushMode) {
            // Refresh completo ao soltar — roda o pipeline inteiro
            if (brushReleaseCallback != null) brushReleaseCallback.run();
            return;
        }
        if (!cropMode) return;
        cropTool.endOperation();
        repaint();
    }

    private void onMove(MouseEvent e) {
        if (brushMode) {
            lastMouseX = e.getX(); lastMouseY = e.getY();
            repaint();
            return;
        }
        if (!cropMode || !cropTool.hasSelection()) {
            if (cropMode) setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            return;
        }
        Point img = panelToImage(e.getX(), e.getY());
        if (img == null) return;
        setCursor(cropTool.getCursorForHandle(
                cropTool.getHandleAt(img.x, img.y, 100)));
    }

    private void applyBrush(MouseEvent e) {
        lastMouseX = e.getX(); lastMouseY = e.getY();
        if (sourceImage == null || brushCallback == null) return;
        Point img = panelToImage(e.getX(), e.getY());
        if (img == null) return;
        brushCallback.onStroke(img.x, img.y,
                sourceImage.getWidth(), sourceImage.getHeight());
        repaint();
    }

    // ══════════════════════════════════════════════════════════════
    // Aspect ratio (crop)
    // ══════════════════════════════════════════════════════════════
    private void enforceAspect() {
        Rectangle sel = cropTool.getSelection();
        if (sel == null || sel.width == 0) return;
        int newH = (int) Math.round(sel.width / lockedAspect);
        newH = Math.max(20, Math.min(newH, sourceImage.getHeight() - sel.y));
        cropTool.setSelection(new Rectangle(sel.x, sel.y, sel.width, newH));
    }

    // ══════════════════════════════════════════════════════════════
    // Conversão de coordenadas
    // ══════════════════════════════════════════════════════════════
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

    private Rectangle selectionInPanel(int offX, int offY) {
        Rectangle sel = cropTool.getSelection();
        if (sel == null || sourceImage == null) return null;
        double sx = (double) imgW / sourceImage.getWidth();
        double sy = (double) imgH / sourceImage.getHeight();
        return new Rectangle(
                offX + (int)(sel.x      * sx), offY + (int)(sel.y       * sy),
                Math.max(1, (int)(sel.width * sx)), Math.max(1, (int)(sel.height * sy)));
    }

    // ══════════════════════════════════════════════════════════════
    // Scrollable
    // ══════════════════════════════════════════════════════════════
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle vr, int o, int d)  { return 16; }
    @Override public int getScrollableBlockIncrement(Rectangle vr, int o, int d) {
        return o == SwingConstants.VERTICAL ? vr.height : vr.width;
    }
    @Override public boolean getScrollableTracksViewportWidth() {
        return imgW <= (getParent() != null ? getParent().getWidth() : getWidth());
    }
    @Override public boolean getScrollableTracksViewportHeight() {
        return imgH <= (getParent() != null ? getParent().getHeight() : getHeight());
    }

    // ══════════════════════════════════════════════════════════════
    // Interfaces auxiliares
    // ══════════════════════════════════════════════════════════════
    @FunctionalInterface
    public interface QuadConsumer<A,B,C,D> { void accept(A a, B b, C c, D d); }

    @FunctionalInterface
    public interface BrushStrokeCallback { void onStroke(int cx, int cy, int refW, int refH); }
}