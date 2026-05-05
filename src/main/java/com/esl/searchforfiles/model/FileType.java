package com.esl.searchforfiles.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public enum FileType {
    AUDIO, VIDEO, IMAGE, DOCUMENT, COMPRESSED, EXECUTABLE, FOLDER,CONFIGURATION, ALL;

    private static final Map<FileType, Set<String>> EXTENSIONS = new HashMap<>();

    static {
        EXTENSIONS.put(AUDIO, Set.of(
                "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "opus", "ape", "alac", "m3u"
        ));

        EXTENSIONS.put(VIDEO, Set.of(
                "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v", "mpg", "mpeg", "3gp"
        ));

        EXTENSIONS.put(IMAGE, Set.of(
                "jpg", "jpeg", "png", "gif", "bmp", "svg", "webp", "ico", "tiff", "tif", "heic", "raw"
        ));

        EXTENSIONS.put(DOCUMENT, Set.of(
                "pdf", "doc", "docx", "txt", "rtf", "odt", "xls", "xlsx", "ppt", "pptx",
                "csv", "xml", "json", "md", "log"
        ));

        EXTENSIONS.put(COMPRESSED, Set.of(
                "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "dmg", "pkg"
        ));

        EXTENSIONS.put(EXECUTABLE, Set.of(
                "exe", "msi", "bat", "cmd", "sh", "app", "jar", "com", "ini"
        ));
    }

    public static FileType fromExtension(String extension) {
        if (extension == null || extension.isEmpty()) return ALL;

        String ext = extension.toLowerCase();
        for (Map.Entry<FileType, Set<String>> entry : EXTENSIONS.entrySet()) {
            if (entry.getValue().contains(ext)) {
                return entry.getKey();
            }
        }
        return ALL;
    }

    public static Set<String> getExtensions(FileType type) {
        return EXTENSIONS.getOrDefault(type, Collections.emptySet());
    }
}
