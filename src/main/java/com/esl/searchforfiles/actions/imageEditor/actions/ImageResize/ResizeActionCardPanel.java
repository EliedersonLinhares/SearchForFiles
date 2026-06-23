package com.esl.searchforfiles.actions.imageEditor.actions.ImageResize;


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
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.function.Consumer;

public class ResizeActionCardPanel extends ActionCardPanel {

    private static final int CARD_HEIGHT_MANUAL     = 136;
    private static final int CARD_HEIGHT_PERCENTAGE = 100;

    private final ImageResizeAction resizeAction;
    private final ImageEditorFrame  editorFrame;

    // ── Widgets ───────────────────────────────────────────────────
    private final JComboBox<String> modeCombo = new JComboBox<>(
            new String[]{"Manual", "Porcentagem"});

    // Painel manual
    private final JTextField widthField   = new JTextField(6);
    private final JTextField heightField  = new JTextField(6);
    private final JToggleButton lockBtn   = new JToggleButton();
    private double aspectRatio            = 0.0;
    private boolean adjusting             = false;

    // Painel porcentagem
    private final JTextField pctField     = new JTextField("100", 6);

    // Painel cardável (CardLayout troca entre os dois modos)
    private final JPanel     modeCards    = new JPanel(new CardLayout());

    private static final String CARD_MANUAL = "manual";
    private static final String CARD_PCT    = "pct";

    // ── Construtor ────────────────────────────────────────────────
    public ResizeActionCardPanel(ImageResizeAction action,
                                 ImageEditorFrame editorFrame,
                                 Consumer<ActionCardPanel> onRemove) {
        super(action, onRemove);
        this.resizeAction = action;
        this.editorFrame  = editorFrame;

        remove(getComponent(1));
        add(buildMainPanel(), BorderLayout.CENTER);

        applyCardHeight();
        revalidate();
        repaint();
    }

    // ── Painel principal ──────────────────────────────────────────

    private JPanel buildMainPanel() {
        JPanel root = new JPanel(new BorderLayout(0, 2));
        root.setBorder(BorderFactory.createEmptyBorder(6, 10, 2, 10));

        // ── Linha do combobox ──────────────────────────────────────
        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));

        JLabel modeLabel = makeLabel("Modo:");
        styleModeCombo();

        topRow.add(modeLabel);
        topRow.add(modeCombo);
        root.add(topRow, BorderLayout.NORTH);

        // ── Cards (Manual | Porcentagem) ───────────────────────────
        modeCards.add(buildManualPanel(),     CARD_MANUAL);
        modeCards.add(buildPercentagePanel(), CARD_PCT);
        root.add(modeCards, BorderLayout.CENTER);

        // Listener do combo
        modeCombo.addActionListener(e -> switchMode());

        return root;
    }

    // ── Card Manual ───────────────────────────────────────────────

    private JPanel buildManualPanel() {
        JPanel panel = new JPanel(new GridBagLayout());

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 3, 3, 3);
        g.anchor = GridBagConstraints.WEST;

        // Linha 0 — Largura
        g.gridy = 0;
        g.gridx = 0; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        panel.add(makeLabel("Largura:"), g);

        styleField(widthField);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        panel.add(widthField, g);

        g.gridx = 2; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        panel.add(makeLabel("px"), g);

        // Cadeado (2 linhas)
        styleLockButton();
        g.gridx = 3; g.gridy = 0; g.gridheight = 2;
        g.anchor = GridBagConstraints.CENTER;
        panel.add(lockBtn, g);
        g.gridheight = 1;
        g.anchor = GridBagConstraints.WEST;

        // Linha 1 — Altura
        g.gridy = 1;
        g.gridx = 0; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        panel.add(makeLabel("Altura:"), g);

        styleField(heightField);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        panel.add(heightField, g);

        g.gridx = 2; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        panel.add(makeLabel("px"), g);

        // Listeners
        widthField.getDocument().addDocumentListener(
                onTextChange(this::onWidthChanged));
        heightField.getDocument().addDocumentListener(
                onTextChange(this::onHeightChanged));

        widthField.addFocusListener(onFocusLost(
                () -> { if (widthField.getText().isBlank())  commitWidth(-1); }));
        heightField.addFocusListener(onFocusLost(
                () -> { if (heightField.getText().isBlank()) commitHeight(-1); }));

        return panel;
    }

    // ── Card Porcentagem ──────────────────────────────────────────

    private JPanel buildPercentagePanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        styleField(pctField);

        // Permite dígitos e ponto/vírgula
        ((AbstractDocument) pctField.getDocument()).setDocumentFilter(
                new DecimalFilter());

        panel.add(makeLabel("Escala:"));
        panel.add(pctField);
        panel.add(makeLabel("%  (100 = tamanho original)"));

        pctField.getDocument().addDocumentListener(onTextChange(() -> {
            double v = parseDouble(pctField, 100.0);
            resizeAction.setPercentage(Math.max(1.0, v));
            refreshSummary();
            editorFrame.requestPreviewRefresh();
        }));

        return panel;
    }

    // ── Troca de modo ─────────────────────────────────────────────

    private void switchMode() {
        boolean isManual = modeCombo.getSelectedIndex() == 0;
        ImageResizeAction.Mode newMode = isManual
                ? ImageResizeAction.Mode.MANUAL
                : ImageResizeAction.Mode.PERCENTAGE;

        resizeAction.setMode(newMode);

        CardLayout cl = (CardLayout) modeCards.getLayout();
        cl.show(modeCards, isManual ? CARD_MANUAL : CARD_PCT);

        // Limpa o estado do modo que saiu para evitar conflitos
        if (isManual) {
            resizeAction.setPercentage(100.0);
        } else {
            resizeAction.setTargetWidth(-1);
            resizeAction.setTargetHeight(-1);
        }

        applyCardHeight();
        refreshSummary();
        editorFrame.requestPreviewRefresh();
    }

    private void applyCardHeight() {
        boolean isManual = modeCombo.getSelectedIndex() == 0;
        int h = isManual ? CARD_HEIGHT_MANUAL : CARD_HEIGHT_PERCENTAGE;
        setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        // Notifica o pai para recalcular o layout
        Container parent = getParent();
        if (parent != null) parent.revalidate();
    }

    // ── Lógica de proporção (modo manual) ────────────────────────

    private void onWidthChanged() {
        if (adjusting) return;
        int w = parseInt(widthField);
        if (w <= 0) { commitWidth(-1); return; }
        commitWidth(w);

        if (lockBtn.isSelected() && aspectRatio > 0) {
            int h = Math.max(1, (int) Math.round(w * aspectRatio));
            adjusting = true;
            heightField.setText(String.valueOf(h));
            adjusting = false;
            commitHeight(h);
        } else {
            int h = parseInt(heightField);
            if (h > 0) aspectRatio = (double) h / w;
        }
        editorFrame.requestPreviewRefresh();
    }

    private void onHeightChanged() {
        if (adjusting) return;
        int h = parseInt(heightField);
        if (h <= 0) { commitHeight(-1); return; }
        commitHeight(h);

        if (lockBtn.isSelected() && aspectRatio > 0) {
            int w = Math.max(1, (int) Math.round(h / aspectRatio));
            adjusting = true;
            widthField.setText(String.valueOf(w));
            adjusting = false;
            commitWidth(w);
        } else {
            int w = parseInt(widthField);
            if (w > 0) aspectRatio = (double) h / w;
        }
        editorFrame.requestPreviewRefresh();
    }

    private void commitWidth(int v)  { resizeAction.setTargetWidth(v);  refreshSummary(); }
    private void commitHeight(int v) { resizeAction.setTargetHeight(v); refreshSummary(); }

    // ── Estilo ────────────────────────────────────────────────────

    private void styleModeCombo() {
        modeCombo.setFont(UIConfig.FONT_DEFAULT);
        modeCombo.setFocusable(false);
    }

    private void styleField(JTextField f) {
        f.setFont(UIConfig.FONT_DEFAULT);
    }

    private void styleLockButton() {
        lockBtn.setSelected(true);
        lockBtn.setToolTipText("Restringir proporções");
        lockBtn.setText(lockIcon(true));
        lockBtn.setFont(UIConfig.FONT_DEFAULT);
        lockBtn.setForeground(UIConfig.BLUE);
        lockBtn.setBorderPainted(false);
        lockBtn.setFocusPainted(false);
        lockBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        lockBtn.setPreferredSize(new Dimension(28, 28));
        lockBtn.addActionListener(e -> {
            boolean locked = lockBtn.isSelected();
            lockBtn.setText(lockIcon(locked));
            lockBtn.setForeground(locked
                    ? UIConfig.BLUE: UIConfig.RED);
            if (locked) {
                int w = parseInt(widthField);
                int h = parseInt(heightField);
                if (w > 0 && h > 0) aspectRatio = (double) h / w;
            }
        });
    }

    private static String lockIcon(boolean locked) { return locked ? "🔒" : "🔓"; }

    private static JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UIConfig.FONT_DEFAULT);
        return l;
    }

    // ── Parsing ───────────────────────────────────────────────────

    private static int parseInt(JTextField f) {
        try { return Integer.parseInt(f.getText().trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    private static double parseDouble(JTextField f, double fallback) {
        try { return Double.parseDouble(f.getText().trim().replace(',', '.')); }
        catch (NumberFormatException e) { return fallback; }
    }

    // ── Factories de listeners ────────────────────────────────────

    private static DocumentListener onTextChange(Runnable r) {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { r.run(); }
            public void removeUpdate(DocumentEvent e)  { r.run(); }
            public void changedUpdate(DocumentEvent e) { r.run(); }
        };
    }

    private static FocusListener onFocusLost(Runnable r) {
        return new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { r.run(); }
        };
    }

    // ── Filtros de entrada ────────────────────────────────────────

    /** Apenas dígitos — usado nos campos de px. */
    private static class DigitsOnlyFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int off, String s, AttributeSet a)
                throws BadLocationException {
            if (s != null && s.matches("\\d+")) super.insertString(fb, off, s, a);
        }
        @Override
        public void replace(FilterBypass fb, int off, int len, String s, AttributeSet a)
                throws BadLocationException {
            if (s != null && s.matches("\\d*")) super.replace(fb, off, len, s, a);
        }
    }

    /** Dígitos + ponto/vírgula — usado no campo de porcentagem. */
    private static class DecimalFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int off, String s, AttributeSet a)
                throws BadLocationException {
            if (s != null && s.matches("[\\d.,]*")) super.insertString(fb, off, s, a);
        }
        @Override
        public void replace(FilterBypass fb, int off, int len, String s, AttributeSet a)
                throws BadLocationException {
            if (s != null && s.matches("[\\d.,]*")) super.replace(fb, off, len, s, a);
        }
    }
}