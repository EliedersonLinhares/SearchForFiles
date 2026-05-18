package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.configuration.MultiFileTransferable;
import com.esl.searchforfiles.model.FileType;
import com.esl.searchforfiles.service.IconService;
import com.esl.searchforfiles.actions.fileTransfer.TransferService;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class DragAction {
 
    public DragAction(Supplier<File> fileSupplier,
                      JComponent component,
                      TransferService transferService) {

        DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(
                component,
                DnDConstants.ACTION_COPY_OR_MOVE,
                dge -> {
                    // Modo de transferência com seleção ativa?
                    boolean hasSelection = transferService != null
                            && transferService.isTransferModeActive()
                            && transferService.getSelectedCount() > 0;

                    List<File> filesToDrag;
                    if (hasSelection) {
                        // Garante que o arquivo deste card também esteja incluído
                        File thisFile = fileSupplier.get();
                        if (thisFile != null && !transferService.isSelected(thisFile)) {
                            transferService.toggleSelection(thisFile);
                        }
                        filesToDrag = new ArrayList<>(transferService.getSelectedFiles());
                    } else {
                        // Comportamento original: só pasta única
                        File f = fileSupplier.get();
                        if (f == null || !f.isDirectory()) return;
                        filesToDrag = List.of(f);
                    }

                    Transferable transferable = new MultiFileTransferable(filesToDrag);

                    // Imagem de arraste: mostra contagem se múltiplos
                    BufferedImage dragImg = filesToDrag.size() == 1
                            ? createDragImage(filesToDrag.get(0))
                            : createMultiDragImage(filesToDrag.size());
                    Point offset = new Point(-16, -16);

                    try {
                        dge.startDrag(
                                DragSource.DefaultCopyDrop,
                                dragImg,
                                offset,
                                transferable,
                                new DragSourceAdapter() {
                                    @Override
                                    public void dragEnter(DragSourceDragEvent dsde) {
                                        dsde.getDragSourceContext()
                                                .setCursor(DragSource.DefaultCopyDrop);
                                    }
                                    @Override
                                    public void dragExit(DragSourceEvent dse) {
                                        dse.getDragSourceContext()
                                                .setCursor(DragSource.DefaultCopyNoDrop);
                                    }
                                }
                        );
                    } catch (InvalidDnDOperationException ex) {
                        ex.printStackTrace();
                    }
                }
        );
    }


    /**
     * Imagem de arraste para múltiplos arquivos: badge com contagem.
     * Adicione dentro de DragAction, ao lado de createDragImage().
     */
    private BufferedImage createMultiDragImage(int count) {
        int cardW = 80, cardH = 80, arc = 12;

        BufferedImage img = new BufferedImage(cardW, cardH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Fundo
        g2.setColor(new Color(40, 40, 40, 200));
        g2.fillRoundRect(0, 0, cardW, cardH, arc, arc);

        // Borda
        g2.setColor(new Color(33, 150, 243, 220));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(1, 1, cardW - 2, cardH - 2, arc, arc);

        // Ícone de arquivos empilhados (3 quadrados deslocados)
        int[] offsets = {8, 4, 0};
        for (int i = 2; i >= 0; i--) {
            int ox = offsets[i] + 8;
            int oy = offsets[i] + 8;
            g2.setColor(new Color(100, 140, 200, 180));
            g2.fillRoundRect(ox, oy, 40, 40, 6, 6);
            g2.setColor(new Color(150, 190, 255, 220));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(ox, oy, 40, 40, 6, 6);
        }

        // Badge de contagem
        String badge = String.valueOf(count);
        g2.setFont(new Font("SansSerif", Font.BOLD, 18));
        FontMetrics fm = g2.getFontMetrics();
        int bw = fm.stringWidth(badge) + 10;
        int bh = fm.getHeight() + 2;
        int bx = cardW - bw - 2;
        int by = cardH - bh - 2;

        g2.setColor(new Color(33, 150, 243));
        g2.fillRoundRect(bx, by, bw, bh, 8, 8);
        g2.setColor(Color.WHITE);
        g2.drawString(badge, bx + 5, by + fm.getAscent());

        g2.dispose();
        return img;
    }


    /**
     * Cria a imagem que aparece "fantasma" sob o cursor durante o drag.
     * Combina o ícone da pasta com o nome do arquivo num card semitransparente.
     */
    private BufferedImage createDragImage(File displayFile) {
        int imgSize = 64;
        int cardW   = imgSize + 16;
        int cardH   = imgSize + 28;
        int arc     = 12;

        BufferedImage img = new BufferedImage(cardW, cardH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setColor(new Color(40, 40, 40, 200));
        g2.fillRoundRect(0, 0, cardW, cardH, arc, arc);

        g2.setColor(new Color(33, 150, 243, 220));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(1, 1, cardW - 2, cardH - 2, arc, arc);

        Icon sysIcon = IconService.hasCustomIcon("folder", FileType.FOLDER)
                ? new ImageIcon(IconService.resolve("folder", FileType.FOLDER))
                : FileSystemView.getFileSystemView().getSystemIcon(displayFile);

        BufferedImage iconImg = new BufferedImage(imgSize, imgSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gi = iconImg.createGraphics();
        gi.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        Image scaled = ((ImageIcon) resizeIcon(sysIcon, imgSize)).getImage();
        gi.drawImage(scaled, 0, 0, imgSize, imgSize, null);
        gi.dispose();

        g2.drawImage(iconImg, (cardW - imgSize) / 2, 4, imgSize, imgSize, null);

        String name = shortName(displayFile.getName(), 12);
        g2.setFont(new Font("SansSerif", Font.BOLD, 10));
        g2.setColor(Color.WHITE);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(name, (cardW - fm.stringWidth(name)) / 2, imgSize + 4 + fm.getAscent());

        g2.dispose();
        return img;
    }

    public Icon resizeIcon(Icon icon, int size) {
        if (icon == null) return null;

        BufferedImage img = new BufferedImage(
                icon.getIconWidth(),
                icon.getIconHeight(),
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g2 = img.createGraphics();
        icon.paintIcon(null, g2, 0, 0);
        g2.dispose();

        // Agora redimensiona a imagem
        Image scaled = img.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private String shortName(String name, int maxLen) {
        if (name.length() <= maxLen) return name;
        return name.substring(0, maxLen - 3) + "...";
    }
}
