package com.esl.searchforfiles.actions.imageEditor.actions.ImageSketchFilter;

import com.esl.searchforfiles.actions.imageEditor.ActionCardPanel;
import com.esl.searchforfiles.actions.imageEditor.ImageEditorFrame;
import com.esl.searchforfiles.configuration.UIConfig;
import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

public class ImageSketchPanel extends ActionCardPanel {

    private static final int CARD_HEIGHT = 140;

    private final ImageSketchAction sketchAction;
    private final ImageEditorFrame  editorFrame;

    private JButton applyBtn;

    public ImageSketchPanel(ImageSketchAction action,
                            ImageEditorFrame editorFrame,
                            Consumer<ActionCardPanel> onRemove) {
        super(action, onRemove);
        this.sketchAction = action;
        this.editorFrame  = editorFrame;

        remove(getComponent(1));
        add(buildPanel(), BorderLayout.CENTER);

        setMaximumSize(new Dimension(Integer.MAX_VALUE, CARD_HEIGHT));
        revalidate();
        repaint();
    }

    // ── UI ────────────────────────────────────────────────────────

    private JPanel buildPanel() {
        JPanel root = new JPanel(new GridBagLayout());
     //   root.setBackground(new Color(50, 50, 50));
        root.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));

        GridBagConstraints g = new GridBagConstraints();
        g.insets  = new Insets(3, 3, 3, 3);
        g.anchor  = GridBagConstraints.WEST;
        g.fill    = GridBagConstraints.HORIZONTAL;

        // Linha 0 — Kernel
        g.gridy = 0;
        addSliderRow(root, g,
                "Kernel:",
                1, 9, sketchAction.getKernelSize(),
                // Slider só permite valores ímpares (1,3,5,7,9)
                raw -> {
                    int odd = (raw % 2 == 0) ? raw + 1 : raw;
                    sketchAction.setKernelSize(odd);
                    refreshSummary();
                    // NÃO chama requestPreviewRefresh — só aplica ao clicar
                },
                raw -> {
                    int odd = (raw % 2 == 0) ? raw + 1 : raw;
                    return odd + "×" + odd;
                });

        // Linha 1 — Iterações
        g.gridy = 1;
        addSliderRow(root, g,
                "Iterações:",
                1, 10, sketchAction.getDilateIterations(),
                raw -> {
                    sketchAction.setDilateIterations(raw);
                    refreshSummary();
                    // NÃO chama requestPreviewRefresh — só aplica ao clicar
                },
                raw -> String.valueOf(raw));

        // Linha 2 — Botão Aplicar
        g.gridy   = 2;
        g.gridx   = 0;
        g.weightx = 0;
        g.fill    = GridBagConstraints.NONE;
        root.add(makeLabel(""), g);   // espaçador

        applyBtn = new JButton("✏ Ativar sketch");
        applyBtn.setBackground(UIConfig.buttonBackgroundColor());
        applyBtn.setFont(UIConfig.FONT_DEFAULT);
        applyBtn.setToolTipText("Aplica o filtro de desenho com os parâmetros atuais");
        styleBtn(applyBtn, sketchAction.isEffectApplied());

        applyBtn.addActionListener(e -> {
            boolean next = !sketchAction.isEffectApplied();
            sketchAction.setEffectApplied(next);
            styleBtn(applyBtn, next);
            refreshSummary();
            editorFrame.requestPreviewRefresh();   // só aqui o preview é atualizado
        });

        g.gridx   = 1;
        g.weightx = 1;
        g.gridwidth = 2;
        g.fill    = GridBagConstraints.HORIZONTAL;
        root.add(applyBtn, g);

        return root;
    }

    // ── Linha de slider reutilizável ──────────────────────────────

    private void addSliderRow(JPanel panel, GridBagConstraints gbc,
                              String name, int min, int max, int initial,
                              IntConsumer onChange,
                              IntFunction<String> formatter) {

        JLabel nameLabel = makeLabel(name);
        nameLabel.setPreferredSize(new Dimension(68, 16));

        JSlider slider = new JSlider(min, max, initial);
        slider.setFocusable(false);

        JLabel valueLabel = new JLabel(formatter.apply(initial));
        valueLabel.setFont(UIConfig.FONT_DEFAULT);
        valueLabel.setPreferredSize(new Dimension(38, 16));
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        slider.addChangeListener(e -> {
            onChange.accept(slider.getValue());
            valueLabel.setText(formatter.apply(slider.getValue()));
        });

        gbc.gridx = 0; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(nameLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(slider, gbc);
        gbc.gridx = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(valueLabel, gbc);
    }

    // ── Estilo ────────────────────────────────────────────────────

    private static void styleBtn(JButton btn, boolean active) {
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFont(UIConfig.FONT_DEFAULT);
        btn.setForeground(Color.WHITE);
        if (active) {
            btn.setBackground(UIConfig.RED);
            btn.setText("✏ Desativar sketch");
        } else {
            btn.setBackground(UIConfig.BLUE);
            btn.setText("✏ Ativar sketch");
        }
    }

    private JButton makeTextBtn(JButton btn, String borderColor, String borderHoverColor, boolean active) {
        if (active) {
            btn.setBackground(UIConfig.SELECTED_COLOR);
        } else {
            btn.setBackground(UIConfig.buttonBackgroundColor());
        }

        Map<String, Object> estiloBotao = Map.of(
                "borderWidth", 2,
                "borderColor",UIManager.getColor(borderColor), // Cor normal
                "hoverBorderColor", UIManager.getColor(borderHoverColor), // Cor ao passar o mouse
                "focusedBorderColor", UIManager.getColor("Slider.trackColor") // Cor se focado (opcional)
        );
        // JButton btn = new JButton(text);
        btn.setFont(UIConfig.FONT_DEFAULT_BOLD);
        btn.putClientProperty(FlatClientProperties.STYLE, estiloBotao);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
    private static JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UIConfig.FONT_SMALL);
        return l;
    }
}
