package com.esl.searchforfiles.ui;


import com.esl.searchforfiles.model.TransferMode;

import javax.swing.*;
import java.awt.*;

public class TransferProgressDialog extends JDialog {

    private final JProgressBar progressBar;
    private final JLabel fileLabel;
    private final JLabel countLabel;

    public TransferProgressDialog(Window owner, TransferMode mode, int total) {
        super(owner, modeTitle(mode), ModalityType.APPLICATION_MODAL);

        setLayout(new BorderLayout(10, 10));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setResizable(false);

        countLabel = new JLabel("0 / " + total);
        countLabel.setFont(countLabel.getFont().deriveFont(Font.BOLD));
        add(countLabel, BorderLayout.NORTH);

        progressBar = new JProgressBar(0, total);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(420, 22));
        add(progressBar, BorderLayout.CENTER);

        fileLabel = new JLabel(" ");
        fileLabel.setFont(fileLabel.getFont().deriveFont(Font.ITALIC, 11f));
        fileLabel.setForeground(Color.GRAY);
        add(fileLabel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    public void update(int done, int total, String fileName) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(done);
            countLabel.setText(done + " / " + total);
            fileLabel.setText(fileName);
        });
    }

    private static String modeTitle(TransferMode m) {
        return switch (m) {
            case COPY   -> "Copiando arquivos…";
            case MOVE   -> "Movendo arquivos…";
            case DELETE -> "Apagando arquivos…";
        };
    }
}