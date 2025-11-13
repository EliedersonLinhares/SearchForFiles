package com.esl.searchforfiles.model;

public class DriveInfo {
    private final String path;
    private final long totalSpace;
    private final long freeSpace;
    private final long usableSpace;

    public DriveInfo(String path, long totalSpace, long freeSpace, long usableSpace) {
        this.path = path;
        this.totalSpace = totalSpace;
        this.freeSpace = freeSpace;
        this.usableSpace = usableSpace;
    }

    public String getPath() { return path; }
    public long getTotalSpace() { return totalSpace; }
    public long getFreeSpace() { return freeSpace; }
    public long getUsableSpace() { return usableSpace; }

    @Override
    public String toString() {
        return String.format("%s - Total: %.2f GB, Livre: %.2f GB (%.1f%%)",
                path,
                totalSpace / (1024.0 * 1024.0 * 1024.0),
                freeSpace / (1024.0 * 1024.0 * 1024.0),
                (freeSpace * 100.0) / totalSpace);
    }
}
