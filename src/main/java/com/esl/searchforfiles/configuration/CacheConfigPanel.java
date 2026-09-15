package com.esl.searchforfiles.configuration;


import com.esl.searchforfiles.cache.thumbnail.ThumbnailCacheManager;
import com.esl.searchforfiles.localization.I18N;
import com.esl.searchforfiles.ui.FileItemPanel;
import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

// ═══════════════════════════════════════════════════════════════
// CacheConfigPanel — aba de cache, agora usando ConfigPanelBase
// ═══════════════════════════════════════════════════════════════
public class CacheConfigPanel extends ConfigPanelBase implements I18N.LanguageChangeListener {

    private final Frame parentFrame;
    private final ThumbnailCacheManager cacheManager;

    private final JLabel locationLabel = makeValueLabel("...");
    private final JLabel countLabel    = makeValueLabel("...");
    private final JLabel sizeLabel     = makeValueLabel("...");
    private JLabel countLabelComplement;


    private TitledBorder infoSectionBorder;
    private JLabel rowLocation;
    private JLabel rowCount;
    private JLabel rowSize;
    private JLabel rowhint;
    private JLabel hintLabel;
    private JLabel hintLabel1;
    private JLabel hintLabel2;
    private JLabel hintLabel3;
    private  JButton btnRefresh;
    private JButton btnOpenFolder;
    private  JButton btnClearOld;
    private JButton btnClearAll;

    public CacheConfigPanel(Frame parentFrame, ThumbnailCacheManager cacheManager) {
        this.parentFrame  = parentFrame;
        this.cacheManager = cacheManager;

        // ── Seção: informações ────────────────────────────────────
        JPanel info = addSection(I18N.get("cacheConfigPanel.JPanel.info"));
        infoSectionBorder = (TitledBorder) info.getBorder();

        rowLocation = addRowLabel(info, I18N.get("cacheConfigPanel.rowLocation"),locationLabel);
        rowCount =addRowLabel(info, I18N.get("cacheConfigPanel.rowCount"), countLabel);
        rowSize = addRowLabel(info, I18N.get("cacheConfigPanel.rowSize"), sizeLabel);




        // ── Botões ────────────────────────────────────────────────
        btnRefresh    = makeBtn(I18N.get("cacheConfigPanel.btnRefresh"));
        btnOpenFolder = makeBtn(I18N.get("cacheConfigPanel.btnOpenFolder"));
        btnClearOld   = makeBtn(I18N.get("cacheConfigPanel.btnClearOld"));
        btnClearAll   = makeDangerBtn(I18N.get("cacheConfigPanel.btnClearAll"));

        btnRefresh   .addActionListener(e -> refreshInfo());
        btnOpenFolder.addActionListener(e -> openCacheFolder());
        btnClearOld  .addActionListener(e -> clearOldThumbnails());
        btnClearAll  .addActionListener(e -> clearAllCache());

        addButtons(btnRefresh, btnOpenFolder, btnClearOld, btnClearAll);

        updateTexts();
        I18N.addLanguageChangeListener(this);

        refreshInfo();
    }

    // ── Ações (sem alteração de lógica) ─────────────────────────

    public void refreshInfo() {
//        locationLabel.setText("carregando...");
//        countLabel   .setText("carregando...");
//        sizeLabel    .setText("carregando...");

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
                countLabelComplement = new JLabel(I18N.get("cacheConfigPanel.countLabelComplement"));
                countLabel   .setText(count + " " + countLabelComplement.getText());
                sizeLabel    .setText(size);
            }
        }.execute();
    }

    private void openCacheFolder() {
        try {
            Desktop.getDesktop().open(cacheManager.getCacheDirectory().toFile());
        } catch (Exception e) {
            showError( I18N.get("cacheConfigPanel.openCacheFolder.error.msg") + " " + e.getMessage());
        }
    }

    private void clearOldThumbnails() {
        int ok = JOptionPane.showConfirmDialog(this,
                I18N.get("cacheConfigPanel.clearOldThumbnails.showConfirmDialog.msg"),
                I18N.get("cacheConfigPanel.clearOldThumbnails.showConfirmDialog.title"), JOptionPane.YES_NO_OPTION);
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
                            ? removed + " " + I18N.get("cacheConfigPanel.removed.showConfirmDialog.msg1")
                            : I18N.get("cacheConfigPanel.removed.showConfirmDialog.msg2");
                    JOptionPane.showMessageDialog(CacheConfigPanel.this, msg);
                } catch (Exception ex) { showError(ex.getMessage()); }
            }
        }.execute();
    }

    private void clearAllCache() {
        int ok = JOptionPane.showConfirmDialog(this,
                I18N.get("cacheConfigPanel.clearAllCache.showConfirmDialog.msg"),
                I18N.get("cacheConfigPanel.clearAllCache.showConfirmDialog.title"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                cacheManager.clearCache();
                FileItemPanel.ICON_CACHE.clear();
                return null;
            }
            @Override protected void done() {
                refreshInfo();
                JOptionPane.showMessageDialog(CacheConfigPanel.this, I18N.get("cacheConfigPanel.clearAllCache.showConfirmDialog.done"));
            }
        }.execute();
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, I18N.get("cacheConfigPanel.clearAllCache.showConfirmDialog.done.error"), JOptionPane.ERROR_MESSAGE);
    }

    protected void updateTexts() {
        refreshInfo();

        if (infoSectionBorder != null)  infoSectionBorder.setTitle(I18N.get("cacheConfigPanel.JPanel.info"));

        if (rowLocation != null)  rowLocation.setText(I18N.get("cacheConfigPanel.rowLocation"));
        if (rowCount != null)  rowCount.setText(I18N.get("cacheConfigPanel.rowCount"));
        if (rowSize != null)  rowSize.setText(I18N.get("cacheConfigPanel.rowSize"));
        //if (rowhint != null)  rowhint.setText(I18N.get("aboutConfigPanel.nameLabel"));



        if (btnRefresh != null)  btnRefresh.setText(I18N.get("cacheConfigPanel.btnRefresh"));
        if (btnOpenFolder != null)  btnOpenFolder.setText(I18N.get("cacheConfigPanel.btnOpenFolder"));
        if (btnClearOld != null)  btnClearOld.setText(I18N.get("cacheConfigPanel.btnClearOld"));
        if (btnClearAll != null)  btnClearAll.setText(I18N.get("cacheConfigPanel.btnClearAll"));
        if (countLabelComplement != null)  countLabelComplement.setText(I18N.get("cacheConfigPanel.countLabelComplement"));

;

        /**
         *   private JLabel rowLocation;
         *     private JLabel rowCount;
         *     private JLabel rowSize;
         *     private JLabel rowhint;
         *     private JLabel hintLabel;
         *     private  JButton btnRefresh;
         *     private JButton btnOpenFolder;
         *     private  JButton btnClearOld;
         *     private JButton btnClearAll;
         */

    }

    @Override
    public void onLanguageChanged(Locale newLocale) {
        updateTexts();
        repaint();
        revalidate();
    }
}