package com.esl.searchforfiles.compressedFiles;

import javax.swing.*;
import java.awt.*;

public class CompressedDialog  extends JDialog {
    private JProgressBar progressBar;
    private JLabel lblStatus;

    public CompressedDialog (Frame owner, String title) {
        super(owner, title, false); // true indica que é modal (bloqueia a janela de trás)
        setLayout(new BorderLayout(10, 10));
        setSize(400, 120);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); // Impede o usuário de fechar no "X" no meio do processo

        lblStatus = new JLabel("Iniciando...");
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true); // Mostra a porcentagem em texto

        JPanel painel = new JPanel(new BorderLayout(5, 5));
        painel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        painel.add(lblStatus, BorderLayout.NORTH);
        painel.add(progressBar, BorderLayout.CENTER);

        add(painel);
    }

    public void atualizar(int percent, String work) {
        progressBar.setValue(percent);
        lblStatus.setText(work);
    }
    public int getProgressBarValue() { return progressBar.getValue(); }
}