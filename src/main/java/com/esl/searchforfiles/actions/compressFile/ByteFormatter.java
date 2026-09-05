package com.esl.searchforfiles.actions.compressFile;

public final class ByteFormatter {
    private ByteFormatter() {}

    public static String format(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return String.format("%.2f %s", value, units[unit]);
    }
}