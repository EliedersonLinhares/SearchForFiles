package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.configuration.UIConfig;

import javax.swing.*;
import java.awt.*;

// ═══════════════════════════════════════════════════════════════
// BlockingDialog — diálogo modal de espera, sem botões.
// Bloqueia a janela pai enquanto estiver visível.
// Uso direto:
//   BlockingDialog dlg = new BlockingDialog(owner, "Aguarde...");
//   dlg.show();
//   // ... faz algo em background ...
//   dlg.hide();
// ═══════════════════════════════════════════════════════════════
public class BlockingDialog {

    private final JDialog dialog;
    private final JLabel  messageLabel;
    private final JLabel  subLabel;

    /**
     * @param owner   Janela pai que será bloqueada (desabilitada)
     * @param message Mensagem principal exibida no diálogo
     */
    public BlockingDialog(Window owner, String message) {
        this(owner, message, null);
    }

    /**
     * @param owner      Janela pai que será bloqueada
     * @param message    Mensagem principal
     * @param subMessage Mensagem secundária (pode ser null)
     */
    public BlockingDialog(Window owner, String message, String subMessage) {
        dialog = new JDialog(owner, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);          // sem barra de título
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setResizable(false);

        // ── Painel interno ────────────────────────────────────────
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        UIManager.getColor("Component.borderColor"), 1),
                BorderFactory.createEmptyBorder(24, 32, 24, 32)));

        // Spinner animado (JProgressBar indeterminado)
        JProgressBar spinner = new JProgressBar();
        spinner.setIndeterminate(true);
        spinner.setPreferredSize(new Dimension(220, 6));
        spinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        spinner.setAlignmentX(Component.CENTER_ALIGNMENT);
        spinner.setBorderPainted(false);

        // Mensagem principal
        messageLabel = new JLabel(message);
        messageLabel.setFont(UIConfig.FONT_DEFAULT_BOLD);
        messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Mensagem secundária (opcional)
        subLabel = new JLabel(subMessage != null ? subMessage : " ");
        subLabel.setFont(UIConfig.FONT_SMALL);
        subLabel.setForeground(Color.GRAY);
        subLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(messageLabel);
        panel.add(Box.createVerticalStrut(6));
        panel.add(subLabel);
        panel.add(Box.createVerticalStrut(14));
        panel.add(spinner);

        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
    }

    // ── API pública ───────────────────────────────────────────────

    /**
     * Atualiza a mensagem principal em tempo real (thread-safe).
     */
    public void setMessage(String message) {
        SwingUtilities.invokeLater(() -> messageLabel.setText(message));
    }

    /**
     * Atualiza a mensagem secundária em tempo real (thread-safe).
     */
    public void setSubMessage(String message) {
        SwingUtilities.invokeLater(() ->
                subLabel.setText(message != null ? message : " "));
    }

    /**
     * Exibe o diálogo de forma NÃO bloqueante para o código chamador.
     * O bloqueio ocorre apenas para o usuário (modal).
     * Deve ser chamado na EDT.
     */
    public void show() {
        // invokeLater permite que o código após show() continue executando
        // enquanto o diálogo fica visível e bloqueando a interação do usuário.
        SwingUtilities.invokeLater(() -> dialog.setVisible(true));
    }

    /**
     * Fecha o diálogo. Thread-safe.
     */
    public void hide() {
        SwingUtilities.invokeLater(() -> dialog.dispose());
    }

    public JDialog getDialog() { return dialog; }
}