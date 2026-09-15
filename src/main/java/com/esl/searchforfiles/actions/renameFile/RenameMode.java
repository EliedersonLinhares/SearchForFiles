package com.esl.searchforfiles.actions.renameFile;

import com.esl.searchforfiles.localization.I18N;



public enum RenameMode {
    FILES("Renomear arquivos",  "🗒  Modo renomear arquivos",  "resultsPanel.buildRenameToolBar.title.file"),
    FOLDERS("Renomear pastas", "📁  Modo renomear pastas", "resultsPanel.buildRenameToolBar.title.folder");

    public final String label;
    public final String toolbarLabel;
    public final String  i18nKey;

    @Override
    public String toString() {
        return I18N.get(i18nKey);
    }


    RenameMode(String label, String toolbarLabel, String i18nKey) {
        this.label       = label;
        this.toolbarLabel = toolbarLabel;
        this.i18nKey  = i18nKey;
    }
}
