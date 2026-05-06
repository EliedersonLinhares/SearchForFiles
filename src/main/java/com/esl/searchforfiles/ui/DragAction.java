package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.model.FileType;
import com.esl.searchforfiles.service.IconService;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.function.Supplier;

public class DragAction {

    public DragAction(Supplier<File> fileSupplier, JComponent component) {

        DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(
                component,
                DnDConstants.ACTION_COPY,
                dge -> {
                    // Consulta o arquivo AGORA, não na construção
                    File displayFile = fileSupplier.get();

                    if (displayFile == null || !displayFile.isDirectory()) return;

                    Transferable transferable = new Transferable() {
                        @Override
                        public DataFlavor[] getTransferDataFlavors() {
                            return new DataFlavor[]{DataFlavor.javaFileListFlavor};
                        }
                        @Override
                        public boolean isDataFlavorSupported(DataFlavor flavor) {
                            return DataFlavor.javaFileListFlavor.equals(flavor);
                        }
                        @Override
                        public Object getTransferData(DataFlavor flavor)
                                throws UnsupportedFlavorException {
                            if (!isDataFlavorSupported(flavor))
                                throw new UnsupportedFlavorException(flavor);
                            return List.of(displayFile);
                        }
                    };

                    BufferedImage dragImg = createDragImage(displayFile);
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
