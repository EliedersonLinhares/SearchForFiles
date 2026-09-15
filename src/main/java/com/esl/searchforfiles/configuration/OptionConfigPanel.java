package com.esl.searchforfiles.configuration;

import com.esl.searchforfiles.Theme.ThemeManager;
import com.esl.searchforfiles.localization.I18N;
import com.esl.searchforfiles.ui.FileExplorerSwing;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.Locale;
import java.util.Map;

public class OptionConfigPanel extends ConfigPanelBase implements I18N.LanguageChangeListener  {
    private final FileExplorerSwing mainFrame;
    private final ThemeManager themeManager;
    private JRadioButton langEnItem;
    private JRadioButton langPtItem;
    private JCheckBox imageChBox;
    private JCheckBox videoChBox;
    private JCheckBox pdfChBox;
    private JCheckBox model3dChBox;

    private TitledBorder languageSectionBorder;
    private TitledBorder themeSectionBorder;
    private TitledBorder identitySectionBorder;


    public OptionConfigPanel(ThemeManager themeManager, FileExplorerSwing mainFrame) {
        this.mainFrame = mainFrame;
        this.themeManager = themeManager;
        buildIdentitySection();
        themeSection();
        buildLanguageSection();

        updateTexts();
        I18N.addLanguageChangeListener(this);
    }

    private void buildIdentitySection() {
        JPanel sectionIdentity = addSection(I18N.get("optionConfigPanel.sectionIdentity.addSection"));
        identitySectionBorder = (TitledBorder) sectionIdentity.getBorder();

        imageChBox = new JCheckBox(I18N.get("optionConfigPanel.JCheckBox.imageChBox"));
        imageChBox.setFont(UIConfig.FONT_DEFAULT);
        imageChBox.setSelected(mainFrame.isUseDefaultWindowsProgramImage());

        videoChBox = new JCheckBox(I18N.get("optionConfigPanel.JCheckBox.videoChBox"));
        videoChBox.setFont(UIConfig.FONT_DEFAULT);
        videoChBox.setSelected(mainFrame.isUseDefaultWindowsProgramVideo());

        pdfChBox = new JCheckBox(I18N.get("optionConfigPanel.JCheckBox.pdfChBox"));
        pdfChBox.setFont(UIConfig.FONT_DEFAULT);
        pdfChBox.setSelected(mainFrame.isUseDefaultWindowsProgramPdf());

        model3dChBox = new JCheckBox(I18N.get("optionConfigPanel.JCheckBox.model3dChBox"));
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
        row.add(textBlock, BorderLayout.CENTER);

        // Adiciona direto na seção (sem label fixo à esquerda)
        sectionIdentity.add(row);
        sectionIdentity.add(Box.createVerticalStrut(4));


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
        JPanel sectionTheme = addSection(I18N.get("optionConfigPanel.sectionTheme.addSection"));
        themeSectionBorder = (TitledBorder) sectionTheme.getBorder();

        ButtonGroup group = new ButtonGroup();
        String current = themeManager.getCurrentTheme();

        for (Map.Entry<String, ThemeManager.ThemeInfo> entry
                : themeManager.getAvailableThemes().entrySet()) {

            String key = entry.getKey();
            ThemeManager.ThemeInfo info = entry.getValue();

            JRadioButton radio = new JRadioButton(info.getDisplayName(), key.equals(current));
            radio.setFont(UIConfig.FONT_DEFAULT);
            radio.addActionListener(e -> {
                if (!key.equals(themeManager.getCurrentTheme()))
                    themeManager.changeThemeSilent(key, mainFrame);
            });

            group.add(radio);
            addRow(sectionTheme, radio);
        }

    }


    protected void buildLanguageSection() {
        JPanel sectionLanguage = addSection(I18N.get("optionConfigPanel.sectionLanguageName"));
        languageSectionBorder = (TitledBorder) sectionLanguage.getBorder();

        Locale cur = I18N.getCurrentLocale();
        ButtonGroup grp = new ButtonGroup();

        langEnItem = new JRadioButton();
        langEnItem.setFont(UIConfig.FONT_DEFAULT);
        langEnItem.setSelected("en".equals(cur.getLanguage()));
        langEnItem.addActionListener(e -> changeLanguage(Locale.of("en", "US")));

        langPtItem = new JRadioButton();
        langPtItem.setFont(UIConfig.FONT_DEFAULT);
        langPtItem.setSelected("pt".equals(cur.getLanguage()));
        langPtItem.addActionListener(e -> changeLanguage(Locale.of("pt", "BR")));

        grp.add(langEnItem);
        grp.add(langPtItem);

        addRow(sectionLanguage, langEnItem);
        addRow(sectionLanguage, langPtItem);
    }




    /**
     * Troca o idioma e persiste a preferência.
     */
    protected final void changeLanguage(Locale locale) {
        I18N.setLocale(locale);
        mainFrame.getConfigManager().saveLocale(locale);
    }

    protected void updateTexts() {
        if (langPtItem != null)  langPtItem.setText(I18N.get("optionConfigPanel.langPtItem.setText"));
        if (langEnItem != null)  langEnItem.setText(I18N.get("optionConfigPanel.langEnItem.setText"));

        if (identitySectionBorder != null) {
            identitySectionBorder.setTitle(I18N.get("optionConfigPanel.sectionIdentity.addSection"));
        }
        if (themeSectionBorder != null) {
            themeSectionBorder.setTitle(I18N.get("optionConfigPanel.sectionTheme.addSection"));
        }
        if (languageSectionBorder != null) {
            languageSectionBorder.setTitle(I18N.get("optionConfigPanel.sectionLanguageName"));
        }
        if (imageChBox != null)  imageChBox.setText(I18N.get("optionConfigPanel.JCheckBox.imageChBox"));
        if (videoChBox != null)  videoChBox.setText(I18N.get("optionConfigPanel.JCheckBox.videoChBox"));
        if (pdfChBox != null)  pdfChBox.setText(I18N.get("optionConfigPanel.JCheckBox.pdfChBox"));
        if (model3dChBox != null)  model3dChBox.setText(I18N.get("optionConfigPanel.JCheckBox.model3dChBox"));
    }

    @Override
    public void onLanguageChanged(Locale newLocale) {
        updateTexts();
        repaint();
        revalidate();
    }
}
