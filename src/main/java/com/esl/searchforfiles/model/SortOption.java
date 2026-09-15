package com.esl.searchforfiles.model;

import com.esl.searchforfiles.localization.I18N;

public enum SortOption {
    NAME("optionSort.name", "name"),
    DATE("optionSort.date", "last_modified"),
    SIZE("optionSort.size", "size"),
    TYPE("optionSort.type", "file_type"),
    PATH("optionSort.path", "path");

    private final String i18nKey;   // chave estável, nunca muda
    private final String fieldName; // identificador lógico/persistido, nunca muda

    SortOption(String i18nKey, String fieldName) {
        this.i18nKey = i18nKey;
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }

    /**
     * Traduzido sob demanda a cada chamada — não há cache.
     * Assim, o JComboBox sempre desenha o texto no idioma atual
     * sem precisar de nenhum listener especial.
     */
    @Override
    public String toString() {
        return I18N.get(i18nKey);
    }

    public static SortOption fromDisplayName(String fieldName) {
        for (SortOption s : values()) {
            if (s.fieldName.equalsIgnoreCase(fieldName)) return s;
        }
        return DATE;
    }
}
