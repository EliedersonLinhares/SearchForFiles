package com.esl.searchforfiles.configuration;


import java.awt.datatransfer.*;
import java.io.File;
import java.util.List;

public class MultiFileTransferable implements Transferable {

    private final List<File> files;

    public MultiFileTransferable(List<File> files) {
        this.files = List.copyOf(files);
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{DataFlavor.javaFileListFlavor};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return DataFlavor.javaFileListFlavor.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
        return files;
    }
}