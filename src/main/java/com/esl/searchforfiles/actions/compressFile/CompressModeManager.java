package com.esl.searchforfiles.actions.compressFile;


import java.io.File;
import java.util.*;

public class CompressModeManager {


    private final Set<File> selectedFiles = new LinkedHashSet<>();
    private boolean compressModeActive = false;

    public boolean isCompressModeActive()  { return compressModeActive; }

    public void enterCompressMode() {
        compressModeActive = true;
        selectedFiles.clear();
    }

    public void exitCompressMode() {
        compressModeActive = false;
        selectedFiles.clear();
    }

    public void toggleSelection(File f) {
        if (!selectedFiles.remove(f)) selectedFiles.add(f);
    }
    public void selectAll(List<File> files) {
        selectedFiles.addAll(files);
    }
    public void clearSelection()                 { selectedFiles.clear(); }
    public Set<File> getSelectedFiles()          { return Collections.unmodifiableSet(selectedFiles); }
    public boolean isSelected(File f)            { return selectedFiles.contains(f); }
    public int getSelectedCount()                { return selectedFiles.size(); }
    public void selectFile(File f)               {selectedFiles.add(f);
    }
}
