package com.esl.searchforfiles.configuration;


import com.esl.searchforfiles.cache.thumbnail.ThumbnailCacheManager;
import com.esl.searchforfiles.ui.FileItemPanel;
import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Map;

// ═══════════════════════════════════════════════════════════════
// CacheConfigPanel — aba de cache, agora usando ConfigPanelBase
// ═══════════════════════════════════════════════════════════════
public class CacheConfigPanel extends ConfigPanelBase {

    private final Frame parentFrame;
    private final ThumbnailCacheManager cacheManager;

    private final JLabel locationLabel = makeValueLabel("...");
    private final JLabel countLabel    = makeValueLabel("...");
    private final JLabel sizeLabel     = makeValueLabel("...");

    public CacheConfigPanel(Frame parentFrame, ThumbnailCacheManager cacheManager) {
        this.parentFrame  = parentFrame;
        this.cacheManager = cacheManager;

        // ── Seção: informações ────────────────────────────────────
        JPanel info = addSection("Informações do cache");
        addRow(info, "Localização",       locationLabel);
        addRow(info, "Thumbnails salvos", countLabel);
        addRow(info, "Tamanho total",     sizeLabel);
        addRow(info, makeHint(
                "Os thumbnails são salvos automaticamente para " +
                        "carregar mais rápido na próxima vez que você " +
                        "visualizar os mesmos vídeos."));

        // ── Botões ────────────────────────────────────────────────
        JButton btnRefresh    = makeBtn("Atualizar");
        JButton btnOpenFolder = makeBtn("Abrir pasta");
        JButton btnClearOld   = makeBtn("Limpar antigos (30 dias)");
        JButton btnClearAll   = makeDangerBtn("Limpar tudo");

        btnRefresh   .addActionListener(e -> refreshInfo());
        btnOpenFolder.addActionListener(e -> openCacheFolder());
        btnClearOld  .addActionListener(e -> clearOldThumbnails());
        btnClearAll  .addActionListener(e -> clearAllCache());

        addButtons(btnRefresh, btnOpenFolder, btnClearOld, btnClearAll);

        refreshInfo();
    }

    // ── Ações (sem alteração de lógica) ─────────────────────────

    public void refreshInfo() {
        locationLabel.setText("carregando...");
        countLabel   .setText("carregando...");
        sizeLabel    .setText("carregando...");

        new SwingWorker<Void, Void>() {
            long count; String size, location;

            @Override protected Void doInBackground() throws Exception {
                count    = cacheManager.getThumbnailCount();
                size     = cacheManager.getCacheSizeFormatted();
                location = cacheManager.getCacheDirectory().toString();
                return null;
            }

            @Override protected void done() {
                locationLabel.setText(location);
                countLabel   .setText(count + " arquivo(s)");
                sizeLabel    .setText(size);
            }
        }.execute();
    }

    private void openCacheFolder() {
        try {
            Desktop.getDesktop().open(cacheManager.getCacheDirectory().toFile());
        } catch (Exception e) {
            showError("Erro ao abrir pasta: " + e.getMessage());
        }
    }

    private void clearOldThumbnails() {
        int ok = JOptionPane.showConfirmDialog(this,
                "Remover thumbnails não acessados há mais de 30 dias?",
                "Confirmar", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;

        new SwingWorker<Integer, Void>() {
            long before, after;
            @Override protected Integer doInBackground() throws Exception {
                before = cacheManager.getThumbnailCount();
                cacheManager.clearOldThumbnails(30);
                after = cacheManager.getThumbnailCount();
                return (int)(before - after);
            }
            @Override protected void done() {
                try {
                    int removed = get();
                    refreshInfo();
                    String msg = removed > 0
                            ? removed + " thumbnail(s) removido(s)."
                            : "Nenhum thumbnail antigo encontrado.";
                    JOptionPane.showMessageDialog(CacheConfigPanel.this, msg);
                } catch (Exception ex) { showError(ex.getMessage()); }
            }
        }.execute();
    }

    private void clearAllCache() {
        int ok = JOptionPane.showConfirmDialog(this,
                "Limpar todo o cache de thumbnails? Esta ação não pode ser desfeita.",
                "Confirmar", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                cacheManager.clearCache();
                FileItemPanel.ICON_CACHE.clear();
                return null;
            }
            @Override protected void done() {
                refreshInfo();
                JOptionPane.showMessageDialog(CacheConfigPanel.this, "Cache limpo.");
            }
        }.execute();
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Erro", JOptionPane.ERROR_MESSAGE);
    }
}