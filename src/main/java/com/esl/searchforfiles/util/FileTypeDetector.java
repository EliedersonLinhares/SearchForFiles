package com.esl.searchforfiles.util;

import com.esl.searchforfiles.model.FileType;

public class FileTypeDetector {
    public static FileType detect(String filename, boolean isDirectory) {
        if (isDirectory) return FileType.FOLDER;

        String extension = PathUtils.getExtension(filename);
        if (extension.isEmpty()) return FileType.ALL;

        return FileType.fromExtension(extension);
    }
}
