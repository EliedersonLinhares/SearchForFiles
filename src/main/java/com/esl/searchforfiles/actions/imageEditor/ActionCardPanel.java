package com.esl.searchforfiles.actions.imageEditor;


import com.esl.searchforfiles.configuration.UIConfig;
import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class ActionCardPanel extends JPanel {

    private final ImageEditAction action;
    private final JCheckBox checkBox;
    private final JLabel summaryLabel;
    private Runnable onToggle;   // ← novo campo opcional
    private Color borderColor;

    public ActionCardPanel(ImageEditAction action, Consumer<ActionCardPanel> onRemove) {
        this.action = action;
        setLayout(new BorderLayout());
      //  setBorder( BorderFactory.createLineBorder( borderColor, 1));
    //    setBackground(new Color(50, 50, 50));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));


        // ── Cabeçalho ──────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setBackground(UIConfig.sliderTrackColor());
        header.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 6));

        checkBox = new JCheckBox(action.getName(), action.isEnabled());
       // checkBox.setForeground(Color.WHITE);
      //  checkBox.setBackground(new Color(42, 42, 42));
        checkBox.setFont(UIConfig.FONT_DEFAULT_BOLD);
        checkBox.addActionListener(e -> {
            action.setEnabled(checkBox.isSelected());
            resetAction();   // ← linha adicionada
        });
        refresh();
        header.add(checkBox, BorderLayout.CENTER);

        JButton closeBtn = new JButton("✕");
        closeBtn.setPreferredSize(new Dimension(20, 20));
        closeBtn.setFont(UIConfig.FONT_DEFAULT);
//        closeBtn.setForeground(new Color(180, 180, 180));
//        closeBtn.setBackground(new Color(60, 60, 60));
        closeBtn.setBorder(BorderFactory.createLineBorder(new Color(90, 90, 90)));
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> {
            onRemove.accept(this);
            resetAction();
        });
        header.add(closeBtn, BorderLayout.EAST);

        // ── Corpo ───────────────────────────────────────────────────
        summaryLabel = new JLabel(action.getSummary());
        summaryLabel.setFont(UIConfig.FONT_DEFAULT);
     //   summaryLabel.setForeground(new Color(160, 160, 160));
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 8));

        add(header,       BorderLayout.NORTH);
        add(summaryLabel, BorderLayout.CENTER);

        // 1. Adiciona o evento de escuta ao JCheckBox
        checkBox.addItemListener(e -> {
            // Verifica se o checkbox foi selecionado ou desmarcado
            refresh();
        });
    }

    private void refresh() {
//        if (checkBox.isSelected()) {
//            // Altera a cor da borda para uma cor personalizada (ex: laranja do FlatLaf ou Hex)
//            // Ativa uma borda de 2 pixels, cor customizada e cantos arredondados (opcional)
//            this.putClientProperty(FlatClientProperties.STYLE,
//                    "border: 2,2,2,2, @accentColor, 10"  // espessura norte,oeste,sul,leste, cor, arco
//
//            );
//        } else {
//            // Remove o estilo customizado voltando para a borda padrão do FlatLaf
//            this.putClientProperty(FlatClientProperties.STYLE, "border: 2,2,2,2, @foregroundColor; ");
//        }
        if (checkBox.isSelected()) {
            // Busca a cor do Accent dinamicamente através da sua classe utilitária
            this.setBorder(BorderFactory.createLineBorder(UIConfig.accent(), 2));
        } else {
            this.setBorder(BorderFactory.createLineBorder(UIConfig.foreground(), 1));
        }


        this.revalidate();
        // 2. OBRIGATÓRIO: Força o componente a se redesenhar com a nova cor
        this.repaint();
    }

    public void resetAction() {
        if (onToggle != null) onToggle.run();   // ← linha adicionada
    }

    /** Define um callback extra para quando o checkbox for clicado. */
    public void setOnToggle(Runnable r) { this.onToggle = r; }
    public ImageEditAction getAction() { return action; }

    /** Atualiza o texto resumido após mudança de parâmetros. */
    public void refreshSummary() { summaryLabel.setText(action.getSummary()); }
}
