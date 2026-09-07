package com.esl.searchforfiles.actions.videoConverter;

import java.io.File;
import java.util.*;

public class VideoConverterModeManager {
    private static final Set<String> ACCEPTED_EXTS =
            Set.of("mp4", "avi", "webm","mkv");

    private final Set<File> selectedFiles = new LinkedHashSet<>();
    private boolean videoConverterModeActive = false;

    public boolean isVideoConverterModeActive()  { return videoConverterModeActive; }

    public void enterVideoConverterMode() {
        videoConverterModeActive = true;
        selectedFiles.clear();
    }

    public void exitVideoConverterMode() {
        videoConverterModeActive = false;
        selectedFiles.clear();
    }

    public void toggleSelection(File f) {
        if (!isAccepted(f)) return;
        if (!selectedFiles.remove(f)) selectedFiles.add(f);
    }

    public void selectAll(List<File> files) {
        files.stream().filter(VideoConverterModeManager::isAccepted).forEach(selectedFiles::add);
    }

    public void clearSelection()                 { selectedFiles.clear(); }
    public Set<File> getSelectedFiles()          { return Collections.unmodifiableSet(selectedFiles); }
    public boolean isSelected(File f)            { return selectedFiles.contains(f); }
    public int getSelectedCount()                { return selectedFiles.size(); }
    public void selectFile(File f) {
        if (isAccepted(f)) selectedFiles.add(f);
    }

    /** Apenas JPG, JPEG e PNG são aceitos no modo de edição. */
    public static boolean isAccepted(File f) {
        if (f == null) return false;
        String ext = extensionOf(f);
        return ACCEPTED_EXTS.contains(ext);
    }

    private static String extensionOf(File f) {
        String n = f.getName();
        int i = n.lastIndexOf('.');
        return (i >= 0 && i < n.length() - 1)
                ? n.substring(i + 1).toLowerCase(Locale.ROOT) : "";
    }
}
