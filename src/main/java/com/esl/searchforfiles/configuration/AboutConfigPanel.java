package com.esl.searchforfiles.configuration;

import com.esl.searchforfiles.localization.I18N;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.stream.Collectors;

public class AboutConfigPanel extends ConfigPanelBase implements I18N.LanguageChangeListener {


    private TitledBorder licenseSectionBorder;
    private TitledBorder authorSectionBorder;
    private TitledBorder identitySectionBorder;
    private JLabel nameLabel;
    private JLabel descLabel;
    private JLabel githubLink;

    private static final String LOGO_PATH    = "/img/jupiter128.png";
    private static final String LICENSE_PATH = "/license/license.txt";

    public AboutConfigPanel() {
        buildIdentitySection();
        buildDeveloperSection();
        buildLicenseSection();

        updateTexts();
        I18N.addLanguageChangeListener(this);
    }

    // ── Seção 1: logo + nome + descrição ────────────────────────

    private void buildIdentitySection() {
        JPanel sectionIdentity = addSection(I18N.get("aboutConfigPanel.JPanel.sectionIdentity"));
        identitySectionBorder = (TitledBorder) sectionIdentity.getBorder();

        // Carrega o logo
        ImageIcon logo = loadLogo();

        // Lado esquerdo: logo
        JLabel logoLabel = new JLabel(logo);
        logoLabel.setVerticalAlignment(SwingConstants.CENTER);
        logoLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 16));

        // Lado direito: nome + descrição empilhados
        nameLabel = new JLabel(I18N.get("aboutConfigPanel.nameLabel"));
        nameLabel.setFont(UIConfig.FONT_TITLE.deriveFont(Font.BOLD, 20f));

        descLabel = new JLabel(
                I18N.get("aboutConfigPanel.descLabel"));
        descLabel.setFont(UIConfig.FONT_DEFAULT);
        descLabel.setForeground(Color.GRAY);

        JPanel textBlock = new JPanel();
        textBlock.setLayout(new BoxLayout(textBlock, BoxLayout.Y_AXIS));
        textBlock.setOpaque(false);
        textBlock.add(nameLabel);
        textBlock.add(Box.createVerticalStrut(6));
        textBlock.add(descLabel);

        // Row: logo | textBlock
        JPanel row = new JPanel(new BorderLayout(0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        row.add(logoLabel,  BorderLayout.WEST);
        row.add(textBlock,  BorderLayout.CENTER);

        // Adiciona direto na seção (sem label fixo à esquerda)
        sectionIdentity.add(row);
        sectionIdentity.add(Box.createVerticalStrut(4));
    }

    // ── Seção 2: desenvolvedor ───────────────────────────────────

    private void buildDeveloperSection() {
        JPanel sectionAuthor = addSection(I18N.get("aboutConfigPanel.JPanel.sectionAuthor"));
        authorSectionBorder = (TitledBorder) sectionAuthor.getBorder();

        addRow(sectionAuthor, "Author", makeValueLabel("Eliederson Linhares"));

        // Link clicável para o GitHub
        githubLink = new JLabel(
                "<html><a href=''>github.com/eliederson</a></html>");
        githubLink.setFont(UIConfig.FONT_DEFAULT);
        githubLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        githubLink.setToolTipText(I18N.get("aboutConfigPanel.githubLink.setToolTipText"));
        githubLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openUrl("https://github.com/eliederson"); // substitua pela URL real
            }
        });

        addRow(sectionAuthor, "GitHub", githubLink);
    }

    // ── Seção 3: licença ─────────────────────────────────────────

    private void buildLicenseSection() {
        JPanel sectionLicense = addSection(I18N.get("aboutConfigPanel.JPanel.sectionLicense"));
        licenseSectionBorder = (TitledBorder) sectionLicense.getBorder();

        JTextArea area = new JTextArea(loadLicenseText());
        area.setFont(UIConfig.FONT_SMALL);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JScrollPane scroll = new JScrollPane(area,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(
                UIManager.getColor("Component.borderColor"), 1));
        scroll.setPreferredSize(new Dimension(0, 140));
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
        wrapper.setBorder(BorderFactory.createEmptyBorder(2, 6, 6, 6));
        wrapper.add(scroll, BorderLayout.CENTER);

        sectionLicense.add(wrapper);
    }

    // ── Helpers ──────────────────────────────────────────────────

    private ImageIcon loadLogo() {
        URL url = getClass().getResource(LOGO_PATH);
        if (url == null) {
            // Fallback: quadrado cinza 128×128 caso o arquivo não seja encontrado
            BufferedImage placeholder = new BufferedImage(
                    64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = placeholder.createGraphics();
            g2.setColor(new Color(100, 100, 120));
            g2.fillRoundRect(0, 0, 64, 64, 16, 16);
            g2.dispose();
            return new ImageIcon(placeholder);
        }
        // Redimensiona para 64×64 para não ocupar muito espaço na aba
        Image img = new ImageIcon(url).getImage()
                .getScaledInstance(64, 64, Image.SCALE_SMOOTH);
        return new ImageIcon(img);
    }

    private String loadLicenseText() {
        URL url = getClass().getResource(LICENSE_PATH);
        if (url == null) return I18N.get("aboutConfigPanel.loadLicenseNotFound");

        try (InputStream in = url.openStream();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return I18N.get("aboutConfigPanel.loadLicenseError") + " "  + e.getMessage();
        }
    }

    private void openUrl(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                     I18N.get("aboutConfigPanel.openUrlError.text") + "\n" + url,
                    I18N.get("aboutConfigPanel.openUrlError.title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    protected void updateTexts() {

        if (nameLabel != null)  nameLabel.setText(I18N.get("aboutConfigPanel.nameLabel"));
        if (descLabel != null)  descLabel.setText(I18N.get("aboutConfigPanel.descLabel"));
        if (githubLink != null)   githubLink.setToolTipText(I18N.get("aboutConfigPanel.githubLink.setToolTipText"));

        if (identitySectionBorder != null) {
            identitySectionBorder.setTitle(I18N.get("aboutConfigPanel.JPanel.sectionIdentity"));
        }
        if (licenseSectionBorder != null) {
            licenseSectionBorder.setTitle(I18N.get("aboutConfigPanel.JPanel.sectionLicense"));
        }
        if (authorSectionBorder != null) {
            authorSectionBorder.setTitle(I18N.get("aboutConfigPanel.JPanel.sectionAuthor"));
        }

    }

    @Override
    public void onLanguageChanged(Locale newLocale) {
        updateTexts();
        repaint();
        revalidate();
    }
}