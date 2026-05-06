package com.esl.searchforfiles.model;

public enum TransferMode {
    COPY("Copiar"),
    MOVE("Mover"),
    DELETE("Apagar");

    public final String label;
    TransferMode(String label) { this.label = label; }
}
