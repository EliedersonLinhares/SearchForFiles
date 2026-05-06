package com.esl.searchforfiles.ui;


import javax.swing.*;
import java.awt.*;

public class SelectionCheckbox extends JCheckBox {

    public SelectionCheckbox() {
        setOpaque(false);
        setFocusable(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

        // Visual: fundo semitransparente para contraste
        setUI(new javax.swing.plaf.basic.BasicCheckBoxUI() {
            @Override
            public synchronized void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                // Fundo branco semitransparente
                g2.setColor(new Color(255, 255, 255, 180));
                g2.fillRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 6, 6);
                g2.dispose();
                super.paint(g, c);
            }
        });
    }
}