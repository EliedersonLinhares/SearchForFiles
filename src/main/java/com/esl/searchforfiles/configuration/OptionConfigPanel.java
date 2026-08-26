package com.esl.searchforfiles.configuration;

import com.esl.searchforfiles.ui.FileExplorerSwing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class OptionConfigPanel extends ConfigPanelBase {
    private final FileExplorerSwing mainFrame;

    public OptionConfigPanel(FileExplorerSwing mainFrame) {
        this.mainFrame = mainFrame;
        buildIdentitySection();
    }

    private void buildIdentitySection() {
        JPanel section = addSection("Opções");


        JCheckBox imageChBox = new JCheckBox("Usar aplicativo default do Windows para Imagens");
        imageChBox.setFont(UIConfig.FONT_DEFAULT);
        // CORREÇÃO: Define o estado inicial com base no valor atual do mainFrame
        imageChBox.setSelected(mainFrame.isUseDefaultWindowsProgramImage());

        JCheckBox videoChBox = new JCheckBox("Usar aplicativo default do Windows para Videos");
        videoChBox.setFont(UIConfig.FONT_DEFAULT);
        // CORREÇÃO: Define o estado inicial com base no valor atual do mainFrame
        videoChBox.setSelected(mainFrame.isUseDefaultWindowsProgramVideo());


        // Lado direito: nome + descrição empilhados


        JPanel textBlock = new JPanel();
        textBlock.setLayout(new BoxLayout(textBlock, BoxLayout.Y_AXIS));
        textBlock.setOpaque(false);
        textBlock.add(imageChBox);
        textBlock.add(videoChBox);
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
    }
}
