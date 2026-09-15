package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.configuration.UIConfig;

import javax.swing.*;
import java.awt.*;

public class ModeToolBarBuilder {

    private final JPanel panel;

    public ModeToolBarBuilder() {
        panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 5));
    }

    /** Título em negrito, no início da barra. */
    public ModeToolBarBuilder title(String text) {
        JLabel lbl = new JLabel("  " + text + "  ");
        lbl.setFont(UIConfig.FONT_TITLE.deriveFont(Font.BOLD));
        panel.add(lbl);
        return this;
    }

    /** Separador vertical, igual ao antigo bar.addSeparator(). */
    public ModeToolBarBuilder separator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 24));
        panel.add(sep);
        return this;
    }

    /** Botão padrão (ex: "Selecionar todos", "Limpar seleção"). */
    public ModeToolBarBuilder button(String text, Runnable action) {
        return button(text, UIConfig.FONT_DEFAULT, null, null, action);
    }

    /** Botão de ação principal, em negrito e cor de destaque. */
    public ModeToolBarBuilder highlightedButton(String text, Runnable action) {
        return button(text, UIConfig.FONT_DEFAULT_BOLD, UIConfig.SELECTED_BORDER, null, action);
    }

    /** Botão de saída, em vermelho. */
    public ModeToolBarBuilder exitButton(String text, Runnable action) {
        return button(text, UIConfig.FONT_DEFAULT, UIConfig.LIGHT_RED, null, action);
    }

    /** Versão genérica, com controle total sobre fonte/cor/tooltip. */
    public ModeToolBarBuilder button(String text, Font font, Color fg, String tooltip, Runnable action) {
        JButton btn = new JButton(text);
        btn.setFont(font);
        if (fg != null) btn.setForeground(fg);
        if (tooltip != null) btn.setToolTipText(tooltip);
        btn.setFocusPainted(false);
        btn.addActionListener(e -> action.run());
        panel.add(btn);
        return this;
    }

    /** Botão customizado com o estilo "highlighted medium" (borda de destaque via FlatLaf). */
    public ModeToolBarBuilder highlightedMediumButton(String text, String tooltip,
                                                      int width, int height, Runnable action) {
        JButton btn = UIConfig.makeHighlightedMediumBtn(text, tooltip, width, height);
        btn.addActionListener(e -> action.run());
        panel.add(btn);
        return this;
    }

    /** Ponto de extensão genérico: adiciona qualquer JButton já construído externamente. */
    public ModeToolBarBuilder customButton(JButton btn, Runnable action) {
        btn.addActionListener(e -> action.run());
        panel.add(btn);
        return this;
    }

    /** Variante para quando o botão já tem seu próprio listener configurado. */
    public ModeToolBarBuilder customButton(JButton btn) {
        panel.add(btn);
        return this;
    }

    public JPanel build() {
        return panel;
    }
}