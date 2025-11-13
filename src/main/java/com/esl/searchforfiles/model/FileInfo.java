package com.esl.searchforfiles.model;

public class FileInfo {
    private final String path;
    private final String name;
    private final String extension;
    private final FileType fileType;
    private final long size;
    private final long lastModified;
    private final boolean isDirectory;

    public FileInfo(String path, String name, String extension, FileType fileType,
                    long size, long lastModified, boolean isDirectory) {
        this.path = path;
        this.name = name;
        this.extension = extension;
        this.fileType = fileType;
        this.size = size;
        this.lastModified = lastModified;
        this.isDirectory = isDirectory;
    }

    // Getters
    public String getPath() { return path; }
    public String getName() { return name; }
    public String getExtension() { return extension; }
    public FileType getFileType() { return fileType; }
    public long getSize() { return size; }
    public long getLastModified() { return lastModified; }
    public boolean isDirectory() { return isDirectory; }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s, %.2f MB)",
                fileType, path, extension, size / (1024.0 * 1024.0));
    }
}
