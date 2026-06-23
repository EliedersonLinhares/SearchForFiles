package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.Theme.ThemeConfigPanel;
import com.esl.searchforfiles.Theme.ThemeManager;
import com.esl.searchforfiles.cache.thumbnail.ThumbnailCacheManager;
import com.esl.searchforfiles.configuration.CacheConfigPanel;

import javax.swing.*;
import java.awt.*;


public class ConfigurationFrame extends JFrame {

    private final ResultsPanel resultsPanel;
    private final JTabbedPane tabbedPane;

    public ConfigurationFrame(Window owner, ResultsPanel resultsPanel, ThemeManager themeManager, JFrame mainFrame) {
        super("Configurações");
        this.resultsPanel = resultsPanel;

        if (owner != null) owner.setEnabled(false);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1000, 600);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (owner != null) owner.setEnabled(true);
                owner.toFront();
            }
        });

        tabbedPane = new JTabbedPane();

        ThumbnailCacheManager cacheManager = new ThumbnailCacheManager();// ajuste conforme sua instância
        tabbedPane.addTab("Cache", new CacheConfigPanel((Frame) owner, cacheManager));
        tabbedPane.addTab("Aparência", new ThemeConfigPanel(themeManager, mainFrame));

        this.add(tabbedPane, BorderLayout.CENTER);
        setVisible(true);
    }

    /** Seleciona uma aba pelo título. Útil para abrir o frame já em uma aba específica. */
    public void selectTab(String title) {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getTitleAt(i).equals(title)) {
                tabbedPane.setSelectedIndex(i);
                return;
            }
        }
    }
}
