package com.esl.searchforfiles.others;


import com.esl.searchforfiles.localization.I18N;

public enum ThumbnailSize {
    PEQUENO      ("thumbnailSize.pequeno",     80,  110),
    MEDIO        ("thumbnailSize.medio",      120,  155),
    GRANDE       ("thumbnailSize.grande",     180,  220),
    EXTRA_GRANDE ("thumbnailSize.extraGrande",256,  300);

    private final String i18nKey;    // chave estável para exibição
    public final int thumbPx;        // tamanho da miniatura em pixels
    public final int cardHeight;     // altura total do card (miniatura + nome)

    ThumbnailSize(String i18nKey, int thumbPx, int cardHeight) {
        this.i18nKey = i18nKey;
        this.thumbPx = thumbPx;
        this.cardHeight = cardHeight;
    }

    /**
     * Traduzido sob demanda a cada chamada — não há cache.
     * Usado apenas para EXIBIÇÃO (o que aparece no JComboBox).
     */
    @Override
    public String toString() {
        return I18N.get(i18nKey);
    }

    /**
     * NOVO: identificador estável, independente de idioma.
     * Use isto — e não toString() — para salvar/persistir a preferência.
     */
    public static ThumbnailSize fromKey(String key) {
        if (key == null) return MEDIO;
        try {
            return ThumbnailSize.valueOf(key);
        } catch (IllegalArgumentException e) {
            return MEDIO; // valor padrão se não encontrar / valor legado inválido
        }
    }

    /**
     * Mantido por compatibilidade, mas agora resolve tanto pela chave
     * estável (name()) quanto pelo texto traduzido em QUALQUER idioma
     * suportado, para não quebrar preferências salvas antes desta mudança.
     */
    @Deprecated
    public static ThumbnailSize fromLabel(String value) {
        if (value == null) return MEDIO;

        // 1. Tenta como chave estável (formato novo)
        try {
            return ThumbnailSize.valueOf(value);
        } catch (IllegalArgumentException ignored) { }

        // 2. Fallback: tenta casar com o texto traduzido no idioma atual
        //    (cobre preferências salvas no formato antigo, ex: "Médio")
        for (ThumbnailSize size : values()) {
            if (size.toString().equalsIgnoreCase(value)) {
                return size;
            }
        }
        return MEDIO;
    }
}
