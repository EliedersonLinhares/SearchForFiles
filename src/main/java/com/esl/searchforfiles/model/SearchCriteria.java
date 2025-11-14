package com.esl.searchforfiles.model;

public class SearchCriteria {
    private String namePattern;
    private String extension;
    private FileType fileType;
    private Long minSize;
    private Long maxSize;
    private String parentPath;
    private boolean includeSubfolders = true;
    private String driveFilter;
    private Long modifiedAfter;
    private Long modifiedBefore;
    private String sortBy = "name";
    private String sortOrder = "ASC";
    private int limit = 3000;

    // Getters
    public String getNamePattern() { return namePattern; }
    public String getExtension() { return extension; }
    public FileType getFileType() { return fileType; }
    public Long getMinSize() { return minSize; }
    public Long getMaxSize() { return maxSize; }
    public String getParentPath() { return parentPath; }
    public boolean isIncludeSubfolders() { return includeSubfolders; }
    public String getDriveFilter() { return driveFilter; }
    public Long getModifiedAfter() { return modifiedAfter; }
    public Long getModifiedBefore() { return modifiedBefore; }
    public String getSortBy() { return sortBy; }
    public String getSortOrder() { return sortOrder; }
    public int getLimit() { return limit; }

    // Fluent API
    public SearchCriteria withName(String pattern) {
        this.namePattern = pattern;
        return this;
    }

    public SearchCriteria withExtension(String ext) {
        this.extension = ext;
        return this;
    }

    public SearchCriteria withFileType(FileType type) {
        this.fileType = type;
        return this;
    }

    public SearchCriteria withMinSize(long size) {
        this.minSize = size;
        return this;
    }

    public SearchCriteria withMaxSize(long size) {
        this.maxSize = size;
        return this;
    }

    public SearchCriteria inPath(String path) {
        this.parentPath = path;
        return this;
    }

    public SearchCriteria inPath(String path, boolean includeSubfolders) {
        this.parentPath = path;
        this.includeSubfolders = includeSubfolders;
        return this;
    }

    public SearchCriteria inDrive(String driveLetter) {
        this.driveFilter = driveLetter;
        return this;
    }

    public SearchCriteria modifiedAfter(long timestamp) {
        this.modifiedAfter = timestamp;
        return this;
    }

    public SearchCriteria modifiedBefore(long timestamp) {
        this.modifiedBefore = timestamp;
        return this;
    }

    public SearchCriteria sortBy(String field, String order) {
        this.sortBy = field;
        this.sortOrder = order;
        return this;
    }

    public SearchCriteria limit(int limit) {
        this.limit = limit;
        return this;
    }

    public String toCacheKey() {
        return String.format("adv:%s:%s:%s:%d:%d:%s:%b:%s:%d:%d:%s:%s",
                namePattern, extension, fileType, minSize, maxSize,
                parentPath, includeSubfolders, driveFilter,
                modifiedAfter, modifiedBefore, sortBy, sortOrder);
    }
}
