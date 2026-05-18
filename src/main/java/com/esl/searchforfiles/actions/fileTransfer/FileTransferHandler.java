package com.esl.searchforfiles.actions.fileTransfer;

import com.esl.searchforfiles.ui.FileItemPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.util.List;

public class FileTransferHandler extends TransferHandler {

    public static final DataFlavor FILE_FLAVOR = DataFlavor.javaFileListFlavor;

    @Override
    public int getSourceActions(JComponent jComponent) {
        return COPY;
    }

    @Override
    protected Transferable createTransferable(JComponent jComponent) {
        if (!(jComponent instanceof FileItemPanel fip)) return null;

        File file = fip.getFile();
        if (file == null) return null;

        return new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{FILE_FLAVOR};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return FILE_FLAVOR.equals(flavor);
            }

            @Override
            public @NotNull Object getTransferData(DataFlavor flavor)
                    throws UnsupportedFlavorException {
                if (!isDataFlavorSupported(flavor))
                    throw new UnsupportedFlavorException(flavor);
                return List.of(file);
            }
        };
    }
}
