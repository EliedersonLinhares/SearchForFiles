package com.esl.searchforfiles.actions.imageEditor.actions.ImageCrop;

import java.awt.*;
import java.awt.image.BufferedImage;

public class CropTool {

    // Área de seleção (coordenadas da imagem original)
    private Rectangle selection;

    // Handles para redimensionar
    public enum Handle {
        NONE,
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        MIDDLE_LEFT, MIDDLE_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,
        MOVE
    }

    private Handle activeHandle = Handle.NONE;
    private Point dragStart;
    private Rectangle originalSelection;

    private static final int HANDLE_SIZE = 8;
    private static final int MIN_SIZE = 20;

    public CropTool() {
        this.selection = null;
    }

    /**
     * Inicia uma nova seleção
     */
    public void startSelection(int x, int y, int imgWidth, int imgHeight) {
        selection = new Rectangle(x, y, 0, 0);
        constrainToImage(imgWidth, imgHeight);
    }

    /**
     * Atualiza a seleção durante o arrasto
     */
    public void updateSelection(int x, int y, int imgWidth, int imgHeight) {
        if (selection == null) return;

        if (activeHandle == Handle.NONE) {
            // Criando nova seleção
            int startX = selection.x;
            int startY = selection.y;

            int newX = Math.min(startX, x);
            int newY = Math.min(startY, y);
            int newW = Math.abs(x - startX);
            int newH = Math.abs(y - startY);

            selection = new Rectangle(newX, newY, newW, newH);
        } else if (activeHandle == Handle.MOVE) {
            // Movendo seleção
            int dx = x - dragStart.x;
            int dy = y - dragStart.y;

            selection.x = originalSelection.x + dx;
            selection.y = originalSelection.y + dy;
        } else {
            // Redimensionando pelos handles
            resizeByHandle(x, y);
        }

        constrainToImage(imgWidth, imgHeight);
    }

    /**
     * Redimensiona a seleção pelo handle ativo
     */
    private void resizeByHandle(int mouseX, int mouseY) {
        int dx = mouseX - dragStart.x;
        int dy = mouseY - dragStart.y;

        int newX = originalSelection.x;
        int newY = originalSelection.y;
        int newW = originalSelection.width;
        int newH = originalSelection.height;

        switch (activeHandle) {
            case TOP_LEFT:
                newX = originalSelection.x + dx;
                newY = originalSelection.y + dy;
                newW = originalSelection.width - dx;
                newH = originalSelection.height - dy;
                break;
            case TOP_CENTER:
                newY = originalSelection.y + dy;
                newH = originalSelection.height - dy;
                break;
            case TOP_RIGHT:
                newY = originalSelection.y + dy;
                newW = originalSelection.width + dx;
                newH = originalSelection.height - dy;
                break;
            case MIDDLE_LEFT:
                newX = originalSelection.x + dx;
                newW = originalSelection.width - dx;
                break;
            case MIDDLE_RIGHT:
                newW = originalSelection.width + dx;
                break;
            case BOTTOM_LEFT:
                newX = originalSelection.x + dx;
                newW = originalSelection.width - dx;
                newH = originalSelection.height + dy;
                break;
            case BOTTOM_CENTER:
                newH = originalSelection.height + dy;
                break;
            case BOTTOM_RIGHT:
                newW = originalSelection.width + dx;
                newH = originalSelection.height + dy;
                break;
        }

        // Garante tamanho mínimo
        if (newW >= MIN_SIZE && newH >= MIN_SIZE) {
            selection = new Rectangle(newX, newY, newW, newH);
        }
    }

    /**
     * Restringe a seleção aos limites da imagem
     */
    private void constrainToImage(int imgWidth, int imgHeight) {
        if (selection == null) return;

        // Garante que não saia dos limites
        if (selection.x < 0) selection.x = 0;
        if (selection.y < 0) selection.y = 0;
        if (selection.x + selection.width > imgWidth) {
            if (activeHandle == Handle.MOVE) {
                selection.x = imgWidth - selection.width;
            } else {
                selection.width = imgWidth - selection.x;
            }
        }
        if (selection.y + selection.height > imgHeight) {
            if (activeHandle == Handle.MOVE) {
                selection.y = imgHeight - selection.height;
            } else {
                selection.height = imgHeight - selection.y;
            }
        }
    }

    /**
     * Detecta qual handle está sob o ponto do mouse
     */
    public Handle getHandleAt(int x, int y, double zoom) {
        if (selection == null || selection.width == 0 || selection.height == 0) {
            return Handle.NONE;
        }

        int hs = (int)(HANDLE_SIZE / (zoom / 100.0));

        int left = selection.x;
        int right = selection.x + selection.width;
        int top = selection.y;
        int bottom = selection.y + selection.height;
        int centerX = selection.x + selection.width / 2;
        int centerY = selection.y + selection.height / 2;

        // Verifica cada handle
        if (isNear(x, y, left, top, hs)) return Handle.TOP_LEFT;
        if (isNear(x, y, centerX, top, hs)) return Handle.TOP_CENTER;
        if (isNear(x, y, right, top, hs)) return Handle.TOP_RIGHT;
        if (isNear(x, y, left, centerY, hs)) return Handle.MIDDLE_LEFT;
        if (isNear(x, y, right, centerY, hs)) return Handle.MIDDLE_RIGHT;
        if (isNear(x, y, left, bottom, hs)) return Handle.BOTTOM_LEFT;
        if (isNear(x, y, centerX, bottom, hs)) return Handle.BOTTOM_CENTER;
        if (isNear(x, y, right, bottom, hs)) return Handle.BOTTOM_RIGHT;

        // Verifica se está dentro da seleção (para mover)
        if (selection.contains(x, y)) return Handle.MOVE;

        return Handle.NONE;
    }

    private boolean isNear(int x, int y, int targetX, int targetY, int tolerance) {
        return Math.abs(x - targetX) <= tolerance && Math.abs(y - targetY) <= tolerance;
    }

    /**
     * Inicia operação de redimensionamento/movimento
     */
    public void startResize(int x, int y, Handle handle) {
        this.activeHandle = handle;
        this.dragStart = new Point(x, y);
        this.originalSelection = new Rectangle(selection);
    }

    /**
     * Finaliza operação
     */
    public void endOperation() {
        this.activeHandle = Handle.NONE;
        this.dragStart = null;
        this.originalSelection = null;
    }

    /**
     * Aplica o crop na imagem
     */
    public BufferedImage applyCrop(BufferedImage source) {
        if (selection == null || selection.width <= 0 || selection.height <= 0) {
            return source;
        }

        // Garante que a seleção está dentro dos limites
        int x = Math.max(0, selection.x);
        int y = Math.max(0, selection.y);
        int w = Math.min(selection.width, source.getWidth() - x);
        int h = Math.min(selection.height, source.getHeight() - y);

        if (w <= 0 || h <= 0) return source;

        BufferedImage cropped = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        Graphics g = cropped.getGraphics();
        g.drawImage(source.getSubimage(x, y, w, h), 0, 0, null);
        g.dispose();

        return cropped;
    }

    /**
     * Retorna o cursor apropriado para o handle
     */
    public Cursor getCursorForHandle(Handle handle) {
        switch (handle) {
            case TOP_LEFT:
            case BOTTOM_RIGHT:
                return Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
            case TOP_RIGHT:
            case BOTTOM_LEFT:
                return Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
            case TOP_CENTER:
            case BOTTOM_CENTER:
                return Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
            case MIDDLE_LEFT:
            case MIDDLE_RIGHT:
                return Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
            case MOVE:
                return Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
            default:
                return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
        }
    }

    // Getters e Setters
    public Rectangle getSelection() { return selection; }
    public void setSelection(Rectangle sel) { this.selection = sel; }
    public void clearSelection() { this.selection = null; }
    public boolean hasSelection() { return selection != null && selection.width > 0 && selection.height > 0; }
    public Handle getActiveHandle() { return activeHandle; }
    public int getHandleSize() { return HANDLE_SIZE; }
}