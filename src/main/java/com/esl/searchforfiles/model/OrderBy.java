package com.esl.searchforfiles.model;

public enum OrderBy {
    ASC("↑ Crescente", "ASC"),
    DESC("↓ Decrescente", "DESC");

    private final String displayName;
    private final String sqlOrder;

    OrderBy(String displayName, String sqlOrder) {
        this.displayName = displayName;
        this.sqlOrder = sqlOrder;
    }

    public String getSqlOrder() {
        return sqlOrder;
    }

    @Override
    public String toString() {
        return displayName;
    }
    public static OrderBy fromDisplayName(String sqlOrder){
        for (OrderBy size : values()){
            if(size.sqlOrder.equalsIgnoreCase(sqlOrder)){
                return size;
            }
        }
        return DESC;
    }
}
