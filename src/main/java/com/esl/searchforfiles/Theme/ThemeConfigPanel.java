package com.esl.searchforfiles.Theme;


import com.esl.searchforfiles.configuration.UIConfig;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class ThemeConfigPanel extends JPanel {

    private final ThemeManager themeManager;
    private final JFrame mainFrame;

    public ThemeConfigPanel(ThemeManager themeManager, JFrame mainFrame) {
        this.themeManager = themeManager;
        this.mainFrame = mainFrame;


        setLayout(new GridBagLayout()); // centraliza na aba

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.setPreferredSize(new Dimension(480, 220));
        content.add(createThemePanel(), BorderLayout.CENTER);

        add(content);
    }

    private JPanel createThemePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Aparência (Skin)"));

        String current = themeManager.getCurrentTheme();
        ButtonGroup buttonGroup = new ButtonGroup();

        for (Map.Entry<String, ThemeManager.ThemeInfo> entry : themeManager.getAvailableThemes().entrySet()) {
            String key = entry.getKey();
            ThemeManager.ThemeInfo info = entry.getValue();

            JRadioButton radio = new JRadioButton(info.getDisplayName(), key.equals(current));
            radio.setAlignmentX(Component.LEFT_ALIGNMENT);
            radio.addActionListener(e -> {
                if (!key.equals(themeManager.getCurrentTheme())) {
                    themeManager.changeThemeSilent(key, mainFrame);
                }
            });

            buttonGroup.add(radio);
            panel.add(radio);
            panel.add(Box.createVerticalStrut(4));
        }

        panel.add(Box.createVerticalStrut(8));

        JLabel hint = new JLabel("<html><i>O tema é salvo automaticamente e aplicado na próxima inicialização.</i></html>");
        hint.setForeground(Color.GRAY);
        hint.setFont(UIConfig.FONT_SMALL);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(hint);

        return panel;
    }
}