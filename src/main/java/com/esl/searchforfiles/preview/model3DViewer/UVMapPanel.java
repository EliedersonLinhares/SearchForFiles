package com.esl.searchforfiles.preview.model3DViewer;

import com.esl.searchforfiles.configuration.UIConfig;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

/**
 * Painel que desenha o layout UV de um material: um quadrado representando
 * o espaço de textura (0.0 a 1.0), com a textura do material como fundo
 * (se houver) e as arestas dos triângulos da malha sobrepostas em azul.
 */
public class UVMapPanel extends JPanel {

    private List<float[]> edges = Collections.emptyList();
    private BufferedImage backgroundTexture;

    public UVMapPanel() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(512, 512));
    }

    public void setEdges(List<float[]> edges) {
        this.edges = (edges != null) ? edges : Collections.emptyList();
        repaint();
    }

    public void setBackgroundTexture(BufferedImage texture) {
        this.backgroundTexture = texture;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int size = Math.min(getWidth(), getHeight()) - 24;
        if (size <= 0) return;
        int offsetX = (getWidth() - size) / 2;
        int offsetY = (getHeight() - size) / 2;

        // Fundo: textura do material, se houver, senão um quadriculado neutro
        if (backgroundTexture != null) {
            g2.drawImage(backgroundTexture, offsetX, offsetY, size, size, null);
        } else {
            //g2.setColor(new Color(0xEE, 0xEE, 0xEE));
            g2.fillRect(offsetX, offsetY, size, size);
        }

        // Grade de referência a cada 0.1 em U e V
        g2.setColor(new Color(0, 0, 0, 60));
        for (int i = 1; i < 10; i++) {
            int x = offsetX + i * size / 10;
            g2.drawLine(x, offsetY, x, offsetY + size);
            int y = offsetY + i * size / 10;
            g2.drawLine(offsetX, y, offsetX + size, y);
        }

        // Borda do quadrado UV (0,0)-(1,1)
        g2.setColor(Color.DARK_GRAY);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRect(offsetX, offsetY, size, size);

        // Arestas dos triângulos em espaço UV — V é invertido, já que no
        // padrão OBJ/OpenGL v=0 fica embaixo, mas telas desenham y=0 em cima
        g2.setColor(new Color(0x1E, 0x88, 0xE5));
        g2.setStroke(new BasicStroke(1f));
        for (float[] edge : edges) {
            int x0 = offsetX + Math.round(edge[0] * size);
            int y0 = offsetY + Math.round((1f - edge[1]) * size);
            int x1 = offsetX + Math.round(edge[2] * size);
            int y1 = offsetY + Math.round((1f - edge[3]) * size);
            g2.drawLine(x0, y0, x1, y1);
        }

        if (edges.isEmpty()) {
            g2.setColor(Color.GRAY);
            g2.setFont(UIConfig.FONT_DEFAULT);
            String msg = "Este material não possui coordenadas UV";
            FontMetrics fm = g2.getFontMetrics();
            int textX = offsetX + (size - fm.stringWidth(msg)) / 2;
            int textY = offsetY + size / 2;
            g2.drawString(msg, textX, textY);
        }
    }
}