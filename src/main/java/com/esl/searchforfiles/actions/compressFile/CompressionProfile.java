package com.esl.searchforfiles.actions.compressFile;

import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;

import java.io.File;

public record CompressionProfile(CompressionLevel compressionLevel, boolean smartStore, boolean encrypt,
                                 char[] password) {

    public static CompressionProfile normal() {
        return new CompressionProfile(CompressionLevel.NORMAL, true, false, null);
    }

    public static CompressionProfile fast() {
        return new CompressionProfile(CompressionLevel.FAST, true, false, null);
    }

    public static CompressionProfile maximum() {
        return new CompressionProfile(CompressionLevel.MAXIMUM, true, false, null);
    }

    public static CompressionProfile passwordProtected(CompressionLevel level, char[] password) {
        return new CompressionProfile(level, true, true, password);
    }

    public boolean hasPassword() {
        return encrypt && password != null && password.length > 0;
    }

    public CompressionMethod compressionMethodFor(File file) {
        if (smartStore && isAlreadyCompressed(file)) {
            return CompressionMethod.STORE;
        }
        return CompressionMethod.DEFLATE;
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