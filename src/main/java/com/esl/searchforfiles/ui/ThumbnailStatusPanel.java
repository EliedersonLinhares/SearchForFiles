package com.esl.searchforfiles.ui;

import javax.swing.*;
import java.awt.*;

public class ThumbnailStatusPanel extends JPanel {

    private JProgressBar progressBar;
    private JLabel statusLabel;
    private int totalVideos = 0;
    private int processedVideos = 0;

    public ThumbnailStatusPanel() {
        setLayout(new BorderLayout(5, 0));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        statusLabel = new JLabel("Pronto");
        add(statusLabel, BorderLayout.WEST);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(200, 20));
        progressBar.setVisible(false);
        add(progressBar, BorderLayout.EAST);
    }

    public void startProcessing(int totalVideos) {
        this.totalVideos = totalVideos;
        this.processedVideos = 0;
        progressBar.setVisible(true);
        progressBar.setValue(0);
        updateStatus();
    }

    public void incrementProcessed() {
        processedVideos++;
        updateStatus();

        if (processedVideos >= totalVideos) {
            SwingUtilities.invokeLater(() -> {
                Timer timer = new Timer(2000, e -> {
                    progressBar.setVisible(false);
                    statusLabel.setText("Pronto");
                });
                timer.setRepeats(false);
                timer.start();
            });
        }
    }

    private void updateStatus() {
        SwingUtilities.invokeLater(() -> {
            int percent = (int) ((processedVideos / (double) totalVideos) * 100);
            progressBar.setValue(percent);
            statusLabel.setText(String.format(
                    "Processando thumbnails: %d/%d",
                    processedVideos,
                    totalVideos
            ));
        });
    }
}
