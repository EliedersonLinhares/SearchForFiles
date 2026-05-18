package com.esl.searchforfiles.actions.fileTransfer;

public enum TransferMode {
    COPY("Copiar"),
    MOVE("Mover"),
    DELETE("Apagar");

    public final String label;
    TransferMode(String label) { this.label = label; }
}
