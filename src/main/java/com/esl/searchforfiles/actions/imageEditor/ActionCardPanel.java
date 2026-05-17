package com.esl.searchforfiles.actions.imageEditor;


import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class ActionCardPanel extends JPanel {

    private final ImageEditAction action;
    private final JCheckBox checkBox;
    private final JLabel summaryLabel;

    public ActionCardPanel(ImageEditAction action, Consumer<ActionCardPanel> onRemove) {
        this.action = action;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80), 1));
        setBackground(new Color(50, 50, 50));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));

        // ── Cabeçalho ──────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setBackground(new Color(42, 42, 42));
        header.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 6));

        checkBox = new JCheckBox(action.getName(), action.isEnabled());
        checkBox.setForeground(Color.WHITE);
        checkBox.setBackground(new Color(42, 42, 42));
        checkBox.setFont(checkBox.getFont().deriveFont(Font.BOLD, 12f));
        checkBox.addActionListener(e -> action.setEnabled(checkBox.isSelected()));
        header.add(checkBox, BorderLayout.CENTER);

        JButton closeBtn = new JButton("✕");
        closeBtn.setPreferredSize(new Dimension(20, 20));
        closeBtn.setFont(closeBtn.getFont().deriveFont(11f));
        closeBtn.setForeground(new Color(180, 180, 180));
        closeBtn.setBackground(new Color(60, 60, 60));
        closeBtn.setBorder(BorderFactory.createLineBorder(new Color(90, 90, 90)));
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> onRemove.accept(this));
        header.add(closeBtn, BorderLayout.EAST);

        // ── Corpo ───────────────────────────────────────────────────
        summaryLabel = new JLabel(action.getSummary());
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(11f));
        summaryLabel.setForeground(new Color(160, 160, 160));
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 8));

        add(header,       BorderLayout.NORTH);
        add(summaryLabel, BorderLayout.CENTER);
    }

    public ImageEditAction getAction() { return action; }

    /** Atualiza o texto resumido após mudança de parâmetros. */
    public void refreshSummary() { summaryLabel.setText(action.getSummary()); }
}
