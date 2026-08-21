package com.esl.searchforfiles.Theme;


import com.esl.searchforfiles.configuration.ConfigPanelBase;
import com.esl.searchforfiles.configuration.UIConfig;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

// ═══════════════════════════════════════════════════════════════
// ThemeConfigPanel — aba de aparência, agora usando ConfigPanelBase
// ═══════════════════════════════════════════════════════════════
public class ThemeConfigPanel extends ConfigPanelBase {

    public ThemeConfigPanel(ThemeManager themeManager, JFrame mainFrame) {

        JPanel section = addSection("Tema (skin)");

        ButtonGroup group = new ButtonGroup();
        String current = themeManager.getCurrentTheme();

        for (Map.Entry<String, ThemeManager.ThemeInfo> entry
                : themeManager.getAvailableThemes().entrySet()) {

            String key  = entry.getKey();
            ThemeManager.ThemeInfo info = entry.getValue();

            JRadioButton radio = new JRadioButton(info.getDisplayName(), key.equals(current));
            radio.setFont(UIConfig.FONT_DEFAULT);
            radio.addActionListener(e -> {
                if (!key.equals(themeManager.getCurrentTheme()))
                    themeManager.changeThemeSilent(key, mainFrame);
            });

            group.add(radio);
            addRow(section, radio);
        }

        addRow(section, makeHint(
                "O tema é salvo automaticamente e aplicado " +
                        "na próxima inicialização."));
    }
}