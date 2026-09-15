package com.esl.searchforfiles.model;

import com.esl.searchforfiles.localization.I18N;

public enum OrderBy {
    ASC("orderBy.asc", "ASC"),
    DESC("orderBy.desc", "DESC");

    private final String i18nKey;   // chave estável, nunca muda
    private final String sqlOrder;  // identificador lógico/persistido, nunca muda

    OrderBy(String i18nKey, String sqlOrder) {
        this.i18nKey = i18nKey;
        this.sqlOrder = sqlOrder;
    }

    public String getSqlOrder() {
        return sqlOrder;
    }

    /**
     * Traduzido sob demanda a cada chamada — não há cache.
     * O JComboBox<OrderBy> desenha o texto correto automaticamente
     * assim que for repintado, sem precisar recriar o modelo.
     */
    @Override
    public String toString() {
        return I18N.get(i18nKey);
    }

    public static OrderBy fromDisplayName(String sqlOrder) {
        for (OrderBy o : values()) {
            if (o.sqlOrder.equalsIgnoreCase(sqlOrder)) return o;
        }
        return DESC;
    }
}
