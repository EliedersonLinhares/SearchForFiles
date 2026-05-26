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
    private final JLabel totalLabel;
    private final FileExplorerSwing fileExplorerSwing;
    private PaginationListener paginationListener;
    private PaginationInfo currentPagination;

    public PaginationPanel(FileExplorerSwing fileExplorerSwing) {
        this.fileExplorerSwing = fileExplorerSwing;

        // WrapLayout: quebra linha quando não há espaço, centralizado
        setLayout(new WrapLayout(FlowLayout.CENTER, 8, 4));
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        // ── Botões ────────────────────────────────────────────────────────────
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

        // ── Items por página ──────────────────────────────────────────────────
        pageSizeCombo = new JComboBox<>(new Integer[]{50, 100, 200, 500, 1000});
        pageSizeCombo.setSelectedItem(100);
        pageSizeCombo.setFont(UIConfig.FONT_DEFAULT);
        pageSizeCombo.setPreferredSize(new Dimension(75, 26));
        pageSizeCombo.addActionListener(e -> onPageSizeChanged());

        // ── Total ─────────────────────────────────────────────────────────────
        totalLabel = new JLabel("0 resultados");
        totalLabel.setFont(UIConfig.FONT_DEFAULT);
        totalLabel.setForeground(Color.GRAY);

        // ── Monta o layout em grupos visuais ──────────────────────────────────
        // Grupo 1 — Navegação
        add(firstButton);
        add(previousButton);
        add(pageLabel);
        add(nextButton);
        add(lastButton);

        // Separador visual (leve — só um espaço rotulado)
        add(vSep());

        // Grupo 2 — Tamanho de página
        JLabel perPage = new JLabel("Itens/pág:");
        perPage.setFont(UIConfig.FONT_DEFAULT);
        add(perPage);
        add(pageSizeCombo);

        add(vSep());

        // Grupo 3 — Total
        add(totalLabel);

        setEnabled(false);
    }

    /**
     * Separador vertical leve entre grupos
     */
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

    /**
     * Atualiza com nova informação de paginação
     */
    public void updatePagination(PaginationInfo pagination) {
        this.currentPagination = pagination;

        if (pagination == null) {
            setEnabled(false);
            return;
        }

        setEnabled(true);

        // Atualiza labels
        pageLabel.setText(String.format("Página %d de %d",
                pagination.getCurrentPage(),
                pagination.getTotalPages()));

        totalLabel.setText(String.format("%,d resultado(s)", pagination.getTotalResults()));

        // Atualiza estado dos botões
        firstButton.setEnabled(pagination.hasPreviousPage());
        previousButton.setEnabled(pagination.hasPreviousPage());
        nextButton.setEnabled(pagination.hasNextPage());
        lastButton.setEnabled(pagination.hasNextPage());
    }

    /**
     * Habilita/desabilita controles
     */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        firstButton.setEnabled(enabled);
        previousButton.setEnabled(enabled);
        nextButton.setEnabled(enabled);
        lastButton.setEnabled(enabled);
        pageSizeCombo.setEnabled(enabled);

        if (!enabled) {
            pageLabel.setText("Nenhum resultado");
            totalLabel.setText("");
        }
    }

    private void goToPage(int page) {
        if (paginationListener != null && currentPagination != null) {
            paginationListener.onPageChanged(page);
        }
    }

    private void goToPreviousPage() {
        if (currentPagination != null && currentPagination.hasPreviousPage()) {
            goToPage(currentPagination.getCurrentPage() - 1);
        }
    }

    private void goToNextPage() {
        if (currentPagination != null && currentPagination.hasNextPage()) {
            goToPage(currentPagination.getCurrentPage() + 1);
        }
    }

    private void goToLastPage() {
        if (currentPagination != null) {
            goToPage(currentPagination.getTotalPages());
        }
    }

    private void onPageSizeChanged() {
        if (paginationListener != null) {
            Integer newSize = (Integer) pageSizeCombo.getSelectedItem();
            if (newSize != null) {
                paginationListener.onPageSizeChanged(newSize);
            }
        }
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
