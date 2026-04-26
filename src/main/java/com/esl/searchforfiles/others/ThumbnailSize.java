package com.esl.searchforfiles.others;


public enum ThumbnailSize {
    PEQUENO    ("Pequeno",      80,  110),
    MEDIO      ("Médio",       120,  155),
    GRANDE     ("Grande",      180,  220),
    EXTRA_GRANDE("Extra Grande",256, 300);

    private final String label;
    public final int thumbPx;   // tamanho da miniatura em pixels
    public final int cardHeight; // altura total do card (miniatura + nome)

    ThumbnailSize(String label, int thumbPx, int cardHeight) {
        this.label     = label;
        this.thumbPx   = thumbPx;
        this.cardHeight = cardHeight;
    }

    @Override public String toString() { return label; }
}
