package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.model.FileInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.List;

/**
 * Painel para exibir resultados da busca em grid
 */
public class ResultsPanel extends JPanel {

    private final JPanel gridPanel;
    private final JScrollPane scrollPane;
    private FileItemClickListener clickListener;

    public ResultsPanel() {
        setLayout(new BorderLayout());

        gridPanel = new JPanel(null); // Layout manual
        gridPanel.setBackground(Color.WHITE);

        scrollPane = new JScrollPane(gridPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);

        add(scrollPane, BorderLayout.CENTER);

        // Listener para redimensionamento
        scrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                // Re-renderiza ao redimensionar
                if (gridPanel.getComponentCount() > 0) {
                    Component first = gridPanel.getComponent(0);
                    if (first instanceof FileItemPanel) {
                        // Tem resultados, re-renderiza
                        revalidate();
                        repaint();
                    }
                }
            }
        });
    }

    public void showResults(List<FileInfo> results) {
        gridPanel.removeAll();
        gridPanel.setLayout(null);

        int panelWidth = scrollPane.getViewport().getWidth();
        if (panelWidth <= 0) {
            panelWidth = getWidth();
        }

        int spacing = 15;
        int minItemWidth = 130;
        int itemHeight = 140;

        int itemsPerRow = Math.max(1, (panelWidth - spacing) / (minItemWidth + spacing));
        int dynamicWidth = (panelWidth - (itemsPerRow + 1) * spacing) / itemsPerRow;

        int x = spacing;
        int y = spacing;
        int count = 0;

        for (FileInfo fileInfo : results) {
            File file = new File(fileInfo.getPath());
            if (!file.exists()) continue;

            FileItemPanel item = new FileItemPanel(file, fileInfo, dynamicWidth, itemHeight);
            item.setBounds(x, y, dynamicWidth, itemHeight);

            // Listener de clique
            if (clickListener != null) {
                item.setClickListener(clickListener);
            }

            gridPanel.add(item);

            count++;
            if (count % itemsPerRow == 0) {
                x = spacing;
                y += itemHeight + spacing;
            } else {
                x += dynamicWidth + spacing;
            }
        }

        gridPanel.setPreferredSize(new Dimension(panelWidth, y + itemHeight + spacing));
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    public void showMessage(String message, MessageType type) {
        gridPanel.removeAll();
        gridPanel.setLayout(new BorderLayout());

        JPanel messagePanel = createMessagePanel(message, type);
        gridPanel.add(messagePanel, BorderLayout.CENTER);
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private JPanel createMessagePanel(String message, MessageType type) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);

        String icon = switch (type) {
            case WELCOME -> "🔍";
            case LOADING -> "⏳";
            case NO_RESULTS -> "❌";
            case ERROR -> "⚠️";
        };

        JLabel iconLabel = new JLabel(icon);
        iconLabel.setFont(new Font("SansSerif", Font.PLAIN, 48));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel messageLabel = new JLabel(message);
        messageLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(Box.createVerticalGlue());
        panel.add(iconLabel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(messageLabel);
        panel.add(Box.createVerticalGlue());

        return panel;
    }

    public void setFileItemClickListener(FileItemClickListener listener) {
        this.clickListener = listener;
    }

    public enum MessageType {
        WELCOME, LOADING, NO_RESULTS, ERROR
    }

    public interface FileItemClickListener {
        void onFileDoubleClick(File file);
        void onFileRightClick(File file, FileInfo fileInfo, Component source, int x, int y);
    }
}