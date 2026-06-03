package com.esl.searchforfiles.actions.imageEditor.actions.ImagePaintBrush;


import com.esl.searchforfiles.actions.imageEditor.ActionCardPanel;
import com.esl.searchforfiles.actions.imageEditor.ImageEditorFrame;
import com.esl.searchforfiles.configuration.UIConfig;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

public class PaintBrushActionCardPanel extends ActionCardPanel {

    private static final int CARD_HEIGHT = 230;

    private final ImagePaintBrushAction brushAction;
    private final ImageEditorFrame editorFrame;

    // Widgets que precisam de referência fora do construtor
    private JPanel colorPreview;
    private JTextField fieldR;
    private JTextField fieldG;
    private JTextField fieldB;
    private JToggleButton solidBtn;
    private JToggleButton softBtn;
    private JButton   paintBtn;
    private boolean   painting = false;

    public PaintBrushActionCardPanel(ImagePaintBrushAction action,
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
        g.insets = new Insets(3, 3, 3, 3);
        g.anchor = GridBagConstraints.WEST;
        g.fill   = GridBagConstraints.HORIZONTAL;

        // ── Linha 0: preview de cor + botão JColorChooser ─────────
        g.gridy = 0; g.gridx = 0; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        root.add(makeLabel("Cor:"), g);

        colorPreview = new JPanel();
        colorPreview.setPreferredSize(new Dimension(28, 20));
        colorPreview.setBackground(brushAction.getBrushColor());
        colorPreview.setBorder(BorderFactory.createLineBorder(new Color(100, 100, 100)));
        g.gridx = 1; g.weightx = 0;
        root.add(colorPreview, g);

        JButton pickerBtn = new JButton("🎨 Escolher cor");
        styleSmallBtn(pickerBtn);
        pickerBtn.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(
                    editorFrame, "Escolher cor do brush", brushAction.getBrushColor());
            if (chosen != null) applyColor(chosen);
        });
        g.gridx = 2; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        root.add(pickerBtn, g);

        // ── Linha 1: campos RGB ───────────────────────────────────
        g.gridy = 1; g.gridx = 0; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        root.add(makeLabel("RGB:"), g);

        JPanel rgbRow = buildRgbRow();
        g.gridx = 1; g.weightx = 1; g.gridwidth = 2; g.fill = GridBagConstraints.HORIZONTAL;
        root.add(rgbRow, g);
        g.gridwidth = 1;

        // ── Linha 2: tipo de brush (Sólido | Suave) ───────────────
        g.gridy = 2; g.gridx = 0; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        root.add(makeLabel("Tipo:"), g);

        JPanel typeRow = buildTypeRow();
        g.gridx = 1; g.weightx = 1; g.gridwidth = 2; g.fill = GridBagConstraints.HORIZONTAL;
        root.add(typeRow, g);
        g.gridwidth = 1;

        // ── Linha 3: opacidade ────────────────────────────────────
        g.gridy = 3;
        addSliderRow(root, g, "Opacidade",
                1, 100, Math.round(brushAction.getOpacity() * 100),
                v -> brushAction.setOpacity(v / 100f),
                v -> v + "%");

        // ── Linha 4: tamanho ──────────────────────────────────────
        g.gridy = 4;
        addSliderRow(root, g, "Tamanho",
                2, 200, brushAction.getBrushSize(),
                brushAction::setBrushSize,
                v -> v + " px");

        // ── Linha 5: botões Pintar / Limpar ───────────────────────
        g.gridy = 5; g.gridx = 0; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        root.add(makeLabel(""), g);

        paintBtn = new JButton("✏ Pintar");
        styleSmallBtn(paintBtn);
        paintBtn.setBackground(new Color(50, 90, 150));
        paintBtn.setBorder(BorderFactory.createLineBorder(new Color(80, 130, 210)));
        paintBtn.addActionListener(e -> togglePaintMode());
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        root.add(paintBtn, g);

        JButton clearBtn = new JButton("🗑 Limpar");
        styleSmallBtn(clearBtn);
        clearBtn.setBackground(new Color(80, 50, 50));
        clearBtn.setBorder(BorderFactory.createLineBorder(new Color(140, 70, 70)));
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

    // ── Linha RGB ─────────────────────────────────────────────────

    private JPanel buildRgbRow() {
        JPanel row = new JPanel(new GridLayout(1, 6, 4, 0));
        row.setBackground(new Color(50, 50, 50));

        Color c = brushAction.getBrushColor();
        fieldR = makeRgbField(String.valueOf(c.getRed()));
        fieldG = makeRgbField(String.valueOf(c.getGreen()));
        fieldB = makeRgbField(String.valueOf(c.getBlue()));

        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { onRgbTyped(); }
            public void removeUpdate(DocumentEvent e)  { onRgbTyped(); }
            public void changedUpdate(DocumentEvent e) { onRgbTyped(); }
        };
        fieldR.getDocument().addDocumentListener(dl);
        fieldG.getDocument().addDocumentListener(dl);
        fieldB.getDocument().addDocumentListener(dl);

        row.add(makeLabel("R")); row.add(fieldR);
        row.add(makeLabel("G")); row.add(fieldG);
        row.add(makeLabel("B")); row.add(fieldB);
        return row;
    }

    private JTextField makeRgbField(String value) {
        JTextField f = new JTextField(value, 3);
        f.setBackground(new Color(38, 38, 38));
        f.setForeground(Color.WHITE);
        f.setCaretColor(Color.WHITE);
        f.setFont(f.getFont().deriveFont(10f));
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(90, 90, 90)),
                BorderFactory.createEmptyBorder(1, 3, 1, 3)));
        // Só aceita dígitos
        ((AbstractDocument) f.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int o, String s, AttributeSet a)
                    throws BadLocationException {
                if (s != null && s.matches("\\d*")) super.insertString(fb, o, s, a);
            }

            @Override
            public void replace(FilterBypass fb, int o, int l, String s, AttributeSet a)
                    throws BadLocationException {
                if (s != null && s.matches("\\d*")) super.replace(fb, o, l, s, a);
            }
        });
        return f;
    }

    private void onRgbTyped() {
        try {
            int r = Math.min(255, Integer.parseInt(fieldR.getText().trim()));
            int g = Math.min(255, Integer.parseInt(fieldG.getText().trim()));
            int b = Math.min(255, Integer.parseInt(fieldB.getText().trim()));
            applyColor(new Color(r, g, b), false); // false = não atualiza os campos
        } catch (NumberFormatException _) {/*Number exception */}
    }

    // ── Tipo de brush ─────────────────────────────────────────────

    private JPanel buildTypeRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, 4, 0));
        row.setBackground(new Color(50, 50, 50));

        solidBtn = new JToggleButton("● Sólido");
        softBtn  = new JToggleButton("◉ Suave");

        styleTypeBtn(solidBtn);
        styleTypeBtn(softBtn);

        boolean isSoft = brushAction.getBrushType() == ImagePaintBrushAction.BrushType.SOFT;
        solidBtn.setSelected(!isSoft);
        softBtn .setSelected( isSoft);
        updateTypeStyles();

        solidBtn.addActionListener(e -> {
            brushAction.setBrushType(ImagePaintBrushAction.BrushType.SOLID);
            solidBtn.setSelected(true);
            softBtn .setSelected(false);
            updateTypeStyles();
            refreshSummary();
        });
        softBtn.addActionListener(e -> {
            brushAction.setBrushType(ImagePaintBrushAction.BrushType.SOFT);
            softBtn .setSelected(true);
            solidBtn.setSelected(false);
            updateTypeStyles();
            refreshSummary();
        });

        row.add(solidBtn);
        row.add(softBtn);
        return row;
    }

    private void updateTypeStyles() {
        applyToggleStyle(solidBtn, solidBtn.isSelected());
        applyToggleStyle(softBtn,  softBtn .isSelected());
    }

    private static void applyToggleStyle(JToggleButton btn, boolean active) {
        btn.setBackground(active ? new Color(60, 100, 160) : new Color(65, 65, 65));
        btn.setBorder(BorderFactory.createLineBorder(
                active ? new Color(100, 150, 220) : new Color(90, 90, 90)));
    }

    // ── Modo pintura ──────────────────────────────────────────────

    private void togglePaintMode() {
        if (painting) {
            editorFrame.getPreviewPanel().exitBrushMode();
            setPainting(false);
        } else {
            editorFrame.getPreviewPanel().enterBrushMode(
                    (cx, cy, refW, refH) -> {
                        brushAction.paintStroke(cx, cy, refW, refH);
                        editorFrame.requestPreviewRefresh();
                    },
                    brushAction::getBrushSize,
                    brushAction::getBrushColor   // ← cor do cursor em tempo real
            );
            setPainting(true);
        }
    }

    private void setPainting(boolean active) {
        painting = active;
        paintBtn.setText(active ? "⏹ Parar" : "✏ Pintar");
        paintBtn.setBackground(active ? new Color(140, 60, 60) : new Color(50, 90, 150));
        paintBtn.setBorder(BorderFactory.createLineBorder(
                active ? new Color(200, 90, 90) : new Color(80, 130, 210)));
    }

    // ── Aplicar cor (de qualquer fonte) ──────────────────────────

    private void applyColor(Color c) { applyColor(c, true); }

    private void applyColor(Color c, boolean updateFields) {
        brushAction.setBrushColor(c);
        colorPreview.setBackground(c);
        colorPreview.repaint();
        if (updateFields) {
            fieldR.setText(String.valueOf(c.getRed()));
            fieldG.setText(String.valueOf(c.getGreen()));
            fieldB.setText(String.valueOf(c.getBlue()));
        }
        refreshSummary();
    }

    // ── Slider reutilizável ───────────────────────────────────────

    private void addSliderRow(JPanel panel, GridBagConstraints gbc,
                              String name, int min, int max, int initial,
                              IntConsumer onChange, IntFunction<String> formatter) {
        JLabel nameLabel = makeLabel(name);
        nameLabel.setPreferredSize(new Dimension(62, 16));

        JSlider slider = new JSlider(min, max, initial);
        slider.setBackground(new Color(50, 50, 50));
        slider.setFocusable(false);

        JLabel valueLabel = new JLabel(formatter.apply(initial));
        valueLabel.setForeground(new Color(140, 140, 140));
        valueLabel.setFont(UIConfig.FONT_DEFAULT);
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

    private static void styleSmallBtn(JButton btn) {
        btn.setForeground(Color.WHITE);
        btn.setBackground(new Color(65, 65, 65));
        btn.setFont(UIConfig.FONT_SMALL);
        btn.setBorder(BorderFactory.createLineBorder(new Color(90, 90, 90)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static void styleTypeBtn(JToggleButton btn) {
        btn.setForeground(Color.WHITE);
        btn.setFont(UIConfig.FONT_DEFAULT);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(new Color(180, 180, 180));
        l.setFont(UIConfig.FONT_DEFAULT);
        return l;
    }
}