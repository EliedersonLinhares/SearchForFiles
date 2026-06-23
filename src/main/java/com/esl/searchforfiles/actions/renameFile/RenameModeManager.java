package com.esl.searchforfiles.actions.renameFile;


import java.io.File;
import java.util.*;

public class RenameModeManager {

    private final RenameMode      mode;
    private final Set<File>       selectedFiles = new LinkedHashSet<>();
    private boolean               active        = false;

    public RenameModeManager(RenameMode mode) { this.mode = mode; }

    public RenameMode getMode()         { return mode; }
    public boolean    isActive()        { return active; }

    public void enterMode()  { active = true;  selectedFiles.clear(); }
    public void exitMode()   { active = false; selectedFiles.clear(); }

    public void toggleSelection(File f) {
        if (!accepts(f)) return;
        if (!selectedFiles.remove(f)) selectedFiles.add(f);
    }

    public void selectAll(List<File> files) {
        files.stream().filter(this::accepts).forEach(selectedFiles::add);
    }

    public void clearSelection()            { selectedFiles.clear(); }
    public Set<File> getSelectedFiles()     { return Collections.unmodifiableSet(selectedFiles); }
    public boolean   isSelected(File f)     { return selectedFiles.contains(f); }
    public int       getSelectedCount()     { return selectedFiles.size(); }
    public void selectFile(File f) {
        if (accepts(f)) selectedFiles.add(f);
    }

    /** Modo FILES aceita qualquer arquivo não-diretório.
     *  Modo FOLDERS aceita apenas diretórios. */
    public boolean accepts(File f) {
        if (f == null) return false;
        return mode == RenameMode.FILES ? !f.isDirectory() : f.isDirectory();
    }
}