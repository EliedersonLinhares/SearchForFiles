package com.esl.searchforfiles.ui;


import javax.swing.*;
import java.awt.*;

public class RatingOverlay extends JComponent {

    private static final Color STAR_COLOR    = new Color(255, 215, 0);   // Amarelo dourado
    private static final Color SHADOW_COLOR  = new Color(0, 0, 0, 120);  // Sombra semitransparente
    private static final Color BG_COLOR      = new Color(0, 0, 0, 90);   // Fundo do badge

    private int rating; // 0–5

    public RatingOverlay(int rating) {
        this.rating = rating;
        setOpaque(false); // transparente fora do desenho
    }

    public void setRating(int rating) {
        this.rating = rating;
        repaint();
    }

    public int getRating() {
        return rating;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (rating <= 0) return; // nada a desenhar

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        String stars = "★".repeat(rating);

        // Fonte proporcional à largura do componente
        float fontSize = Math.max(9f, getWidth() / 7.5f);
        Font font = new Font("SansSerif", Font.BOLD, (int) fontSize);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();

        int textW = fm.stringWidth(stars);
        int textH = fm.getAscent();
        int padX  = 4, padY = 2;

        // Posição: canto inferior esquerdo do componente
        int bgX = 2;
        int bgY = getHeight() - textH - padY * 2 - 2;
        int bgW = textW + padX * 2;
        int bgH = textH + padY * 2;

        // Fundo arredondado semitransparente
        g2.setColor(BG_COLOR);
        g2.fillRoundRect(bgX, bgY, bgW, bgH, 6, 6);

        // Sombra do texto (deslocada 1px)
        g2.setColor(SHADOW_COLOR);
        g2.drawString(stars, bgX + padX + 1, bgY + padY + textH + 1 - 1);

        // Texto amarelo
        g2.setColor(STAR_COLOR);
        g2.drawString(stars, bgX + padX, bgY + padY + textH - 1);

        g2.dispose();
    }
}
