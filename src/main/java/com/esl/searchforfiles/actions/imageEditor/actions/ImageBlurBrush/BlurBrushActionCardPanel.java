package com.esl.searchforfiles.actions.imageEditor.actions.ImageBlurBrush;


import com.esl.searchforfiles.actions.imageEditor.ActionCardPanel;
import com.esl.searchforfiles.actions.imageEditor.ImageEditorFrame;
import com.esl.searchforfiles.configuration.UIConfig;
import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

public class BlurBrushActionCardPanel extends ActionCardPanel {

    private static final int CARD_HEIGHT = 170;

    private final ImageBlurBrushAction brushAction;
    private final ImageEditorFrame editorFrame;

    private JButton paintBtn;
    private JToggleButton solidBtn, softBtn;
    private boolean  painting = false;

    public BlurBrushActionCardPanel(ImageBlurBrushAction action,
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
        root.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 3, 3, 3);
        g.anchor = GridBagConstraints.WEST;
        g.fill   = GridBagConstraints.HORIZONTAL;

        // ── Linha 0: tipo de brush ────────────────────────────────
        g.gridy = 0; g.gridx = 0; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        root.add(makeLabel("Tipo:"), g);

        g.gridx = 1; g.weightx = 1; g.gridwidth = 2; g.fill = GridBagConstraints.HORIZONTAL;
        root.add(buildTypeRow(), g);
        g.gridwidth = 1;

        // ── Linha 1: raio do blur ─────────────────────────────────
        g.gridy = 1;
        addSliderRow(root, g, "Blur:",
                1, 20, brushAction.getBlurRadius(),
                brushAction::setBlurRadius,
                v -> v + " px");

        // ── Linha 2: tamanho do brush ─────────────────────────────
        g.gridy = 2;
        addSliderRow(root, g, "Tamanho:",
                2, 200, brushAction.getBrushSize(),
                brushAction::setBrushSize,
                v -> v + " px");

        // ── Linha 3: botões Pintar / Limpar ───────────────────────
        g.gridy = 3; g.gridx = 0; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        root.add(makeLabel(""), g);

       paintBtn = new JButton("✏ Pintar");
       paintBtn.setBackground(new Color(80, 130, 210));
       paintBtn.setForeground(Color.white);
      //  styleBtn(paintBtn, new Color(50, 90, 150), new Color(80, 130, 210));
        makeTextBtn(paintBtn,
                "Slider.trackColor",
                "Slider.trackColor");


        paintBtn.addActionListener(e -> togglePaintMode());
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        root.add(paintBtn, g);

        JButton clearBtn = new JButton("🗑 Limpar");
        clearBtn.setForeground(Color.white);
        clearBtn.setBackground(UIConfig.RED);
       // styleBtn(clearBtn, new Color(80, 50, 50), new Color(140, 70, 70));
        makeTextBtn(clearBtn,
                "Slider.trackColor",
                "Slider.trackColor");

        clearBtn.addActionListener(e -> {
            brushAction.clearMask();
            editorFrame.getPreviewPanel().exitBrushMode();
            setPainting(false);
            refreshSummary();
            editorFrame.requestPreviewRefresh();
        });
        g.gridx = 2; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        root.add(clearBtn, g);

        return root;
    }

    // ── Tipo de brush ─────────────────────────────────────────────

    private JPanel buildTypeRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, 4, 0));
        solidBtn = new JToggleButton("● Sólido");
        softBtn  = new JToggleButton("◉ Suave");
        styleToggle(solidBtn); styleToggle(softBtn);

        boolean isSoft = brushAction.getBrushType() == ImageBlurBrushAction.BrushType.SOFT;
        solidBtn.setSelected(!isSoft);
        softBtn .setSelected( isSoft);
        updateTypeStyles();

        solidBtn.addActionListener(e -> {
            brushAction.setBrushType(ImageBlurBrushAction.BrushType.SOLID);
            solidBtn.setSelected(true); softBtn.setSelected(false);
            updateTypeStyles(); refreshSummary();
        });
        softBtn.addActionListener(e -> {
            brushAction.setBrushType(ImageBlurBrushAction.BrushType.SOFT);
            softBtn.setSelected(true); solidBtn.setSelected(false);
            updateTypeStyles(); refreshSummary();
        });

        row.add(solidBtn); row.add(softBtn);
        return row;
    }

    private void updateTypeStyles() {
        applyToggleStyle(solidBtn, solidBtn.isSelected());
        applyToggleStyle(softBtn,  softBtn .isSelected());
        solidBtn.setText( solidBtn.isSelected() ? "> ● Sólido" : "● Sólido");
        softBtn.setText( softBtn.isSelected() ? "> ◉ Suave" : "◉ Suave");
    }

    private static void applyToggleStyle(JToggleButton btn, boolean active) {

        btn.setBackground(active ? UIConfig.BLUE : new Color(170, 60, 60));
    }

    // ── Modo pintura ──────────────────────────────────────────────

    private void togglePaintMode() {
        if (painting) {
            editorFrame.getPreviewPanel().exitBrushMode();
            setPainting(false);
        } else {
            editorFrame.getPreviewPanel().enterBrushMode(
                    // onStroke: aplica o blur só na área pintada, em tempo real
                    (cx, cy, refW, refH) -> {
                        brushAction.paintStroke(cx, cy, refW, refH);
                        BufferedImage proxy = editorFrame.getPreviewProxy();
                        if (proxy != null)
                            editorFrame.getPreviewPanel().setImage(brushAction.apply(proxy));
                    },
                    brushAction::getBrushSize,
                    null,   // cursor branco (blur não tem cor)
                    editorFrame::requestPreviewRefresh   // refresh completo ao soltar
            );
            setPainting(true);
        }
    }

    private void setPainting(boolean active) {
        painting = active;
        paintBtn.setText(active ? "⏹ Parar" : "✏ Pintar");
        paintBtn.setBackground(active ? UIConfig.RED: UIConfig.BLUE);
    }

    // ── Slider reutilizável ───────────────────────────────────────

    private void addSliderRow(JPanel panel, GridBagConstraints gbc,
                              String name, int min, int max, int initial,
                              IntConsumer onChange, IntFunction<String> formatter) {
        JLabel nameLabel = makeLabel(name);
        nameLabel.setPreferredSize(new Dimension(66, 16));

        JSlider slider = new JSlider(min, max, initial);
        slider.setFocusable(false);

        JLabel valueLabel = new JLabel(formatter.apply(initial));
        valueLabel.setFont(UIConfig.FONT_SMALL);
        valueLabel.setPreferredSize(new Dimension(40, 16));
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        slider.addChangeListener(e -> {
            onChange.accept(slider.getValue());
            valueLabel.setText(formatter.apply(slider.getValue()));
            refreshSummary();
        });

        gbc.gridx = 0; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(nameLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(slider, gbc);
        gbc.gridx = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(valueLabel, gbc);
    }

    // ── Estilo ────────────────────────────────────────────────────

    private JButton makeTextBtn(JButton btn, String borderColor, String borderHoverColor) {
        Map<String, Object> estiloBotao = Map.of(
                "borderWidth", 2,
                "borderColor",UIManager.getColor(borderColor), // Cor normal
                "hoverBorderColor", UIManager.getColor(borderHoverColor), // Cor ao passar o mouse
                "focusedBorderColor", UIManager.getColor("Slider.trackColor") // Cor se focado (opcional)
        );

        btn.setFont(UIConfig.FONT_DEFAULT_BOLD);
        btn.putClientProperty(FlatClientProperties.STYLE, estiloBotao);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private static void styleToggle(JToggleButton btn) {
        btn.setForeground(Color.WHITE);
        btn.setFont(UIConfig.FONT_DEFAULT);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setPreferredSize(new Dimension(66, 16));
        l.setFont(UIConfig.FONT_DEFAULT);
        return l;
    }
}