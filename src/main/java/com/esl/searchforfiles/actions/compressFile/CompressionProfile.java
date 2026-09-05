package com.esl.searchforfiles.actions.compressFile;

import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record CompressionProfile(
        CompressionLevel compressionLevel,
        boolean smartStore,
        boolean encrypt,
        char[] password,
        boolean splitEnabled,
        long splitSizeBytes,
        String comment,
        Map<String, CompressionLevel> levelByExtension) {

    public static final long MIN_SPLIT_SIZE = 65536L; // mínimo exigido pelo Zip4j

    public static CompressionProfile normal() {
        return new CompressionProfile(CompressionLevel.NORMAL, true, false, null, false, 0, null, Map.of());
    }

    public static CompressionProfile fast() {
        return new CompressionProfile(CompressionLevel.FAST, true, false, null, false, 0, null, Map.of());
    }

    public static CompressionProfile maximum() {
        return new CompressionProfile(CompressionLevel.MAXIMUM, true, false, null, false, 0, null, Map.of());
    }

    public static CompressionProfile passwordProtected(CompressionLevel level, char[] password) {
        return new CompressionProfile(level, true, true, password, false, 0, null, Map.of());
    }

    /** Extensões com boa taxa de compressão textual/código — usadas quando a otimização por tipo está ativa. */
    public static Map<String, CompressionLevel> defaultLevelOverrides() {
        Map<String, CompressionLevel> map = new HashMap<>();
        for (String ext : List.of("txt", "log", "csv", "json", "xml", "java", "c", "cpp", "h",
                "py", "js", "ts", "html", "css", "sql", "md", "yml", "yaml")) {
            map.put(ext, CompressionLevel.MAXIMUM);
        }
        return map;
    }

    public boolean hasPassword() {
        return encrypt && password != null && password.length > 0;
    }

    public boolean hasSplit() {
        return splitEnabled && splitSizeBytes >= MIN_SPLIT_SIZE;
    }

    public boolean hasComment() {
        return comment != null && !comment.isBlank();
    }

    public CompressionMethod compressionMethodFor(File file) {
        if (smartStore && isAlreadyCompressed(file)) {
            return CompressionMethod.STORE;
        }
        return CompressionMethod.DEFLATE;
    }

    public CompressionLevel compressionLevelFor(File file) {
        CompressionLevel override = levelByExtension.get(extensionOf(file));
        return override != null ? override : compressionLevel;
    }

    private String extensionOf(File file) {
        String name = file.getName().toLowerCase();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "";
    }

    private boolean isAlreadyCompressed(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".mp3") ||
                name.endsWith(".aac") || name.endsWith(".ogg") || name.endsWith(".flac") ||
                name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") ||
                name.endsWith(".mov") || name.endsWith(".webm") || name.endsWith(".zip") ||
                name.endsWith(".rar") || name.endsWith(".7z") || name.endsWith(".gz") ||
                name.endsWith(".bz2") || name.endsWith(".xz") || name.endsWith(".pdf");
    }
}