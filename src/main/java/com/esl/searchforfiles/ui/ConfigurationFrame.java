package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.Theme.ThemeConfigPanel;
import com.esl.searchforfiles.Theme.ThemeManager;
import com.esl.searchforfiles.cache.thumbnail.ThumbnailCacheManager;
import com.esl.searchforfiles.configuration.*;
import com.esl.searchforfiles.database.DatabaseConfigPanel;

import javax.swing.*;
import java.awt.*;

// ═══════════════════════════════════════════════════════════════
// ConfigurationFrame — container principal
// ═══════════════════════════════════════════════════════════════
public class ConfigurationFrame extends JFrame {

    private final JTabbedPane tabbedPane;

    public ConfigurationFrame(Window owner, ResultsPanel resultsPanel,
                              ThemeManager themeManager, FileExplorerSwing mainFrame) {
        super("Configurações");

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
        tabbedPane.addTab("Banco de dados",
                new DatabaseConfigPanel(resultsPanel.getFileExplorerSwing().getController().getDbManager()));
        tabbedPane.addTab("Cache",     new CacheConfigPanel((Frame) owner, cacheManager));
        tabbedPane.addTab("Opções",     new OptionConfigPanel(mainFrame));
        tabbedPane.addTab("Aparência", new ThemeConfigPanel(themeManager, mainFrame));
        tabbedPane.addTab("Bibliotecas", new LibrariesConfigPanel());
        tabbedPane.addTab("Sobre", new AboutConfigPanel());

        add(tabbedPane, BorderLayout.CENTER);
        setVisible(true);
    }

    public void selectTab(String title) {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getTitleAt(i).equals(title)) {
                tabbedPane.setSelectedIndex(i);
                return;
            }
        }
    }
}
