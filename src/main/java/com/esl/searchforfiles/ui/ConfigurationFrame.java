package com.esl.searchforfiles.ui;


import com.esl.searchforfiles.Theme.ThemeManager;
import com.esl.searchforfiles.cache.thumbnail.ThumbnailCacheManager;
import com.esl.searchforfiles.configuration.*;
import com.esl.searchforfiles.database.DatabaseConfigPanel;
import com.esl.searchforfiles.localization.I18N;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

// ═══════════════════════════════════════════════════════════════
// ConfigurationFrame — container principal
// ═══════════════════════════════════════════════════════════════
public class ConfigurationFrame extends JFrame implements I18N.LanguageChangeListener {

    private final JTabbedPane tabbedPane;

    // índice da aba -> chave i18n (ordem deve bater com a ordem de addTab)
    private final Map<Integer, String> tabKeys = new LinkedHashMap<>();

    public ConfigurationFrame(Window owner, ResultsPanel resultsPanel,
                              ThemeManager themeManager, FileExplorerSwing mainFrame) {
        super(I18N.get("configurationFrame.title")); // se o título da janela também precisar traduzir

        if (owner != null) owner.setEnabled(false);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(720, 420);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());
        setResizable(false);
        setIconImages(UIConfig.IconsConfig(this));

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (owner != null) owner.setEnabled(true);
                owner.toFront();
            }
        });

        tabbedPane = new JTabbedPane();
        tabbedPane.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        ThumbnailCacheManager cacheManager = new ThumbnailCacheManager();

        addTab("configurationFrame.tab.database",
                new DatabaseConfigPanel(resultsPanel.getFileExplorerSwing().getController().getDbManager(), mainFrame));
        addTab("configurationFrame.tab.cache",
                new CacheConfigPanel((Frame) owner, cacheManager));
        addTab("configurationFrame.tab.options",
                new OptionConfigPanel(themeManager, mainFrame));
        addTab("configurationFrame.tab.about",
                new AboutConfigPanel());

        add(tabbedPane, BorderLayout.CENTER);
        setVisible(true);

        updateTexts();
        I18N.addLanguageChangeListener(this);
    }

    /** Adiciona a aba já registrando a chave i18n do título para futura atualização. */
    private void addTab(String i18nKey, JComponent panel) {
        int index = tabbedPane.getTabCount();
        tabbedPane.addTab(I18N.get(i18nKey), panel);
        tabKeys.put(index, i18nKey);
    }

    /** Agora seleciona por CHAVE i18n, não pelo texto exibido. */
    public void selectTabByKey(String i18nKey) {
        for (Map.Entry<Integer, String> entry : tabKeys.entrySet()) {
            if (entry.getValue().equals(i18nKey)) {
                tabbedPane.setSelectedIndex(entry.getKey());
                return;
            }
        }
    }

    protected void updateTexts() {
        setTitle(I18N.get("configurationFrame.title"));

        for (Map.Entry<Integer, String> entry : tabKeys.entrySet()) {
            tabbedPane.setTitleAt(entry.getKey(), I18N.get(entry.getValue()));
        }
    }

    @Override
    public void onLanguageChanged(Locale newLocale) {
        updateTexts();
        repaint();
        revalidate();
    }
}
