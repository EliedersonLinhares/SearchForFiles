package com.esl.searchforfiles.preview;


import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import org.apache.pdfbox.rendering.PDFRenderer;
import java.io.IOException;

public class PdfThumbnailPanel extends JPanel {

    private final JPanel container;
    private final JScrollPane scrollPane;
    private java.util.function.Consumer<Integer> onThumbnailClicked;
    private int selectedPage = 0;

    public PdfThumbnailPanel() {
        setLayout(new BorderLayout());

        container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

        scrollPane = new JScrollPane(container);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setPreferredSize(new Dimension(160, 0)); // Largura fixa da barra lateral
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);
    }

    public void setOnThumbnailClicked(java.util.function.Consumer<Integer> callback) {
        this.onThumbnailClicked = callback;
    }

    /**
     * Carrega as miniaturas em background sem travar a interface Java.
     */
    public void loadThumbnails(PDFRenderer renderer, int totalPages) {
        container.removeAll();
        container.revalidate();
        container.repaint();

        if (renderer == null || totalPages == 0) return;

        // SwingWorker renderiza as miniaturas progressivamente em background
        new SwingWorker<Void, ThumbnailItem>() {
            @Override
            protected Void doInBackground() {
                for (int i = 0; i < totalPages; i++) {
                    if (isCancelled()) break;
                    try {
                        // Renderiza a página com escala muito baixa (ex: 0.15f para virar ícone)
                        BufferedImage thumbImg = renderer.renderImage(i, 0.15f);
                        publish(new ThumbnailItem(i, thumbImg));
                    } catch (IOException e) {
                        System.err.println("[PDF Thumbs] Erro no frame " + i);
                    }
                }
                return null;
            }

            @Override
            protected void process(java.util.List<ThumbnailItem> chunks) {
                for (ThumbnailItem item : chunks) {
                    JLabel label = new JLabel(new ImageIcon(item.image));
                    label.setAlignmentX(Component.CENTER_ALIGNMENT);
                    label.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createEmptyBorder(6, 8, 6, 8),
                            BorderFactory.createLineBorder(item.index == selectedPage ? Color.BLUE : Color.GRAY, item.index == selectedPage ? 2 : 1)
                    ));

                    final int pageIdx = item.index;
                    label.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            updateSelection(pageIdx);
                            if (onThumbnailClicked != null) {
                                onThumbnailClicked.accept(pageIdx);
                            }
                        }
                    });

                    container.add(label);
                }
                container.revalidate();
            }
        }.execute();
    }

    /**
     * Destaca visualmente com uma borda azul a miniatura da página que está ativa.
     */
    public void updateSelection(int pageIndex) {
        this.selectedPage = pageIndex;
        Component[] components = container.getComponents();
        for (int i = 0; i < components.length; i++) {
            if (components[i] instanceof JLabel label) {
                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createEmptyBorder(6, 8, 6, 8),
                        BorderFactory.createLineBorder(i == pageIndex ? Color.BLUE : Color.GRAY, i == pageIndex ? 2 : 1)
                ));
            }
        }
        container.repaint();
    }

    public void scrollToPage(int pageIndex) {
        Component[] components = container.getComponents();
        if (components == null || components.length == 0 || pageIndex < 0 || pageIndex >= components.length) {
            return;
        }

        // Aguarda o Swing renderizar o layout para obter a coordenada Y exata do item
        SwingUtilities.invokeLater(() -> {
            Component targetComponent = components[pageIndex];

            // Obtém a posição vertical inicial da miniatura correspondente
            int targetY = targetComponent.getY();

            // Calcula uma margem para centralizar a miniatura na barra lateral
            int viewHeight = scrollPane.getViewport().getHeight();
            int componentHeight = targetComponent.getHeight();
            int scrollPos = targetY - (viewHeight / 2) + (componentHeight / 2);

            // Restringe o valor entre 0 e o máximo do scrollbar para evitar erros de limite
            JScrollBar verticalBar = scrollPane.getVerticalScrollBar();
            int maxScroll = verticalBar.getMaximum() - verticalBar.getVisibleAmount();
            int safeScroll = Math.max(0, Math.min(maxScroll, scrollPos));

            // Move o scroll de forma suave até o destino
            verticalBar.setValue(safeScroll);
        });
    }

    // Classe interna auxiliar para transportar os pacotes de dados
    private record ThumbnailItem(int index, BufferedImage image) {}
}