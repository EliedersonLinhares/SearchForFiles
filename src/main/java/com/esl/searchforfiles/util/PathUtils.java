package com.esl.searchforfiles.util;

public class PathUtils {
    public static String getExtension(String filename) {
        if (filename == null || filename.isEmpty()) return "";

        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    public static String normalizeDriveLetter(String driveLetter) {
        if (driveLetter == null || driveLetter.isEmpty()) {
            return "";
        }
        return driveLetter.toUpperCase().replaceAll("[:\\\\]", "") + ":\\";
    }

    public static String getDriveFromPath(String path) {
        if (path == null || path.length() < 2) return "";
        return path.substring(0, 2);
    }
}
