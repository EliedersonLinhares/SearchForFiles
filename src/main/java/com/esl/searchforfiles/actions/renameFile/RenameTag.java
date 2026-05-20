package com.esl.searchforfiles.actions.renameFile;

import java.util.List;

public class RenameTag {
    public final String code;        // token inserido no texto, ex: "<inc Nr>"
    public final String description; // exibido na JList

    public RenameTag(String code, String description) {
        this.code        = code;
        this.description = description;
    }

    @Override
    public String toString() { return code + "  —  " + description; }

    /** Conjunto padrão de tags disponíveis. */
    public static List<RenameTag> defaults() {
        return List.of(
                new RenameTag("<inc Nr>",    "número incremental (01, 02…)"),
                new RenameTag("<inc Alpha>", "letra incremental (a, b, c…)"),
                new RenameTag("<ad. pasta>", "nome da pasta pai"),
                new RenameTag("<data>",      "data atual (yyyyMMdd)"),
                new RenameTag("<ext>",       "extensão original"),
                new RenameTag("<nome orig>", "nome original sem extensão")
        );
    }
}
