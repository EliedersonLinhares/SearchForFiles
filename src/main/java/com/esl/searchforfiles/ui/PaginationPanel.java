package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.configuration.UIConfig;
import com.esl.searchforfiles.configuration.WrapLayout;
import com.esl.searchforfiles.model.PaginationInfo;

import javax.swing.*;
import java.awt.*;

/**
 * Painel com controles de paginação
 */

public class PaginationPanel extends JPanel {
    private final JButton firstButton;
    private final JButton previousButton;
    private final JLabel pageLabel;
    private final JButton nextButton;
    private final JButton lastButton;
    private final JComboBox<Integer> pageSizeCombo;
    private final JComboBox<Integer> pageSelectCombo; // ← NOVO
    private final JLabel totalLabel;
    private final FileExplorerSwing fileExplorerSwing;
    private PaginationListener paginationListener;
    private PaginationInfo currentPagination;

    // Flag para evitar que a atualização programática do combo
    // dispare o ActionListener como se fosse uma escolha do usuário
    private boolean updatingPageCombo = false;

    public PaginationPanel(FileExplorerSwing fileExplorerSwing) {
        this.fileExplorerSwing = fileExplorerSwing;

        setLayout(new WrapLayout(FlowLayout.CENTER, 8, 4));
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        // ── Botões de navegação ───────────────────────────────────
        firstButton = navButton("⏮", "Primeira página");
        firstButton.addActionListener(e -> {
            goToPage(1);
            fileExplorerSwing.getResultsPanel().topScroll();
        });

        previousButton = navButton("◀", "Página anterior");
        previousButton.addActionListener(e -> {
            goToPreviousPage();
            fileExplorerSwing.getResultsPanel().topScroll();
        });

        pageLabel = new JLabel("Página 1 de 1");
        pageLabel.setFont(UIConfig.FONT_DEFAULT);

        nextButton = navButton("▶", "Próxima página");
        nextButton.addActionListener(e -> {
            goToNextPage();
            fileExplorerSwing.getResultsPanel().topScroll();
        });

        lastButton = navButton("⏭", "Última página");
        lastButton.addActionListener(e -> {
            goToLastPage();
            fileExplorerSwing.getResultsPanel().topScroll();
        });

        // ── Seletor de página específica ──────────────────────────
        pageSelectCombo = new JComboBox<>(new Integer[]{1});
        pageSelectCombo.setFont(UIConfig.FONT_DEFAULT);
        pageSelectCombo.setPreferredSize(new Dimension(70, 26));
        pageSelectCombo.setToolTipText("Ir para página");
        pageSelectCombo.addActionListener(e -> {
            // Ignora eventos disparados programaticamente
            if (updatingPageCombo) return;
            Integer selected = (Integer) pageSelectCombo.getSelectedItem();
            if (selected != null && paginationListener != null
                    && currentPagination != null
                    && selected != currentPagination.getCurrentPage()) {
                goToPage(selected);
                fileExplorerSwing.getResultsPanel().topScroll();
            }
        });

        // ── Items por página ──────────────────────────────────────
        pageSizeCombo = new JComboBox<>(new Integer[]{50, 100, 200, 500, 1000});
        pageSizeCombo.setSelectedItem(100);
        pageSizeCombo.setFont(UIConfig.FONT_DEFAULT);
        pageSizeCombo.setPreferredSize(new Dimension(75, 26));
        pageSizeCombo.addActionListener(e -> onPageSizeChanged());

        // ── Total ─────────────────────────────────────────────────
        totalLabel = new JLabel("0 resultados");
        totalLabel.setFont(UIConfig.FONT_DEFAULT);
        totalLabel.setForeground(Color.GRAY);

        // ── Layout ───────────────────────────────────────────────
        // Grupo 1 — Navegação
        add(firstButton);
        add(previousButton);
        add(pageLabel);
        add(nextButton);
        add(lastButton);

        add(vSep());

        // Grupo 2 — Ir para página
        JLabel goToLabel = new JLabel("Ir para:");
        goToLabel.setFont(UIConfig.FONT_DEFAULT);
        add(goToLabel);
        add(pageSelectCombo);

        add(vSep());

        // Grupo 3 — Tamanho de página
        JLabel perPage = new JLabel("Itens/pág:");
        perPage.setFont(UIConfig.FONT_DEFAULT);
        add(perPage);
        add(pageSizeCombo);

        add(vSep());

        // Grupo 4 — Total
        add(totalLabel);

        setEnabled(false);
    }

    // ── Atualização ───────────────────────────────────────────────

    public void updatePagination(PaginationInfo pagination) {
        this.currentPagination = pagination;

        if (pagination == null) {
            setEnabled(false);
            return;
        }

        setEnabled(true);

        // Atualiza label de página
        pageLabel.setText(String.format("Página %d de %d",
                pagination.getCurrentPage(),
                pagination.getTotalPages()));

        totalLabel.setText(String.format("%,d resultado(s)",
                pagination.getTotalResults()));

        // Atualiza botões
        firstButton.setEnabled(pagination.hasPreviousPage());
        previousButton.setEnabled(pagination.hasPreviousPage());
        nextButton.setEnabled(pagination.hasNextPage());
        lastButton.setEnabled(pagination.hasNextPage());

        // Reconstrói o combo de seleção de página se o total mudou
        rebuildPageSelectCombo(pagination);
    }

    /**
     * Reconstrói os itens do combo sempre que o número de páginas
     * ou a página atual mudar. A flag updatingPageCombo evita que
     * o ActionListener interprete a atualização como escolha do usuário.
     */
    private void rebuildPageSelectCombo(PaginationInfo pagination) {
        int total   = pagination.getTotalPages();
        int current = pagination.getCurrentPage();

        // Verifica se realmente precisa reconstruir (evita trabalho desnecessário)
        boolean needsRebuild = pageSizeCombo.getItemCount() != total;
        if (!needsRebuild && pageSelectCombo.getItemCount() == total) {
            // Só atualiza a seleção
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

        // Ajusta largura do combo para acomodar números grandes (ex: "999")
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
            pageLabel.setText("Nenhum resultado");
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

    private JButton navButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setFont(UIConfig.FONT_DEFAULT);
        btn.setToolTipText(tooltip);
        btn.setPreferredSize(new Dimension(46, 26));
        btn.setMargin(new Insets(2, 6, 2, 6));
        return btn;
    }

    public int getPageSize() {
        Integer size = (Integer) pageSizeCombo.getSelectedItem();
        return size != null ? size : 100;
    }

    public void setPaginationListener(PaginationListener listener) {
        this.paginationListener = listener;
    }

    public interface PaginationListener {
        void onPageChanged(int newPage);
        void onPageSizeChanged(int newPageSize);
    }
}
