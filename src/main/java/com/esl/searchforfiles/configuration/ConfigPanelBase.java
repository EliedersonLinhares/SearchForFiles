package com.esl.searchforfiles.configuration;


import javax.swing.*;
import java.awt.*;


// ═══════════════════════════════════════════════════════════════
// ConfigPanelBase — base reutilizável para qualquer aba
//
// Uso:
//   1. Estenda esta classe
//   2. Chame addSection("Título") para criar um grupo
//   3. Dentro do grupo, chame addRow(label, componente) ou addRow(componente)
//   4. Chame addButtons(botão1, botão2...) para a faixa de botões
//
// Todas as abas seguirão o mesmo grid, espaçamento e tipografia.
// ═══════════════════════════════════════════════════════════════
public abstract class ConfigPanelBase extends JPanel {

    // Larguras fixas para alinhar labels na coluna esquerda
    private static final int  LABEL_WIDTH  = 160;
    private static final int  GAP_V        = 6;    // espaço entre linhas
    private static final int  GAP_SECTION  = 14;   // espaço entre seções

    // Painel que acumula todo o conteúdo (layout vertical)
    private final JPanel body;

    protected ConfigPanelBase() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);

        // Scroll caso o conteúdo cresça futuramente
        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        add(scroll, BorderLayout.CENTER);
    }

    // ── API para subclasses ──────────────────────────────────────

    /**
     * Adiciona um grupo com título e borda.
     * Retorna o JPanel interno onde você adicionará as linhas.
     */
    protected JPanel addSection(String title) {
        if (body.getComponentCount() > 0)
            body.add(Box.createVerticalStrut(GAP_SECTION));

        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder(title));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        body.add(section);
        return section;
    }

    /**
     * Adiciona uma linha label + componente dentro de uma seção.
     * O label tem largura fixa (LABEL_WIDTH) e o componente ocupa o resto.
     */
    protected void addRow(JPanel section, String labelText, JComponent value) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        row.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        JLabel lbl = new JLabel(labelText);
        lbl.setFont(UIConfig.FONT_DEFAULT_BOLD);
        lbl.setPreferredSize(new Dimension(LABEL_WIDTH, 22));

        row.add(lbl,   BorderLayout.WEST);
        row.add(value, BorderLayout.CENTER);

        section.add(row);
        section.add(Box.createVerticalStrut(GAP_V));
    }

    /**
     * Adiciona uma linha com apenas um componente (sem label),
     * útil para hints, checkboxes e radio buttons.
     */
    protected void addRow(JPanel section, JComponent comp) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        row.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        row.add(comp, BorderLayout.CENTER);

        section.add(row);
        section.add(Box.createVerticalStrut(GAP_V));
    }

    /**
     * Adiciona a faixa de botões alinhada à esquerda no rodapé do painel.
     */
    protected void addButtons(JButton... buttons) {
        body.add(Box.createVerticalStrut(GAP_SECTION));

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bar.setOpaque(false);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (JButton btn : buttons) bar.add(btn);

        body.add(bar);
    }

    /** Cria um botão padrão com a fonte da aplicação. */
    protected JButton makeBtn(String text) {
        JButton btn = new JButton(text);
        btn.setFont(UIConfig.FONT_DEFAULT);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /** Cria um botão destrutivo (texto vermelho). */
    protected JButton makeDangerBtn(String text) {
        JButton btn = makeBtn(text);
        btn.setForeground(new Color(200, 50, 50));
        return btn;
    }

    /** Cria um JLabel de valor com quebra de texto automática. */
    protected JLabel makeValueLabel(String text) {
        JLabel lbl = new JLabel(wrapHtml(text));
        lbl.setFont(UIConfig.FONT_DEFAULT);
        return lbl;
    }

    /**
     * Atualiza o texto de um label criado com makeValueLabel,
     * mantendo a quebra automática.
     */
    protected void setValueLabelText(JLabel lbl, String text) {
        lbl.setText(wrapHtml(text));
    }

    /** Cria um hint em itálico cinza com quebra de texto automática. */
    protected JLabel makeHint(String text) {
        JLabel lbl = new JLabel(wrapHtml("<i>" + text + "</i>"));
        lbl.setFont(UIConfig.FONT_SMALL);
        lbl.setForeground(Color.GRAY);
        return lbl;
    }

    /**
     * Envolve o conteúdo em HTML com largura máxima definida,
     * forçando quebra de linha automática no JLabel.
     * A largura é calculada descontando label (LABEL_WIDTH),
     * bordas e padding do painel.
     */
    private String wrapHtml(String inner) {
        int maxW = 580 - LABEL_WIDTH - 60; // largura do frame − label − margens
        return "<html><body style='width:" + maxW + "px'>" + inner + "</body></html>";
    }
}