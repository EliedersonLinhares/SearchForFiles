package com.esl.searchforfiles.model;

import java.util.Collections;
import java.util.List;

public class FileInfo {
    private  String path;
    private  String name;
    private final String extension;
    private final FileType fileType;
    private final long size;
    private final long lastModified;
    private final boolean isDirectory;
    private int rating;        // 0–5
    private List<String> tags;          // lazy: carregado sob demanda

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
    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public String getExtension() {
        return extension;
    }

    public FileType getFileType() {
        return fileType;
    }

    public long getSize() {
        return size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public int getRating() {
        return rating;
    }

    public void setPath(String path) {this.path = path;}
    public void setName(String name) {this.name = name;}

    public void setRating(int r) {
        this.rating = r;
    }

    public List<String> getTags() {
        return tags != null ? tags : Collections.emptyList();
    }

    public void setTags(List<String> t) {
        this.tags = t;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s, %.2f MB)",
                fileType, path, extension, size / (1024.0 * 1024.0));
    }
}
