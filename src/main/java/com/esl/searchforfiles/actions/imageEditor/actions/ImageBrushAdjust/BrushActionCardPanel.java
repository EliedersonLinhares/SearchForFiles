package com.esl.searchforfiles.actions.imageEditor.actions.ImageBrushAdjust;


import com.esl.searchforfiles.actions.imageEditor.ActionCardPanel;
import com.esl.searchforfiles.actions.imageEditor.ImageEditorFrame;
import com.esl.searchforfiles.configuration.UIConfig;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;

public class BrushActionCardPanel extends ActionCardPanel {

    private static final int CARD_HEIGHT = 220;

    private final ImageBrushAction brushAction;
    private final ImageEditorFrame editorFrame;

    // Botões de modo — guardados para atualizar o estilo ativo/inativo
    private final Map<ImageBrushAction.BrushTarget, JButton> targetBtns = new LinkedHashMap<>();

    public BrushActionCardPanel(ImageBrushAction action,
                                ImageEditorFrame editorFrame,
                                Consumer<ActionCardPanel> onRemove) {
        super(action, onRemove);
        this.brushAction = action;
        this.editorFrame = editorFrame;

        remove(getComponent(1));
        add(buildPanel(), BorderLayout.CENTER);

        setMaximumSize(new Dimension(Integer.MAX_VALUE, CARD_HEIGHT));
        revalidate();
        repaint();
    }

    // ── UI ────────────────────────────────────────────────────────

    private JPanel buildPanel() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(new Color(50, 50, 50));
        root.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));

        GridBagConstraints g = new GridBagConstraints();
        g.insets  = new Insets(3, 3, 3, 3);
        g.anchor  = GridBagConstraints.WEST;
        g.fill    = GridBagConstraints.HORIZONTAL;

        // ── Linha 0: botões de modo (Brilho | Contraste | Gamma | Saturação) ──
        g.gridy = 0; g.gridx = 0; g.gridwidth = 3; g.weightx = 1;
        root.add(buildTargetButtonsRow(), g);
        g.gridwidth = 1;

        // ── Linhas 1–4: sliders de valor ──────────────────────────
        g.gridy = 1;
        addSliderRow(root, g, "Brilho",
                -100, 100, (int)(brushAction.getBrightness() * 100),
                v -> brushAction.setBrightness(v / 100.0),
                v -> String.format("%+.2f", v / 100.0));

        g.gridy = 2;
        addSliderRow(root, g, "Contraste",
                0, 200, (int)(brushAction.getContrast() * 100),
                v -> brushAction.setContrast(v / 100.0),
                v -> String.format("%.2f", v / 100.0));

        g.gridy = 3;
        addSliderRow(root, g, "Gamma",
                10, 300, (int)(brushAction.getGamma() * 100),
                v -> brushAction.setGamma(v / 100.0),
                v -> String.format("%.2f", v / 100.0));

        g.gridy = 4;
        addSliderRow(root, g, "Saturação",
                0, 300, (int)(brushAction.getSaturation() * 100),
                v -> brushAction.setSaturation(v / 100.0),
                v -> String.format("%.2f", v / 100.0));

        // ── Linha 5: tamanho do brush ─────────────────────────────
        g.gridy = 5;
        addSliderRow(root, g, "Tamanho",
                4, 200, brushAction.getBrushSize(),
                v -> brushAction.setBrushSize(v),
                v -> v + " px");

        // ── Linha 6: botão Limpar máscara ─────────────────────────
        g.gridy = 6; g.gridx = 0; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        root.add(makeLabel(""), g);

        JButton clearMaskBtn = new JButton("🗑 Limpar pintura");
        clearMaskBtn.setBackground(new Color(80, 50, 50));
        clearMaskBtn.setForeground(Color.WHITE);
        clearMaskBtn.setFont(UIConfig.FONT_DEFAULT);
        clearMaskBtn.setBorder(BorderFactory.createLineBorder(new Color(140, 70, 70)));
        clearMaskBtn.setFocusPainted(false);
        clearMaskBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearMaskBtn.addActionListener(e -> {
            brushAction.clearMask();
            editorFrame.getPreviewPanel().exitBrushMode();
            updateTargetStyles();
            refreshSummary();
            editorFrame.requestPreviewRefresh();
        });

        g.gridx = 1; g.weightx = 1; g.gridwidth = 2; g.fill = GridBagConstraints.HORIZONTAL;
        root.add(clearMaskBtn, g);

        return root;
    }

    // ── Linha de botões de modo ───────────────────────────────────

    private JPanel buildTargetButtonsRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 4, 0));
        row.setBackground(new Color(50, 50, 50));

        record BtnDef(ImageBrushAction.BrushTarget target, String label) {}
        List<BtnDef> defs = List.of(
                new BtnDef(ImageBrushAction.BrushTarget.BRIGHTNESS, "☀ Brilho"),
                new BtnDef(ImageBrushAction.BrushTarget.CONTRAST,   "◑ Contraste"),
                new BtnDef(ImageBrushAction.BrushTarget.GAMMA,      "γ Gamma"),
                new BtnDef(ImageBrushAction.BrushTarget.SATURATION, "🎨 Saturação")
        );

        for (BtnDef def : defs) {
            JButton btn = new JButton(def.label());
            btn.setForeground(Color.WHITE);
            btn.setFont(UIConfig.FONT_DEFAULT);
            btn.setFocusPainted(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            applyTargetStyle(btn, false);

            btn.addActionListener(e -> {
                boolean wasActive = brushAction.getActiveTarget() == def.target()
                        && editorFrame.getPreviewPanel().isBrushMode();

                if (wasActive) {
                    // Toggle: desativa o brush
                    editorFrame.getPreviewPanel().exitBrushMode();
                } else {
                    brushAction.setActiveTarget(def.target());
                    // Ativa o modo brush no painel passando o callback de stroke
                    editorFrame.getPreviewPanel().enterBrushMode(
                            (cx, cy, refW, refH) -> {
                                brushAction.paintStroke(cx, cy, refW, refH);
                                editorFrame.requestPreviewRefresh();
                            },
                            () -> brushAction.getBrushSize()
                    );
                }
                updateTargetStyles();
                refreshSummary();
            });

            targetBtns.put(def.target(), btn);
            row.add(btn);
        }
        return row;
    }

    /** Atualiza o visual dos botões conforme o modo ativo. */
    private void updateTargetStyles() {
        boolean brushActive = editorFrame.getPreviewPanel().isBrushMode();
        ImageBrushAction.BrushTarget current = brushAction.getActiveTarget();
        targetBtns.forEach((target, btn) ->
                applyTargetStyle(btn, brushActive && target == current));
    }

    private static void applyTargetStyle(JButton btn, boolean active) {
        if (active) {
            btn.setBackground(new Color(60, 100, 160));
            btn.setBorder(BorderFactory.createLineBorder(new Color(100, 150, 220)));
        } else {
            btn.setBackground(new Color(65, 65, 65));
            btn.setBorder(BorderFactory.createLineBorder(new Color(90, 90, 90)));
        }
    }

    // ── Slider reutilizável ───────────────────────────────────────

    private void addSliderRow(JPanel panel, GridBagConstraints gbc,
                              String name, int min, int max, int initial,
                              Consumer<Integer> onChange, IntFunction<String> formatter) {
        JLabel nameLabel = makeLabel(name);
        nameLabel.setPreferredSize(new Dimension(62, 16));

        JSlider slider = new JSlider(min, max, initial);
        slider.setBackground(new Color(50, 50, 50));
        slider.setFocusable(false);

        JLabel valueLabel = new JLabel(formatter.apply(initial));
        valueLabel.setForeground(new Color(140, 140, 140));
        valueLabel.setFont(UIConfig.FONT_SMALL);
        valueLabel.setPreferredSize(new Dimension(38, 16));
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        slider.addChangeListener(e -> {
            onChange.accept(slider.getValue());
            valueLabel.setText(formatter.apply(slider.getValue()));
            refreshSummary();
            editorFrame.requestPreviewRefresh();
        });

        gbc.gridx = 0; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(nameLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(slider, gbc);
        gbc.gridx = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(valueLabel, gbc);
    }

    private static JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(new Color(180, 180, 180));
        l.setFont(UIConfig.FONT_SMALL);
        return l;
    }
}