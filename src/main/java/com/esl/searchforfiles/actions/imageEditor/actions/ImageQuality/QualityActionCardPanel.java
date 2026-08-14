package com.esl.searchforfiles.actions.imageEditor.actions.ImageQuality;


import com.esl.searchforfiles.actions.imageEditor.ActionCardPanel;
import com.esl.searchforfiles.actions.imageEditor.ImageEditorFrame;
import com.esl.searchforfiles.configuration.UIConfig;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class QualityActionCardPanel extends ActionCardPanel {

    private static final int CARD_HEIGHT = 90;

    private final ImageQualityAction qualityAction;
    private final ImageEditorFrame editorFrame;

    public QualityActionCardPanel(ImageQualityAction action,
                                  ImageEditorFrame editorFrame,
                                  Consumer<ActionCardPanel> onRemove) {
        super(action, onRemove);
        this.qualityAction = action;
        this.editorFrame   = editorFrame;

        remove(getComponent(1));
        add(buildPanel(), BorderLayout.CENTER);

        setMaximumSize(new Dimension(Integer.MAX_VALUE, CARD_HEIGHT));
        revalidate();
        repaint();
    }

    // ── UI ────────────────────────────────────────────────────────

    private JPanel buildPanel() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 3, 3, 3);
        g.anchor = GridBagConstraints.WEST;
        g.fill   = GridBagConstraints.HORIZONTAL;

        // ── Linha 0: slider de qualidade ──────────────────────────
        g.gridy = 0;

        JLabel nameLabel = makeLabel("Qualidade:");
       // nameLabel.setPreferredSize(new Dimension(70, 16));
        g.gridx = 0; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        root.add(nameLabel, g);

        JSlider slider = new JSlider(0, 100, qualityAction.getQuality());
        slider.setFocusable(false);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        root.add(slider, g);

        JLabel valueLabel = new JLabel(qualityAction.getQuality() + "%");
        valueLabel.setFont(UIConfig.FONT_DEFAULT);
        valueLabel.setPreferredSize(new Dimension(34, 16));
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        g.gridx = 2; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        root.add(valueLabel, g);

        // ── Linha 1: label de aviso ───────────────────────────────
        g.gridy = 1; g.gridx = 0; g.gridwidth = 3; g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        JLabel hint = makeLabel("100% = sem perda  |  valores baixos geram artefatos JPEG");
        hint.setForeground(new Color(110, 110, 110));
        root.add(hint, g);

        // ── Listener ──────────────────────────────────────────────
        slider.addChangeListener(e -> {
            int v = slider.getValue();
            qualityAction.setQuality(v);
            valueLabel.setText(v + "%");

            // Muda a cor do label conforme a qualidade
            valueLabel.setForeground(qualityColor(v));

            refreshSummary();
            // Só dispara o refresh ao soltar (compressão JPEG é mais pesada)
            if (!slider.getValueIsAdjusting())
                editorFrame.requestPreviewRefresh();
        });

        return root;
    }

    /**
     * Verde para alta qualidade, amarelo no meio, vermelho para baixa.
     */
    private static Color qualityColor(int q) {
        if (q >= 80) return new Color(100, 200, 100);
        if (q >= 50) return new Color(220, 180,  60);
        return new Color(220, 80, 80);
    }

    private static JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UIConfig.FONT_DEFAULT);
        return l;
    }
}