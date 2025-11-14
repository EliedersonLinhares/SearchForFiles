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

    // Armazena últimos resultados para re-renderizar ao redimensionar
    private List<FileInfo> lastResults;

    // Cor de fundo customizável
    private Color backgroundColor = new Color(245, 245, 250); // Cinza azulado claro

    public ResultsPanel() {
        setLayout(new BorderLayout());

        gridPanel = new JPanel(null); // Layout manual
        gridPanel.setBackground(backgroundColor);

        scrollPane = new JScrollPane(gridPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);

        add(scrollPane, BorderLayout.CENTER);

        // Listener para redimensionamento
        // CORREÇÃO: Listener otimizado para redimensionamento fluido
        setupResizeListener();
    }
    /**
     * Configura listener para redimensionamento fluido
     * NOVO MÉTODO
     */
    private void setupResizeListener() {
        // Timer para debounce do redimensionamento
        Timer resizeTimer = new Timer(150, e -> {
            if (lastResults != null && !lastResults.isEmpty()) {
                renderGrid(lastResults);
            }
        });
        resizeTimer.setRepeats(false);

        // Listener de redimensionamento
        scrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                // Debounce: só re-renderiza 150ms após parar de redimensionar
                resizeTimer.restart();
            }
        });
    }
//    public void showResults(List<FileInfo> results) {
//        gridPanel.removeAll();
//        gridPanel.setLayout(null);
//
//        int panelWidth = scrollPane.getViewport().getWidth();
//        if (panelWidth <= 0) {
//            panelWidth = getWidth();
//        }
//
//        int spacing = 15;
//        int minItemWidth = 130;
//        int itemHeight = 140;
//
//        int itemsPerRow = Math.max(1, (panelWidth - spacing) / (minItemWidth + spacing));
//        int dynamicWidth = (panelWidth - (itemsPerRow + 1) * spacing) / itemsPerRow;
//
//        int x = spacing;
//        int y = spacing;
//        int count = 0;
//
//        for (FileInfo fileInfo : results) {
//            File file = new File(fileInfo.getPath());
//            if (!file.exists()) continue;
//
//            FileItemPanel item = new FileItemPanel(file, fileInfo, dynamicWidth, itemHeight);
//            item.setBounds(x, y, dynamicWidth, itemHeight);
//
//            // Listener de clique
//            if (clickListener != null) {
//                item.setClickListener(clickListener);
//            }
//
//            gridPanel.add(item);
//
//            count++;
//            if (count % itemsPerRow == 0) {
//                x = spacing;
//                y += itemHeight + spacing;
//            } else {
//                x += dynamicWidth + spacing;
//            }
//        }
//
//        gridPanel.setPreferredSize(new Dimension(panelWidth, y + itemHeight + spacing));
//        gridPanel.revalidate();
//        gridPanel.repaint();
//    }
    /**
     * Exibe resultados no grid
     * MODIFICADO: Armazena resultados para re-renderização
     */
    public void showResults(List<FileInfo> results) {
        this.lastResults = results;
        renderGrid(results);
    }

    /**
     * Renderiza o grid de resultados
     * NOVO MÉTODO: Separado para facilitar re-renderização
     */
    private void renderGrid(List<FileInfo> results) {
        gridPanel.removeAll();
        gridPanel.setLayout(null);
        gridPanel.setBackground(backgroundColor);

        int panelWidth = scrollPane.getViewport().getWidth();
        if (panelWidth <= 0) {
            panelWidth = getWidth();
        }

        // Se ainda não tem largura válida, tenta novamente depois
        if (panelWidth <= 100) {
            SwingUtilities.invokeLater(() -> renderGrid(results));
            return;
        }

        int spacing = 15;
        int minItemWidth = 130;
        int itemHeight = 140;

        // Calcula quantos itens cabem por linha
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

        // Calcula altura total necessária
        int totalRows = (int) Math.ceil((double) count / itemsPerRow);
        int totalHeight = spacing + (totalRows * (itemHeight + spacing));

        gridPanel.setPreferredSize(new Dimension(panelWidth, totalHeight));
        gridPanel.revalidate();
        gridPanel.repaint();
    }

//    public void showMessage(String message, MessageType type) {
//        gridPanel.removeAll();
//        gridPanel.setLayout(new BorderLayout());
//
//        JPanel messagePanel = createMessagePanel(message, type);
//        gridPanel.add(messagePanel, BorderLayout.CENTER);
//        gridPanel.revalidate();
//        gridPanel.repaint();
//    }
    /**
     * Exibe mensagem centralizada
     * MODIFICADO: Usa backgroundColor
     */
    public void showMessage(String message, MessageType type) {
        this.lastResults = null; // Limpa últimos resultados

        gridPanel.removeAll();
        gridPanel.setLayout(new BorderLayout());
        gridPanel.setBackground(backgroundColor);

        JPanel messagePanel = createMessagePanel(message, type);
        gridPanel.add(messagePanel, BorderLayout.CENTER);
        gridPanel.revalidate();
        gridPanel.repaint();
    }

//    private JPanel createMessagePanel(String message, MessageType type) {
//        JPanel panel = new JPanel();
//        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
//        panel.setBackground(Color.WHITE);
//
//        String icon = switch (type) {
//            case WELCOME -> "🔍";
//            case LOADING -> "⏳";
//            case NO_RESULTS -> "❌";
//            case ERROR -> "⚠️";
//        };
//
//        JLabel iconLabel = new JLabel(icon);
//        iconLabel.setFont(new Font("SansSerif", Font.PLAIN, 48));
//        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
//
//        JLabel messageLabel = new JLabel(message);
//        messageLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
//        messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
//
//        panel.add(Box.createVerticalGlue());
//        panel.add(iconLabel);
//        panel.add(Box.createVerticalStrut(10));
//        panel.add(messageLabel);
//        panel.add(Box.createVerticalGlue());
//
//        return panel;
//    }
    /**
     * Cria painel de mensagem
     * MODIFICADO: Usa backgroundColor
     */
    private JPanel createMessagePanel(String message, MessageType type) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(backgroundColor);

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
    /**
     * Define cor de fundo do painel
     * NOVO MÉTODO
     */
    public void setBackgroundColor(Color color) {
        this.backgroundColor = color;
        gridPanel.setBackground(color);

        // Atualiza todos os componentes filhos
        for (Component comp : gridPanel.getComponents()) {
            if (comp instanceof JPanel && !(comp instanceof FileItemPanel)) {
                comp.setBackground(color);
            }
        }

        repaint();
    }

    /**
     * Obtém cor de fundo atual
     * NOVO MÉTODO
     */
    public Color getBackgroundColor() {
        return backgroundColor;
    }

    /**
     * Força re-renderização dos resultados atuais
     * NOVO MÉTODO: Útil para atualizar após mudanças de tema
     */
    public void refresh() {
        if (lastResults != null && !lastResults.isEmpty()) {
            renderGrid(lastResults);
        }
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