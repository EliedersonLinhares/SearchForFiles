package com.esl.searchforfiles.configuration;


import com.esl.searchforfiles.cache.thumbnail.ThumbnailCacheManager;
import com.esl.searchforfiles.ui.FileItemPanel;
import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Map;

public class CacheConfigPanel extends JPanel {

    private final Frame parentFrame;
    private final ThumbnailCacheManager cacheManager;

    private JLabel thumbnailCountLabel;
    private JLabel cacheSizeLabel;
    private JLabel cacheLocationLabel;

    public CacheConfigPanel(Frame parentFrame, ThumbnailCacheManager cacheManager) {
        this.parentFrame = parentFrame;
        this.cacheManager = cacheManager;

        setLayout(new GridBagLayout()); // centraliza o conteúdo na aba

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.setPreferredSize(new Dimension(780, 400));
        content.add(createInfoPanel(),   BorderLayout.CENTER);
        content.add(createButtonPanel(), BorderLayout.SOUTH);

        add(content); // GridBagLayout sem constraints centraliza automaticamente

        refreshInfo();
    }

    // -------------------------------------------------------------------------
    // Construção da UI
    // -------------------------------------------------------------------------

    private JPanel createInfoPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Informações do Cache"));

        panel.add(labelRow("Localização:",       cacheLocationLabel  = new JLabel("...")));
        panel.add(Box.createVerticalStrut(4));
        panel.add(labelRow("Thumbnails salvos:", thumbnailCountLabel = new JLabel("...")));
        panel.add(labelRow("Tamanho total:",     cacheSizeLabel      = new JLabel("...")));
        panel.add(Box.createVerticalStrut(2));

        JLabel hint = new JLabel("<html><i>Os thumbnails são salvos automaticamente para " +
                "carregar mais rápido na próxima vez que você visualizar os mesmos vídeos.</i></html>");
        hint.setForeground(Color.GRAY);
        hint.setFont(UIConfig.FONT_DEFAULT);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(hint);

        return panel;
    }

    private JPanel labelRow(String labelText, JLabel valueLabel) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lbl = new JLabel(labelText + "  ");
        lbl.setFont(UIConfig.FONT_DEFAULT_BOLD);
        valueLabel.setFont(UIConfig.FONT_DEFAULT);
        row.add(lbl);
        row.add(valueLabel);
        return row;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));

//        JButton btnRefresh = new JButton("🔄 Atualizar Informações");
//        JButton btnOpenFolder = new JButton("📁 Abrir Pasta do Cache");
//        JButton btnClearOld = new JButton("🗑️ Limpar Antigos (30 dias)");
//        JButton btnClearAll = new JButton("❌ Limpar Tudo");
//        btnClearAll.setForeground(new Color(200, 50, 50));

        JButton btnRefresh = makeTextBtn("\uD83D\uDD04 Atualizar Informações",
                "Slider.trackColor",
                "Component.accentColor");
        JButton btnOpenFolder = makeTextBtn("\uD83D\uDCC1 Abrir Pasta do Cache",
                "Slider.trackColor",
                "Component.accentColor");
        JButton btnClearOld = makeTextBtn("\uD83D\uDDD1\uFE0F Limpar Antigos (30 dias)",
                "Slider.trackColor",
                "Component.accentColor");
        JButton btnClearAll = makeTextBtn("❌ Limpar Tudo",
                "Slider.trackColor",
                "Component.accentColor");
        btnClearAll.setForeground(new Color(200, 50, 50));
        btnRefresh.addActionListener(e -> refreshInfo());
        btnOpenFolder.addActionListener(e -> openCacheFolder());
        btnClearOld.addActionListener(e -> clearOldThumbnails());
        btnClearAll.addActionListener(e -> clearAllCache());

        panel.add(btnRefresh);
        panel.add(btnOpenFolder);
        panel.add(btnClearOld);
        panel.add(btnClearAll);

        return panel;
    }

    // -------------------------------------------------------------------------
    // Ações
    // -------------------------------------------------------------------------

    private JButton makeTextBtn(String text, String borderColor, String borderHoverColor) {
        Map<String, Object> estiloBotao = Map.of(
                "borderWidth", 2,
                "borderColor",UIManager.getColor(borderColor), // Cor normal
                "hoverBorderColor", UIManager.getColor(borderHoverColor), // Cor ao passar o mouse
                "focusedBorderColor", UIManager.getColor("Slider.trackColor") // Cor se focado (opcional)
        );
        JButton btn = new JButton(text);
        btn.setFont(UIConfig.FONT_DEFAULT_BOLD);
        btn.putClientProperty(FlatClientProperties.STYLE, estiloBotao);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }


    /** Recarrega contagem, tamanho e localização em background. */
    public void refreshInfo() {
        thumbnailCountLabel.setText("carregando...");
        cacheSizeLabel.setText("carregando...");
        cacheLocationLabel.setText("carregando...");

        new SwingWorker<Void, Void>() {
            long count; String size, location;

            @Override
            protected Void doInBackground() throws Exception {
                count    = cacheManager.getThumbnailCount();
                size     = cacheManager.getCacheSizeFormatted();
                location = cacheManager.getCacheDirectory().toString();
                return null;
            }

            @Override
            protected void done() {
                thumbnailCountLabel.setText(count + " arquivo(s)");
                cacheSizeLabel.setText(size);
                cacheLocationLabel.setText(location);
            }
        }.execute();
    }

    private void openCacheFolder() {
        try {
            Desktop.getDesktop().open(cacheManager.getCacheDirectory().toFile());
        } catch (IOException e) {
            showError("Erro ao abrir pasta: " + e.getMessage());
        } catch (UnsupportedOperationException e) {
            showError("Operação não suportada neste sistema operacional.");
        }
    }

    private void clearOldThumbnails() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "<html><body style='width:280px'>" +
                        "<b>Limpar thumbnails antigos?</b><br><br>" +
                        "Serão removidos thumbnails de vídeos não acessados há mais de 30 dias.<br>" +
                        "Eles serão gerados novamente quando necessário." +
                        "</body></html>",
                "Confirmar Limpeza",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) return;

        new SwingWorker<Integer, Void>() {
            long beforeCount, afterCount;

            @Override
            protected Integer doInBackground() throws Exception {
                beforeCount = cacheManager.getThumbnailCount();
                cacheManager.clearOldThumbnails(30);
                afterCount = cacheManager.getThumbnailCount();
                return (int) (beforeCount - afterCount);
            }

            @Override
            protected void done() {
                try {
                    int removed = get();
                    refreshInfo();
                    if (removed > 0) {
                        JOptionPane.showMessageDialog(CacheConfigPanel.this,
                                String.format("✓ Limpeza concluída!\n\nRemovidos: %d thumbnail(s)\nRestantes: %d thumbnail(s)",
                                        removed, afterCount),
                                "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(CacheConfigPanel.this,
                                "Nenhum thumbnail antigo encontrado.\n" +
                                        "Todos os thumbnails foram acessados recentemente.",
                                "Informação", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception ex) {
                    showError("Erro ao limpar thumbnails antigos: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void clearAllCache() {
        new SwingWorker<Void, Void>() {
            long count; String size;

            @Override
            protected Void doInBackground() throws Exception {
                count = cacheManager.getThumbnailCount();
                size  = cacheManager.getCacheSizeFormatted();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    int confirm = JOptionPane.showConfirmDialog(
                            CacheConfigPanel.this,
                            String.format(
                                    "<html><body style='width:280px'>" +
                                            "<b>⚠️ Limpar TODO o cache?</b><br><br>" +
                                            "Serão removidos: <b>%d</b> thumbnail(s) (<b>%s</b>)<br><br>" +
                                            "<span style='color:red'>Esta ação não pode ser desfeita.</span>" +
                                            "</body></html>", count, size),
                            "Confirmar Limpeza Total",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE
                    );

                    if (confirm != JOptionPane.YES_OPTION) return;

                    new SwingWorker<Void, Void>() {
                        @Override
                        protected Void doInBackground() throws Exception {
                            cacheManager.clearCache();
                            FileItemPanel.ICON_CACHE.clear();
                            return null;
                        }

                        @Override
                        protected void done() {
                            try {
                                get();
                                refreshInfo();
                                JOptionPane.showMessageDialog(CacheConfigPanel.this,
                                        String.format("✓ Cache limpo com sucesso!\n\nRemovidos: %d thumbnail(s)\n" +
                                                "Cache em memória também foi limpo.", count),
                                        "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                            } catch (Exception ex) {
                                showError("Erro ao limpar cache: " + ex.getMessage());
                            }
                        }
                    }.execute();

                } catch (Exception ex) {
                    showError("Erro ao obter informações do cache: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Erro", JOptionPane.ERROR_MESSAGE);
    }
}