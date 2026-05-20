package com.esl.searchforfiles.actions.renameFile;

import java.awt.*;

public enum RenameMode {
    FILES("Renomear arquivos",  "🗒  Modo renomear arquivos",  new Color(59, 109, 17)),
    FOLDERS("Renomear pastas", "📁  Modo renomear pastas",    new Color(133, 79, 11));

    public final String label;
    public final String toolbarLabel;
    public final Color  accentColor;

    RenameMode(String label, String toolbarLabel, Color accentColor) {
        this.label       = label;
        this.toolbarLabel = toolbarLabel;
        this.accentColor  = accentColor;
    }
}
