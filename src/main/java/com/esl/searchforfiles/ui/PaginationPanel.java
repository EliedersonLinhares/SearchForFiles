package com.esl.searchforfiles.ui;
import com.esl.searchforfiles.model.PaginationInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

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

    private PaginationListener paginationListener;
    private PaginationInfo currentPagination;

    public PaginationPanel() {
        setLayout(new FlowLayout(FlowLayout.CENTER, 10, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        // Botões de navegação
        firstButton = new JButton("⏮ Primeira");
        firstButton.setFont(new Font("SansSerif", Font.PLAIN, 11));
        firstButton.addActionListener(e -> goToPage(1));

        previousButton = new JButton("◀ Anterior");
        previousButton.setFont(new Font("SansSerif", Font.PLAIN, 11));
        previousButton.addActionListener(e -> goToPreviousPage());

        pageLabel = new JLabel("Página 1 de 1");
        pageLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

        nextButton = new JButton("Próxima ▶");
        nextButton.setFont(new Font("SansSerif", Font.PLAIN, 11));
        nextButton.addActionListener(e -> goToNextPage());

        lastButton = new JButton("Última ⏭");
        lastButton.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lastButton.addActionListener(e -> goToLastPage());

        // ComboBox de tamanho de página
        pageSizeCombo = new JComboBox<>(new Integer[]{50, 100, 200, 500, 1000});
        pageSizeCombo.setSelectedItem(100);
        pageSizeCombo.setFont(new Font("SansSerif", Font.PLAIN, 11));
        pageSizeCombo.addActionListener(e -> onPageSizeChanged());

        // Label de total
        totalLabel = new JLabel("0 resultados");
        totalLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        totalLabel.setForeground(Color.GRAY);

        // Layout
        add(firstButton);
        add(previousButton);
        add(new JLabel(" "));
        add(pageLabel);
        add(new JLabel(" "));
        add(nextButton);
        add(lastButton);
        add(new JSeparator(SwingConstants.VERTICAL));
        add(new JLabel("Itens por página:"));
        add(pageSizeCombo);
        add(new JSeparator(SwingConstants.VERTICAL));
        add(totalLabel);

        // Inicia desabilitado
        setEnabled(false);
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
