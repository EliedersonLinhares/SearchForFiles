package com.esl.searchforfiles.actions.renameFile;


// 6. TableRowDragHandler.java
//    Permite arrastar linhas do JTable para reordenar.
//    Desenha uma linha indicadora na posição de soltura e
//    uma sobreposição semitransparente na linha sendo arrastada.
// ═══════════════════════════════════════════════════════════════════
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class TableRowDragHandler extends MouseAdapter {

    private final JTable           table;
    private final RenameTableModel model;

    private int     dragFromRow  = -1;   // linha de origem do drag
    private int     dropTargetRow = -1;  // linha indicadora (onde vai cair)
    private boolean dragging     = false;

    // Cor da linha indicadora de destino
    private static final Color LINE_COLOR = new Color(100, 180, 255);

    public TableRowDragHandler(JTable table, RenameTableModel model) {
        this.table = table;
        this.model = model;
        table.addMouseListener(this);
        table.addMouseMotionListener(this);

        // Sobrescreve paintComponent para desenhar indicador e overlay
        JViewport vp = (JViewport) table.getParent();
        // Usa um decorator no próprio table via override inline
        installPainter();
    }

    /** Instala um JLayer sobre a tabela para desenhar o feedback visual. */
    private void installPainter() {
        // Abordagem simples: substituímos o renderer de seleção padrão
        // pela lógica de pintura customizada no próprio table.
        // Para não depender de JLayer (Java 7+), usamos um wrapper de
        // paintComponent via subclasse anônima criada aqui.
        // Como não podemos subclassificar table após criação,
        // usamos um ComponentListener que redesenha o glass pane.

        // Registra um GlassPane no JFrame pai para o overlay
        SwingUtilities.invokeLater(() -> {
            JRootPane root = SwingUtilities.getRootPane(table);
            if (root == null) return;

            JPanel glass = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    if (!dragging || dropTargetRow < 0) return;

                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

                    // Converte coordenadas da tabela para o glass pane
                    Point tblLoc = SwingUtilities.convertPoint(
                            table, 0, 0, this);

                    // Overlay azul na linha sendo arrastada
                    if (dragFromRow >= 0 && dragFromRow < table.getRowCount()) {
                        Rectangle srcRect = table.getCellRect(dragFromRow, 0, true);
                        srcRect.width = table.getWidth();
                        g2.setColor(new Color(100, 160, 255, 40));
                        g2.fillRect(tblLoc.x + srcRect.x,
                                tblLoc.y + srcRect.y,
                                srcRect.width, srcRect.height);
                    }

                    // Linha indicadora de destino
                    int targetY;
                    if (dropTargetRow >= table.getRowCount()) {
                        // Abaixo da última linha
                        Rectangle last = table.getCellRect(
                                table.getRowCount() - 1, 0, true);
                        targetY = tblLoc.y + last.y + last.height;
                    } else {
                        Rectangle rect = table.getCellRect(dropTargetRow, 0, true);
                        targetY = tblLoc.y + rect.y;
                    }

                    g2.setColor(LINE_COLOR);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawLine(tblLoc.x, targetY,
                            tblLoc.x + table.getWidth(), targetY);

                    // Triângulo indicador nas bordas
                    int[] xs = {tblLoc.x, tblLoc.x + 8, tblLoc.x};
                    int[] ys = {targetY - 4, targetY, targetY + 4};
                    g2.fillPolygon(xs, ys, 3);
                    int rx = tblLoc.x + table.getWidth();
                    int[] xs2 = {rx, rx - 8, rx};
                    g2.fillPolygon(xs2, ys, 3);

                    g2.dispose();
                }
            };
            glass.setOpaque(false);
            glass.setFocusable(false);
            root.setGlassPane(glass);
            glass.setVisible(true);
        });
    }

    // ── Mouse events ──────────────────────────────────────────────

    @Override
    public void mousePressed(MouseEvent e) {
        dragFromRow  = table.rowAtPoint(e.getPoint());
        dropTargetRow = dragFromRow;
        dragging      = false;
        table.setRowSelectionInterval(dragFromRow, dragFromRow);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (dragFromRow < 0) return;
        dragging = true;

        int y = e.getPoint().y;
        int rows = table.getRowCount();
        int rh   = table.getRowHeight();

        // Calcula linha de destino pelo centro das células
        int target = y / rh;
        if (y % rh > rh / 2) target++;           // snap para a metade inferior
        target = Math.max(0, Math.min(rows, target));

        dropTargetRow = target;

        // Scroll automático quando o drag chega perto das bordas
        Rectangle vis = table.getVisibleRect();
        if (y < vis.y + 20)
            table.scrollRectToVisible(new Rectangle(0, y - rh, 1, rh));
        else if (y > vis.y + vis.height - 20)
            table.scrollRectToVisible(new Rectangle(0, y + rh, 1, rh));

        repaintGlass();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (dragging && dragFromRow >= 0 && dropTargetRow >= 0) {
            // Ajusta índice: se o destino é após a origem, desconta 1
            int to = dropTargetRow > dragFromRow
                    ? dropTargetRow - 1 : dropTargetRow;
            to = Math.max(0, Math.min(model.getRowCount() - 1, to));
            model.moveRow(dragFromRow, to);
            table.setRowSelectionInterval(to, to);
        }
        dragFromRow   = -1;
        dropTargetRow = -1;
        dragging      = false;
        repaintGlass();
    }

    @Override public void mouseExited(MouseEvent e) {
        if (!dragging) { dragFromRow = -1; dropTargetRow = -1; }
    }

    private void repaintGlass() {
        JRootPane root = SwingUtilities.getRootPane(table);
        if (root != null && root.getGlassPane() != null)
            root.getGlassPane().repaint();
    }
}
