package com.esl.searchforfiles.ui;

public class ShortcutInfo {
    public final String targetPath;
    public final String relativePath;
    public final String workingDir;
    public final String iconLocation;
    public final String arguments;

    public ShortcutInfo(String targetPath, String relativePath,
                        String workingDir, String iconLocation, String arguments) {
        this.targetPath = targetPath;
        this.relativePath = relativePath;
        this.workingDir = workingDir;
        this.iconLocation = iconLocation;
        this.arguments = arguments;
    }

    @Override
    public String toString() {
        return String.format(
                "ShortcutInfo{\n" +
                        "  Target: %s\n" +
                        "  Relative: %s\n" +
                        "  Working Dir: %s\n" +
                        "  Icon: %s\n" +
                        "  Args: %s\n" +
                        "}",
                targetPath, relativePath, workingDir, iconLocation, arguments
        );
    }
}
