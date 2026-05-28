package com.esl.searchforfiles.actions.imageEditor.actions.ImageCrop;


import com.esl.searchforfiles.actions.imageEditor.ActionCardPanel;
import com.esl.searchforfiles.actions.imageEditor.ImageEditorFrame;
import com.esl.searchforfiles.configuration.UIConfig;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Card de configuração da action de crop.
 *
 * Fluxo:
 *  1. Usuário escolhe a proporção no combo.
 *  2. Clica em "Definir região" → o editor entra no modo crop
 *     (via cropModeCallback), exibindo o CropOverlayPanel sobre a preview.
 *  3. Quando o usuário confirma no overlay, o editor chama
 *     applyRegionFromPixels() com as coordenadas e o card atualiza tudo.
 *  4. "Limpar seleção" apaga a região e desativa o efeito.
 */
public class CropActionCardPanel extends ActionCardPanel {

    private static final int CARD_HEIGHT_IDLE   = 130;  // sem seleção ativa
    private static final int CARD_HEIGHT_ACTIVE = 162;  // com botões confirmar/cancelar

    private final ImageCropAction  cropAction;
    private final ImageEditorFrame editorFrame;

    // ── Widgets ───────────────────────────────────────────────────
    private final JComboBox<ImageCropAction.AspectRatioPreset> aspectCombo;
    private final JLabel   regionLabel  = new JLabel("não definida");
    private final JButton  defineBtn    = new JButton("✏ Definir região");
    private final JButton  clearBtn     = new JButton("✕ Limpar");
    private final JButton  confirmBtn   = new JButton("✓ Confirmar");
    private final JButton  cancelSelBtn = new JButton("✕ Cancelar seleção");
    private final JPanel   cropBtnRow   = new JPanel(new GridLayout(1, 2, 6, 0));

    private Consumer<ImageCropAction> cropModeCallback;

    public CropActionCardPanel(ImageCropAction action,
                               ImageEditorFrame editorFrame,
                               Consumer<ActionCardPanel> onRemove) {
        super(action, onRemove);
        this.cropAction  = action;
        this.editorFrame = editorFrame;

        aspectCombo = new JComboBox<>(ImageCropAction.AspectRatioPreset.values());

        remove(getComponent(1));
        add(buildPanel(), BorderLayout.CENTER);

        setCropButtonsVisible(false);   // esconde confirmar/cancelar inicialmente
        setMaximumSize(new Dimension(Integer.MAX_VALUE, CARD_HEIGHT_IDLE));
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

        // Linha 0 — Proporção
        g.gridy = 0; g.gridx = 0; g.weightx = 0;
        root.add(makeLabel("Proporção:"), g);

        styleCombo();
        g.gridx = 1; g.weightx = 1; g.gridwidth = 2;
        root.add(aspectCombo, g);
        g.gridwidth = 1;

        // Linha 1 — Região
        g.gridy = 1; g.gridx = 0; g.weightx = 0;
        root.add(makeLabel("Região:"), g);

        styleRegionLabel();
        g.gridx = 1; g.weightx = 1; g.gridwidth = 2;
        root.add(regionLabel, g);
        g.gridwidth = 1;

        // Linha 2 — Definir / Limpar
        g.gridy = 2; g.gridx = 0; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        root.add(makeLabel(""), g);

        styleBtn(defineBtn, new Color(60, 100, 160), new Color(90, 140, 210));
        g.gridx = 1; g.weightx = 0;
        root.add(defineBtn, g);

        styleBtn(clearBtn, new Color(80, 50, 50), new Color(160, 80, 80));
        g.gridx = 2;
        root.add(clearBtn, g);

        // Linha 3 — Confirmar / Cancelar seleção (visíveis só durante o crop)
        styleBtn(confirmBtn,   new Color(40, 110, 60),  new Color(70, 160, 90));
        styleBtn(cancelSelBtn, new Color(100, 60, 60),  new Color(160, 90, 90));

        cropBtnRow.setBackground(new Color(50, 50, 50));
        cropBtnRow.add(confirmBtn);
        cropBtnRow.add(cancelSelBtn);

        g.gridy = 3; g.gridx = 0; g.weightx = 0; g.gridwidth = 3;
        g.fill  = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(4, 3, 3, 3);
        root.add(cropBtnRow, g);

        // ── Listeners ─────────────────────────────────────────────
        aspectCombo.addActionListener(e -> {
            cropAction.setAspectPreset(
                    (ImageCropAction.AspectRatioPreset) aspectCombo.getSelectedItem());
            refreshSummary();
            if (cropAction.hasRegion()) editorFrame.requestPreviewRefresh();
        });

        defineBtn.addActionListener(e -> {
            if (cropModeCallback != null) {
                cropModeCallback.accept(cropAction);
                setCropButtonsVisible(true);
            }
        });

        clearBtn.addActionListener(e -> {
            cropAction.clearRegion();
            editorFrame.getPreviewPanel().exitCropMode();
            setCropButtonsVisible(false);
            updateRegionLabel();
            refreshSummary();
            editorFrame.requestPreviewRefresh();
        });

        confirmBtn.addActionListener(e -> {
            editorFrame.getPreviewPanel().confirmCrop();
            setCropButtonsVisible(false);
        });

        cancelSelBtn.addActionListener(e -> {
            editorFrame.getPreviewPanel().exitCropMode();
            setCropButtonsVisible(false);
        });

        return root;
    }

    // ── Visibilidade dos botões de crop ───────────────────────────

    private void setCropButtonsVisible(boolean visible) {
        cropBtnRow.setVisible(visible);
        int h = visible ? CARD_HEIGHT_ACTIVE : CARD_HEIGHT_IDLE;
        setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        Container parent = getParent();
        if (parent != null) parent.revalidate();
    }

    // ── API pública ───────────────────────────────────────────────

    public void applyRegionFromPixels(int x, int y, int w, int h, int refW, int refH) {
        cropAction.setCropRegionPixels(x, y, w, h, refW, refH);
        updateRegionLabel();
        refreshSummary();
        editorFrame.requestPreviewRefresh();
    }

    public void setCropModeCallback(Consumer<ImageCropAction> cb) {
        this.cropModeCallback = cb;
    }

    // ── Helpers ───────────────────────────────────────────────────

    private void updateRegionLabel() {
        regionLabel.setFont(UIConfig.FONT_DEFAULT);
        if (cropAction.hasRegion()) {
            double[] r = cropAction.getNormalizedRegion();
            regionLabel.setText(String.format(
                    "X:%.0f%% Y:%.0f%%  W:%.0f%% H:%.0f%%",
                    r[0]*100, r[1]*100, r[2]*100, r[3]*100));
            regionLabel.setForeground(new Color(160, 210, 140));
        } else {
            regionLabel.setText("não definida");
            regionLabel.setForeground(new Color(140, 140, 140));
        }
    }

    private void styleCombo() {
        aspectCombo.setBackground(new Color(55, 55, 55));
        aspectCombo.setForeground(Color.WHITE);
        aspectCombo.setFont(UIConfig.FONT_DEFAULT);
        aspectCombo.setFocusable(false);
    }

    private void styleRegionLabel() {
        regionLabel.setFont(UIConfig.FONT_DEFAULT);
        regionLabel.setForeground(new Color(140, 140, 140));
    }

    private static void styleBtn(JButton btn, Color bg, Color border) {
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(UIConfig.FONT_DEFAULT);
        btn.setBorder(BorderFactory.createLineBorder(border));
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