package com.esl.searchforfiles.actions.imageEditor.actions.ImageRotate;


import com.esl.searchforfiles.actions.imageEditor.ActionCardPanel;
import com.esl.searchforfiles.actions.imageEditor.ImageEditorFrame;
import com.esl.searchforfiles.configuration.UIConfig;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class RotateActionCardPanel extends ActionCardPanel {

    private static final int CARD_HEIGHT = 100;

    private final ImageRotateAction rotateAction;
    private final ImageEditorFrame editorFrame;

    // Botões guardados para atualizar o estilo "ativo"
    private final Map<ImageRotateAction.Transform, JButton> buttons = new LinkedHashMap<>();

    public RotateActionCardPanel(ImageRotateAction action,
                                 ImageEditorFrame editorFrame,
                                 Consumer<ActionCardPanel> onRemove) {
        super(action, onRemove);
        this.rotateAction = action;
        this.editorFrame  = editorFrame;

        // Substitui o summaryLabel padrão pelo painel de botões
        remove(getComponent(1));
        add(buildButtonsPanel(), BorderLayout.CENTER);

        setMaximumSize(new Dimension(Integer.MAX_VALUE, CARD_HEIGHT));
        revalidate();
        repaint();
    }

    // ── Painel de botões ──────────────────────────────────────────

    private JPanel buildButtonsPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 4, 6, 0));
        panel.setBackground(new Color(50, 50, 50));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));

        addBtn(panel, "↺ 90°",  "Anti-horário",  ImageRotateAction.Transform.ROTATE_CCW);
        addBtn(panel, "↻ 90°",  "Horário",       ImageRotateAction.Transform.ROTATE_CW);
        addBtn(panel, "⇔",      "Flip H",        ImageRotateAction.Transform.FLIP_H);
        addBtn(panel, "⇕",      "Flip V",        ImageRotateAction.Transform.FLIP_V);

        return panel;
    }

    private void addBtn(JPanel panel, String icon, String tooltip,
                        ImageRotateAction.Transform t) {
        JButton btn = new JButton(icon);
        btn.setToolTipText(tooltip);
        btn.setForeground(Color.WHITE);
        btn.setBackground(new Color(65, 65, 65));
        btn.setBorder(BorderFactory.createLineBorder(new Color(90, 90, 90)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFont(UIConfig.FONT_DEFAULT);

        btn.addActionListener(e -> {
            // Toggle: clicar no botão já ativo desativa a operação
            ImageRotateAction.Transform next =
                    rotateAction.getTransform() == t
                            ? ImageRotateAction.Transform.NONE
                            : t;

            rotateAction.setTransform(next);
            refreshSummary();
            updateButtonStyles();
            editorFrame.requestPreviewRefresh();
        });

        buttons.put(t, btn);
        panel.add(btn);
    }

    /** Destaca visualmente o botão da operação atualmente selecionada. */
    private void updateButtonStyles() {
        ImageRotateAction.Transform active = rotateAction.getTransform();
        buttons.forEach((t, btn) -> {
            boolean sel = t == active;
            btn.setBackground(sel ? new Color(60, 100, 160) : new Color(65, 65, 65));
            btn.setBorder(BorderFactory.createLineBorder(
                    sel ? new Color(100, 150, 220) : new Color(90, 90, 90)));
        });
    }
}