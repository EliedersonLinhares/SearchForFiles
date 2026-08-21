package com.esl.searchforfiles.configuration;

import javax.swing.*;
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
import java.util.stream.Collectors;

public class AboutConfigPanel extends ConfigPanelBase {

    private static final String LOGO_PATH    = "/img/jupiter128.png";
    private static final String LICENSE_PATH = "/license/license.txt";

    public AboutConfigPanel() {
        buildIdentitySection();
        buildDeveloperSection();
        buildLicenseSection();
    }

    // ── Seção 1: logo + nome + descrição ────────────────────────

    private void buildIdentitySection() {
        JPanel section = addSection("Aplicativo");

        // Carrega o logo
        ImageIcon logo = loadLogo();

        // Lado esquerdo: logo
        JLabel logoLabel = new JLabel(logo);
        logoLabel.setVerticalAlignment(SwingConstants.CENTER);
        logoLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 16));

        // Lado direito: nome + descrição empilhados
        JLabel nameLabel = new JLabel("Jupiter - File Management");
        nameLabel.setFont(UIConfig.FONT_TITLE.deriveFont(Font.BOLD, 20f));

        JLabel descLabel = new JLabel(
                "Gerenciador de arquivos com múltiplas funções");
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
        section.add(row);
        section.add(Box.createVerticalStrut(4));
    }

    // ── Seção 2: desenvolvedor ───────────────────────────────────

    private void buildDeveloperSection() {
        JPanel section = addSection("Desenvolvedor");

        addRow(section, "Autor", makeValueLabel("Eliederson Linhares"));

        // Link clicável para o GitHub
        JLabel githubLink = new JLabel(
                "<html><a href=''>github.com/eliederson</a></html>");
        githubLink.setFont(UIConfig.FONT_DEFAULT);
        githubLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        githubLink.setToolTipText("Abrir no navegador");
        githubLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openUrl("https://github.com/eliederson"); // substitua pela URL real
            }
        });

        addRow(section, "GitHub", githubLink);
    }

    // ── Seção 3: licença ─────────────────────────────────────────

    private void buildLicenseSection() {
        JPanel section = addSection("Licença");

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

        section.add(wrapper);
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
        if (url == null) return "Arquivo de licença não encontrado.";

        try (InputStream in = url.openStream();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "Erro ao carregar licença: " + e.getMessage();
        }
    }

    private void openUrl(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Não foi possível abrir o link:\n" + url,
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
}