package com.esl.searchforfiles.actions.imageEditor.actions.ImageAdjust;


import com.esl.searchforfiles.actions.imageEditor.ActionCardPanel;
import com.esl.searchforfiles.actions.imageEditor.ImageEditorFrame;
import com.esl.searchforfiles.configuration.UIConfig;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * Card de ação para {@link ImageAdjustAction}.
 * Exibe 4 sliders (brilho, contraste, gamma, saturação) diretamente no card,
 * sem precisar abrir um diálogo separado.
 *
 * Layout:
 *   ┌─ header (checkbox + botão fechar) ─────────────────┐
 *   │ Brilho     [────●──────────]  0.00                  │
 *   │ Contraste  [────────●──────]  1.00                  │
 *   │ Gamma      [────────●──────]  1.00                  │
 *   │ Saturação  [────────●──────]  1.00                  │
 *   └────────────────────────────────────────────────────┘
 */
public class AdjustActionCardPanel extends ActionCardPanel {

    private static final int CARD_HEIGHT = 160;

    // Referência tipada para evitar casts
    private final ImageAdjustAction adjustAction;
    private final ImageEditorFrame editorFrame;   // ← novo campo

    public AdjustActionCardPanel(ImageAdjustAction action,
                                 ImageEditorFrame editorFrame,   // ← novo parâmetro
                                 Consumer<ActionCardPanel> onRemove) {
        super(action, onRemove);
        this.adjustAction = action;
        this.editorFrame  = editorFrame;

        remove(getComponent(1));
        add(buildSlidersPanel(), BorderLayout.CENTER);

        setMaximumSize(new Dimension(Integer.MAX_VALUE, CARD_HEIGHT));
        revalidate();
        repaint();
    }

    // ── Painel de sliders ──────────────────────────────────────────
    private JPanel buildSlidersPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
   //     panel.setBackground(new Color(50, 50, 50));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 10, 6, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(1, 2, 1, 2);
        gbc.fill   = GridBagConstraints.HORIZONTAL;
        gbc.gridy  = GridBagConstraints.RELATIVE;

        addSliderRow(panel, gbc, "Brilho",
                -100, 100, (int)(adjustAction.getBrightness() * 100),
                v -> adjustAction.setBrightness(v / 100.0),
                v -> String.format("%.2f", v / 100.0));

        addSliderRow(panel, gbc, "Contraste",
                0, 200, (int)(adjustAction.getContrast() * 100),
                v -> adjustAction.setContrast(v / 100.0),
                v -> String.format("%.2f", v / 100.0));

        addSliderRow(panel, gbc, "Gamma",
                10, 300, (int)(adjustAction.getGamma() * 100),
                v -> adjustAction.setGamma(v / 100.0),
                v -> String.format("%.2f", v / 100.0));

        addSliderRow(panel, gbc, "Saturação",
                0, 300, (int)(adjustAction.getSaturation() * 100),
                v -> adjustAction.setSaturation(v / 100.0),
                v -> String.format("%.2f", v / 100.0));

        return panel;
    }
    /**
     * Adiciona uma linha: [label | slider (flex) | valueLabel].
     *
     * @param panel      painel destino
     * @param gbc        constraints base (gridy=RELATIVE já configurado)
     * @param name       nome do parâmetro
     * @param min        valor mínimo do slider (em inteiro escalado)
     * @param max        valor máximo do slider
     * @param initial    valor inicial
     * @param onChange   callback chamado quando o slider muda (recebe valor int escalado)
     * @param formatter  converte o int escalado em texto para o valueLabel
     */
    private void addSliderRow(JPanel panel, GridBagConstraints gbc,
                              String name, int min, int max, int initial,
                              IntConsumer onChange, IntFunction<String> formatter) {

        JLabel nameLabel = new JLabel(name);
   //     nameLabel.setForeground(new Color(180, 180, 180));
        nameLabel.setFont(UIConfig.FONT_DEFAULT);
        nameLabel.setPreferredSize(new Dimension(66, 16));

        JSlider slider = new JSlider(min, max, initial);
  //      slider.setBackground(new Color(50, 50, 50));
        slider.setFocusable(false);

        JLabel valueLabel = new JLabel(formatter.apply(initial));
   //     valueLabel.setForeground(new Color(140, 140, 140));
        valueLabel.setFont(UIConfig.FONT_DEFAULT);
        valueLabel.setPreferredSize(new Dimension(34, 16));
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        slider.addChangeListener(_ -> {
            onChange.accept(slider.getValue());
            valueLabel.setText(formatter.apply(slider.getValue()));
            refreshSummary();
            editorFrame.requestPreviewRefresh();   // ← dispara o refresh
        });

        gbc.gridx = 0; gbc.weightx = 0; panel.add(nameLabel,  gbc);
        gbc.gridx = 1; gbc.weightx = 1; panel.add(slider,     gbc);
        gbc.gridx = 2; gbc.weightx = 0; panel.add(valueLabel, gbc);
    }
}