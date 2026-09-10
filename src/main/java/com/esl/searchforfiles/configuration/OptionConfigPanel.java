package com.esl.searchforfiles.configuration;

import com.esl.searchforfiles.Theme.ThemeManager;
import com.esl.searchforfiles.ui.FileExplorerSwing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.Map;

public class OptionConfigPanel extends ConfigPanelBase {
    private final FileExplorerSwing mainFrame;
    private final ThemeManager themeManager;

    public OptionConfigPanel(ThemeManager themeManager,FileExplorerSwing mainFrame) {
        this.mainFrame = mainFrame;
        this.themeManager = themeManager;
        buildIdentitySection();
        themeSection();
    }

    private void buildIdentitySection() {
        JPanel section = addSection("Opções");

        JCheckBox imageChBox = new JCheckBox("Usar aplicativo default do Windows para Imagens");
        imageChBox.setFont(UIConfig.FONT_DEFAULT);
        imageChBox.setSelected(mainFrame.isUseDefaultWindowsProgramImage());

        JCheckBox videoChBox = new JCheckBox("Usar aplicativo default do Windows para Videos");
        videoChBox.setFont(UIConfig.FONT_DEFAULT);
        videoChBox.setSelected(mainFrame.isUseDefaultWindowsProgramVideo());

        JCheckBox pdfChBox = new JCheckBox("Usar aplicativo default do Windows para arquivos PDF");
        pdfChBox.setFont(UIConfig.FONT_DEFAULT);
        pdfChBox.setSelected(mainFrame.isUseDefaultWindowsProgramPdf());

        JCheckBox model3dChBox = new JCheckBox("Usar aplicativo default do Windows para arquivos 3D .obj");
        model3dChBox.setFont(UIConfig.FONT_DEFAULT);
        model3dChBox.setSelected(mainFrame.isUseDefaultWindowsProgram3dModel());

        // Lado direito: nome + descrição empilhados


        JPanel textBlock = new JPanel();
        textBlock.setLayout(new BoxLayout(textBlock, BoxLayout.Y_AXIS));
        textBlock.setOpaque(false);
        textBlock.add(imageChBox);
        textBlock.add(videoChBox);
        textBlock.add(pdfChBox);
        textBlock.add(model3dChBox);
        textBlock.add(Box.createVerticalStrut(6));

        // Row: logo | textBlock
        JPanel row = new JPanel(new BorderLayout(0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        row.add(textBlock,  BorderLayout.CENTER);

        // Adiciona direto na seção (sem label fixo à esquerda)
        section.add(row);
        section.add(Box.createVerticalStrut(4));


        imageChBox.addItemListener(e -> {
            mainFrame.setUseDefaultWindowsProgramImage(e.getStateChange() == ItemEvent.SELECTED);
            System.out.println("Valor do boolean: " + mainFrame.isUseDefaultWindowsProgramImage());
        });
        videoChBox.addItemListener(e -> {
            mainFrame.setUseDefaultWindowsProgramVideo(e.getStateChange() == ItemEvent.SELECTED);
            System.out.println("Valor do boolean: " + mainFrame.isUseDefaultWindowsProgramVideo());
        });
        pdfChBox.addItemListener(e -> {
            mainFrame.setUseDefaultWindowsProgramPdf(e.getStateChange() == ItemEvent.SELECTED);
            System.out.println("Valor do boolean: " + mainFrame.isUseDefaultWindowsProgramPdf());
        });
        model3dChBox.addItemListener(e -> {
            mainFrame.setUseDefaultWindowsProgram3dModel(e.getStateChange() == ItemEvent.SELECTED);
            System.out.println("Valor do boolean: " + mainFrame.isUseDefaultWindowsProgram3dModel());
        });
    }

    private void themeSection() {
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
