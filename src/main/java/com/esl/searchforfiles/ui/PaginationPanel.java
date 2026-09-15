package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.configuration.UIConfig;
import com.esl.searchforfiles.configuration.WrapLayout;
import com.esl.searchforfiles.localization.I18N;
import com.esl.searchforfiles.model.PaginationInfo;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

/**
 * Painel com controles de paginação
 */
public class PaginationPanel extends JPanel implements I18N.LanguageChangeListener {

    private JButton firstButton;
    private JButton previousButton;
    private final JLabel pageLabel;
    private JButton nextButton;
    private JButton lastButton;
    private final JComboBox<Integer> pageSizeCombo;
    private final JComboBox<Integer> pageSelectCombo;
    private final JLabel totalLabel;
    private final FileExplorerSwing fileExplorerSwing;
    private PaginationListener paginationListener;
    private PaginationInfo currentPagination;

    private boolean updatingPageCombo = false;

    // ── NOVO: labels/tooltips que precisam ser atualizados ao trocar idioma
    private JLabel goToLabel;
    private JLabel perPageLabel;

    public PaginationPanel(FileExplorerSwing fileExplorerSwing) {
        this.fileExplorerSwing = fileExplorerSwing;

        setLayout(new WrapLayout(FlowLayout.CENTER, 8, 4));
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        // ── Botões de navegação ───────────────────────────────────
        firstButton = UIConfig.makeHighlightedMediumBtn("⏮",
                I18N.get("paginationPanel.firstButton.tooltip"), 50, 26);
        firstButton.addActionListener(e -> {
            goToPage(1);
            fileExplorerSwing.getResultsPanel().topScroll();
        });

        previousButton = UIConfig.makeHighlightedMediumBtn("◀",
                I18N.get("paginationPanel.previousButton.tooltip"), 50, 26);
        previousButton.addActionListener(e -> {
            goToPreviousPage();
            fileExplorerSwing.getResultsPanel().topScroll();
        });

        pageLabel = new JLabel(I18N.get("paginationPanel.pageLabel.initial"));
        pageLabel.setFont(UIConfig.FONT_DEFAULT);

        nextButton = UIConfig.makeHighlightedMediumBtn("▶",
                I18N.get("paginationPanel.nextButton.tooltip"), 50, 26);
        nextButton.addActionListener(e -> {
            goToNextPage();
            fileExplorerSwing.getResultsPanel().topScroll();
        });

        lastButton = UIConfig.makeHighlightedMediumBtn("⏭",
                I18N.get("paginationPanel.lastButton.tooltip"), 50, 26);
        lastButton.addActionListener(e -> {
            goToLastPage();
            fileExplorerSwing.getResultsPanel().topScroll();
        });

        // ── Seletor de página específica ──────────────────────────
        pageSelectCombo = new JComboBox<>(new Integer[]{1});
        pageSelectCombo.setFont(UIConfig.FONT_DEFAULT);
        pageSelectCombo.setPreferredSize(new Dimension(70, 26));
        pageSelectCombo.setToolTipText(I18N.get("paginationPanel.pageSelectCombo.tooltip"));
        pageSelectCombo.addActionListener(e -> {
            if (updatingPageCombo) return;
            Integer selected = (Integer) pageSelectCombo.getSelectedItem();
            if (selected != null && paginationListener != null
                    && currentPagination != null
                    && selected != currentPagination.getCurrentPage()) {
                goToPage(selected);
                fileExplorerSwing.getResultsPanel().topScroll();
            }
        });

        // ── Itens por página ──────────────────────────────────────
        pageSizeCombo = new JComboBox<>(new Integer[]{50, 100, 200, 500, 1000});
        pageSizeCombo.setSelectedItem(fileExplorerSwing.getConfigManager().getItensShow());
        pageSizeCombo.setFont(UIConfig.FONT_DEFAULT);
        pageSizeCombo.setPreferredSize(new Dimension(75, 26));
        pageSizeCombo.addActionListener(e -> {
            onPageSizeChanged();
            Integer itemSelecionado = (Integer) pageSizeCombo.getSelectedItem();
            fileExplorerSwing.getConfigManager().saveItensShow(itemSelecionado);
        });

        // ── Total ─────────────────────────────────────────────────
        totalLabel = new JLabel(I18N.get("paginationPanel.totalLabel.initial"));
        totalLabel.setFont(UIConfig.FONT_DEFAULT);
        totalLabel.setForeground(Color.GRAY);

        // ── Layout ───────────────────────────────────────────────
        add(firstButton);
        add(previousButton);
        add(pageLabel);
        add(nextButton);
        add(lastButton);

        add(vSep());

        goToLabel = new JLabel(I18N.get("paginationPanel.goToLabel.text"));
        goToLabel.setFont(UIConfig.FONT_DEFAULT);
        add(goToLabel);
        add(pageSelectCombo);

        add(vSep());

        perPageLabel = new JLabel(I18N.get("paginationPanel.perPageLabel.text"));
        perPageLabel.setFont(UIConfig.FONT_DEFAULT);
        add(perPageLabel);
        add(pageSizeCombo);

        add(vSep());

        add(totalLabel);

        setEnabled(false);

        updateTexts();
        I18N.addLanguageChangeListener(this);
    }

    // ── Atualização ───────────────────────────────────────────────

    public void updatePagination(PaginationInfo pagination) {
        this.currentPagination = pagination;

        if (pagination == null) {
            setEnabled(false);
            return;
        }

        setEnabled(true);
        refreshPageAndTotalLabels();

        firstButton.setEnabled(pagination.hasPreviousPage());
        previousButton.setEnabled(pagination.hasPreviousPage());
        nextButton.setEnabled(pagination.hasNextPage());
        lastButton.setEnabled(pagination.hasNextPage());

        rebuildPageSelectCombo(pagination);
    }

    /**
     * NOVO: centraliza a formatação de pageLabel/totalLabel a partir
     * de currentPagination, para poder ser chamada tanto de
     * updatePagination() quanto de updateTexts() (troca de idioma).
     */
    private void refreshPageAndTotalLabels() {
        if (currentPagination == null) return;

        pageLabel.setText(I18N.get("paginationPanel.pageLabel.format")
                .replace("{0}", String.valueOf(currentPagination.getCurrentPage()))
                .replace("{1}", String.valueOf(currentPagination.getTotalPages())));

        totalLabel.setText(I18N.get("paginationPanel.totalLabel.format")
                .replace("{0}", String.format("%,d", currentPagination.getTotalResults())));
    }

    private void rebuildPageSelectCombo(PaginationInfo pagination) {
        int total   = pagination.getTotalPages();
        int current = pagination.getCurrentPage();

        boolean needsRebuild = pageSizeCombo.getItemCount() != total;
        if (!needsRebuild && pageSelectCombo.getItemCount() == total) {
            updatingPageCombo = true;
            pageSelectCombo.setSelectedItem(current);
            updatingPageCombo = false;
            return;
        }

        updatingPageCombo = true;
        try {
            pageSelectCombo.removeAllItems();
            for (int i = 1; i <= total; i++) {
                pageSelectCombo.addItem(i);
            }
            pageSelectCombo.setSelectedItem(current);
        } finally {
            updatingPageCombo = false;
        }

        int digits = String.valueOf(total).length();
        int comboW = Math.max(55, 30 + digits * 14);
        pageSelectCombo.setPreferredSize(
                new Dimension(comboW, pageSelectCombo.getPreferredSize().height));
        revalidate();
    }

    // ── Helpers ───────────────────────────────────────────────────

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        firstButton.setEnabled(enabled);
        previousButton.setEnabled(enabled);
        nextButton.setEnabled(enabled);
        lastButton.setEnabled(enabled);
        pageSizeCombo.setEnabled(enabled);
        pageSelectCombo.setEnabled(enabled);

        if (!enabled) {
            pageLabel.setText(I18N.get("paginationPanel.pageLabel.noResults"));
            totalLabel.setText("");
        }
    }

    private void goToPage(int page) {
        if (paginationListener != null && currentPagination != null)
            paginationListener.onPageChanged(page);
    }

    private void goToPreviousPage() {
        if (currentPagination != null && currentPagination.hasPreviousPage())
            goToPage(currentPagination.getCurrentPage() - 1);
    }

    private void goToNextPage() {
        if (currentPagination != null && currentPagination.hasNextPage())
            goToPage(currentPagination.getCurrentPage() + 1);
    }

    private void goToLastPage() {
        if (currentPagination != null)
            goToPage(currentPagination.getTotalPages());
    }

    private void onPageSizeChanged() {
        if (paginationListener != null) {
            Integer newSize = (Integer) pageSizeCombo.getSelectedItem();
            if (newSize != null)
                paginationListener.onPageSizeChanged(newSize);
        }
    }

    private JSeparator vSep() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(2, 22));
        return sep;
    }

    public int getPageSize() {
        Integer size = (Integer) pageSizeCombo.getSelectedItem();
        return size != null ? size : 100;
    }

    public void setPaginationListener(PaginationListener listener) {
        this.paginationListener = listener;
    }

    // ── i18n ─────────────────────────────────────────────────────
    private void updateTexts() {
        firstButton.setToolTipText(I18N.get("paginationPanel.firstButton.tooltip"));
        previousButton.setToolTipText(I18N.get("paginationPanel.previousButton.tooltip"));
        nextButton.setToolTipText(I18N.get("paginationPanel.nextButton.tooltip"));
        lastButton.setToolTipText(I18N.get("paginationPanel.lastButton.tooltip"));

        pageSelectCombo.setToolTipText(I18N.get("paginationPanel.pageSelectCombo.tooltip"));

        goToLabel.setText(I18N.get("paginationPanel.goToLabel.text"));
        perPageLabel.setText(I18N.get("paginationPanel.perPageLabel.text"));

        // Reconstrói pageLabel/totalLabel a partir do estado atual,
        // ou usa os textos de "vazio" se o painel estiver desabilitado.
        if (isEnabled() && currentPagination != null) {
            refreshPageAndTotalLabels();
        } else {
            pageLabel.setText(I18N.get("paginationPanel.pageLabel.noResults"));
            totalLabel.setText("");
        }
    }

    @Override
    public void onLanguageChanged(Locale newLocale) {
        updateTexts();
        revalidate();
        repaint();
    }

    public interface PaginationListener {
        void onPageChanged(int newPage);
        void onPageSizeChanged(int newPageSize);
    }
}
