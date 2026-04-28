package com.esl.searchforfiles.model;

public enum SortOption {
    NAME("Nome", "name"),
    DATE("Data de Modificação", "last_modified"),
    SIZE("Tamanho", "size"),
    TYPE("Tipo", "file_type"),
    PATH("Caminho", "path");

    private final String displayName;
    private final String fieldName;

    SortOption(String displayName, String fieldName) {
        this.displayName = displayName;
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static SortOption fromDisplayName(String fieldName){
        for (SortOption size : values()){
            if(size.fieldName.equalsIgnoreCase(fieldName)){
            return size;
            }
        }
        return DATE;
    }
}
