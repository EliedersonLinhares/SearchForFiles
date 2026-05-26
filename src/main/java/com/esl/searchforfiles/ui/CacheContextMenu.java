package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.cache.thumbnail.ThumbnailCacheManager;
import com.esl.searchforfiles.configuration.UIConfig;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;

/**
 * Menu de contexto para gerenciar cache de thumbnails de vídeos
 */
public class CacheContextMenu extends JPopupMenu {

    private final Component parent;
    private final ThumbnailCacheManager cacheManager;

    public CacheContextMenu(Component parent, ThumbnailCacheManager cacheManager) {
        this.parent = parent;
        this.cacheManager = cacheManager;

        createMenuItems();
    }

    private void createMenuItems() {
        // ===== SEÇÃO: INFORMAÇÕES =====
        JMenuItem infoItem = new JMenuItem("📊 Informações do Cache");
        infoItem.setFont(UIConfig.FONT_SMALL);
        infoItem.addActionListener(e -> showCacheInfo());
        add(infoItem);

        addSeparator();

        // ===== SEÇÃO: GERENCIAMENTO =====
        JMenuItem manageItem = new JMenuItem("⚙️ Gerenciar Cache");
        manageItem.addActionListener(e -> openCacheManager());
        add(manageItem);

        JMenuItem openFolderItem = new JMenuItem("📁 Abrir Pasta do Cache");
        openFolderItem.addActionListener(e -> openCacheFolder());
        add(openFolderItem);

        addSeparator();

        // ===== SEÇÃO: LIMPEZA =====
        JMenuItem refreshItem = new JMenuItem("🔄 Atualizar Informações");
        refreshItem.addActionListener(e -> refreshCacheInfo());
        add(refreshItem);

        JMenuItem clearOldItem = new JMenuItem("🗑️ Limpar Thumbnails Antigos (30 dias)");
        clearOldItem.addActionListener(e -> clearOldThumbnails());
        add(clearOldItem);

        JMenuItem clearAllItem = new JMenuItem("❌ Limpar Todo o Cache");
        clearAllItem.setForeground(new Color(200, 50, 50));
        clearAllItem.addActionListener(e -> clearAllCache());
        add(clearAllItem);
    }

    /**
     * Mostra informações detalhadas do cache
     */
    private void showCacheInfo() {
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                long count = cacheManager.getThumbnailCount();
                String size = cacheManager.getCacheSizeFormatted();
                String location = cacheManager.getCacheDirectory().toString();

                return String.format(
                        "<html><body style='width: 350px; font-family: SansSerif;'>" +
                                "<h3 style='margin-top: 0;'>📊 Informações do Cache de Thumbnails</h3>" +
                                "<hr>" +
                                "<table cellpadding='5'>" +
                                "<tr><td><b>Localização:</b></td><td>%s</td></tr>" +
                                "<tr><td><b>Thumbnails salvos:</b></td><td>%d arquivo(s)</td></tr>" +
                                "<tr><td><b>Tamanho total:</b></td><td>%s</td></tr>" +
                                "</table>" +
                                "<hr>" +
                                "<p style='font-size: 10px; color: gray;'>" +
                                "Os thumbnails são salvos automaticamente para carregar mais rápido<br>" +
                                "na próxima vez que você visualizar os mesmos vídeos." +
                                "</p>" +
                                "</body></html>",
                        location, count, size
                );
            }

            @Override
            protected void done() {
                try {
                    String info = get();
                    JOptionPane.showMessageDialog(
                            parent,
                            info,
                            "Informações do Cache",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                } catch (Exception e) {
                    showError("Erro ao obter informações do cache: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    /**
     * Abre o gerenciador de cache completo
     */
    private void openCacheManager() {
        Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(parent);
        CacheManagerDialog.showDialog(parentFrame, cacheManager);
    }

    /**
     * Abre a pasta do cache no explorador de arquivos
     */
    private void openCacheFolder() {
        try {
            Desktop.getDesktop().open(cacheManager.getCacheDirectory().toFile());
        } catch (IOException e) {
            showError("Erro ao abrir pasta: " + e.getMessage());
        } catch (UnsupportedOperationException e) {
            showError("Operação não suportada neste sistema operacional.");
        }
    }

    /**
     * Atualiza e mostra informações rápidas do cache
     */
    private void refreshCacheInfo() {
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                long count = cacheManager.getThumbnailCount();
                String size = cacheManager.getCacheSizeFormatted();
                return String.format("%d thumbnails • %s", count, size);
            }

            @Override
            protected void done() {
                try {
                    String info = get();
                    JOptionPane.showMessageDialog(
                            parent,
                            "Cache atualizado!\n\n" + info,
                            "Cache Atualizado",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                } catch (Exception e) {
                    showError("Erro ao atualizar informações: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    /**
     * Limpa thumbnails mais antigos que 30 dias
     */
    private void clearOldThumbnails() {
        int result = JOptionPane.showConfirmDialog(
                parent,
                "<html><body style='width: 300px;'>" +
                        "<p><b>Limpar thumbnails antigos?</b></p>" +
                        "<p>Serão removidos thumbnails de vídeos que não são " +
                        "acessados há mais de 30 dias.</p>" +
                        "<p>Os thumbnails serão gerados novamente quando necessário.</p>" +
                        "</body></html>",
                "Confirmar Limpeza",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (result == JOptionPane.YES_OPTION) {
            SwingWorker<Integer, Void> worker = new SwingWorker<>() {
                long beforeCount;
                long afterCount;

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
                        if (removed > 0) {
                            JOptionPane.showMessageDialog(
                                    parent,
                                    String.format(
                                            "✓ Limpeza concluída!\n\n" +
                                                    "Removidos: %d thumbnail(s) antigo(s)\n" +
                                                    "Restantes: %d thumbnail(s)",
                                            removed, afterCount
                                    ),
                                    "Sucesso",
                                    JOptionPane.INFORMATION_MESSAGE
                            );
                        } else {
                            JOptionPane.showMessageDialog(
                                    parent,
                                    "Nenhum thumbnail antigo encontrado.\n" +
                                            "Todos os thumbnails foram acessados recentemente.",
                                    "Informação",
                                    JOptionPane.INFORMATION_MESSAGE
                            );
                        }
                    } catch (Exception e) {
                        showError("Erro ao limpar thumbnails antigos: " + e.getMessage());
                    }
                }
            };

            worker.execute();
        }
    }

    /**
     * Limpa todo o cache de thumbnails
     */
    private void clearAllCache() {
        SwingWorker<Long, Void> worker = new SwingWorker<>() {
            @Override
            protected Long doInBackground() throws Exception {
                return cacheManager.getThumbnailCount();
            }

            @Override
            protected void done() {
                try {
                    long count = get();
                    String size = cacheManager.getCacheSizeFormatted();

                    int result = JOptionPane.showConfirmDialog(
                            parent,
                            String.format(
                                    "<html><body style='width: 300px;'>" +
                                            "<p><b>⚠️ Limpar TODO o cache?</b></p>" +
                                            "<p>Serão removidos:</p>" +
                                            "<ul>" +
                                            "<li><b>%d</b> thumbnail(s)</li>" +
                                            "<li><b>%s</b> de espaço em disco</li>" +
                                            "</ul>" +
                                            "<p style='color: red;'>Todos os thumbnails serão gerados " +
                                            "novamente quando necessário.</p>" +
                                            "</body></html>",
                                    count, size
                            ),
                            "Confirmar Limpeza Total",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE
                    );

                    if (result == JOptionPane.YES_OPTION) {
                        performClearAll(count);
                    }
                } catch (Exception e) {
                    showError("Erro ao obter informações do cache: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    /**
     * Executa a limpeza total do cache
     */
    private void performClearAll(long beforeCount) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                cacheManager.clearCache();
                // Limpa também o cache em memória
                FileItemPanel.ICON_CACHE.clear();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(
                            parent,
                            String.format(
                                    "✓ Cache limpo com sucesso!\n\n" +
                                            "Removidos: %d thumbnail(s)\n" +
                                            "Cache em memória também foi limpo.",
                                    beforeCount
                            ),
                            "Sucesso",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                } catch (Exception e) {
                    showError("Erro ao limpar cache: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    /**
     * Mostra mensagem de erro
     */
    private void showError(String message) {
        JOptionPane.showMessageDialog(
                parent,
                message,
                "Erro",
                JOptionPane.ERROR_MESSAGE
        );
    }

    /**
     * Método estático para mostrar o menu facilmente
     */
    public static void show(Component parent, ThumbnailCacheManager cacheManager, int x, int y) {
        CacheContextMenu menu = new CacheContextMenu(parent, cacheManager);
        menu.show(parent, x, y);
    }
}