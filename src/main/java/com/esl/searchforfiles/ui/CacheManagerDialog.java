package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.cache.thumbnail.ThumbnailCacheManager;
import com.esl.searchforfiles.configuration.UIConfig;

import javax.swing.*;
import java.awt.*;

/**
 * Diálogo para gerenciar o cache de thumbnails de vídeos
 */
public class CacheManagerDialog extends JDialog {

    private final ThumbnailCacheManager cacheManager;
    private JLabel cacheInfoLabel;
    private JLabel cacheSizeLabel;
    private JLabel thumbnailCountLabel;
    private JLabel cacheLocationLabel;

    public CacheManagerDialog(Frame parent, ThumbnailCacheManager cacheManager) {
        super(parent, "Gerenciador de Cache de Thumbnails", true);
        this.cacheManager = cacheManager;

        initComponents();
        updateCacheInfo();

        setSize(500, 300);
        setLocationRelativeTo(parent);
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));

        // Painel de informações
        JPanel infoPanel = createInfoPanel();
        add(infoPanel, BorderLayout.CENTER);

        // Painel de botões
        JPanel buttonPanel = createButtonPanel();
        add(buttonPanel, BorderLayout.SOUTH);

        // Margem
        ((JPanel) getContentPane()).setBorder(
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        );
    }

    private JPanel createInfoPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Informações do Cache"));

        // Localização
        JPanel locationPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        locationPanel.add(new JLabel("Localização: "));
        cacheLocationLabel = new JLabel();
        cacheLocationLabel.setFont(UIConfig.FONT_SMALL);
        locationPanel.add(cacheLocationLabel);
        panel.add(locationPanel);

        panel.add(Box.createVerticalStrut(10));

        // Quantidade de thumbnails
        JPanel countPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        countPanel.add(new JLabel("Thumbnails salvos: "));
        thumbnailCountLabel = new JLabel("0");
        thumbnailCountLabel.setFont(UIConfig.FONT_SMALL);
        countPanel.add(thumbnailCountLabel);
        panel.add(countPanel);

        // Tamanho do cache
        JPanel sizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        sizePanel.add(new JLabel("Tamanho total: "));
        cacheSizeLabel = new JLabel("0 B");
        cacheSizeLabel.setFont(UIConfig.FONT_SMALL);
        sizePanel.add(cacheSizeLabel);
        panel.add(sizePanel);

        panel.add(Box.createVerticalStrut(15));

        // Informações adicionais
        cacheInfoLabel = new JLabel("<html><i>O cache armazena thumbnails de vídeos " +
                "para carregamento mais rápido.</i></html>");
        cacheInfoLabel.setForeground(Color.GRAY);
        panel.add(cacheInfoLabel);

        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        // Botão Atualizar
        JButton refreshButton = new JButton("Atualizar");
        refreshButton.addActionListener(e -> updateCacheInfo());
        panel.add(refreshButton);

        // Botão Abrir Pasta
        JButton openFolderButton = new JButton("Abrir Pasta");
        openFolderButton.addActionListener(e -> openCacheFolder());
        panel.add(openFolderButton);

        // Botão Limpar Antigos
        JButton clearOldButton = new JButton("Limpar Antigos (30 dias)");
        clearOldButton.addActionListener(e -> clearOldThumbnails());
        panel.add(clearOldButton);

        // Botão Limpar Tudo
        JButton clearAllButton = new JButton("Limpar Tudo");
        clearAllButton.setForeground(new Color(200, 50, 50));
        clearAllButton.addActionListener(e -> clearAllCache());
        panel.add(clearAllButton);

        // Botão Fechar
        JButton closeButton = new JButton("Fechar");
        closeButton.addActionListener(e -> dispose());
        panel.add(closeButton);

        return panel;
    }

    private void updateCacheInfo() {
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private long count;
            private String size;
            private String location;

            @Override
            protected Void doInBackground() throws Exception {
                count = cacheManager.getThumbnailCount();
                size = cacheManager.getCacheSizeFormatted();
                location = cacheManager.getCacheDirectory().toString();
                return null;
            }

            @Override
            protected void done() {
                thumbnailCountLabel.setText(String.valueOf(count));
                cacheSizeLabel.setText(size);
                cacheLocationLabel.setText(location);
            }
        };

        worker.execute();
    }

    private void openCacheFolder() {
        try {
            Desktop.getDesktop().open(cacheManager.getCacheDirectory().toFile());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Erro ao abrir pasta: " + e.getMessage(),
                    "Erro",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void clearOldThumbnails() {
        int result = JOptionPane.showConfirmDialog(
                this,
                "Deseja remover thumbnails mais antigos que 30 dias?",
                "Confirmar Limpeza",
                JOptionPane.YES_NO_OPTION
        );

        if (result == JOptionPane.YES_OPTION) {
            SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    cacheManager.clearOldThumbnails(30);
                    return null;
                }

                @Override
                protected void done() {
                    updateCacheInfo();
                    JOptionPane.showMessageDialog(
                            CacheManagerDialog.this,
                            "Thumbnails antigos removidos com sucesso!",
                            "Sucesso",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                }
            };

            worker.execute();
        }
    }

    private void clearAllCache() {
        int result = JOptionPane.showConfirmDialog(
                this,
                "Tem certeza que deseja limpar TODO o cache?\n" +
                        "Todos os thumbnails salvos serão removidos.",
                "Confirmar Limpeza Total",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (result == JOptionPane.YES_OPTION) {
            SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    cacheManager.clearCache();
                    return null;
                }

                @Override
                protected void done() {
                    updateCacheInfo();
                    JOptionPane.showMessageDialog(
                            CacheManagerDialog.this,
                            "Cache limpo com sucesso!",
                            "Sucesso",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                }
            };

            worker.execute();
        }
    }

    // Método estático para mostrar o diálogo facilmente
    public static void showDialog(Frame parent, ThumbnailCacheManager cacheManager) {
        CacheManagerDialog dialog = new CacheManagerDialog(parent, cacheManager);
        dialog.setVisible(true);
    }
}
