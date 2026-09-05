package com.esl.searchforfiles.preview.model3DViewer;

public class GroupNode {
    public final int id;
    public final String name;
    public final boolean isLeaf;
    public boolean visible;
    public final java.util.List<GroupNode> children = new java.util.ArrayList<>();

    public GroupNode(int id, String name, boolean isLeaf, boolean visible) {
        this.id = id;
        this.name = name;
        this.isLeaf = isLeaf;
        this.visible = visible;
    }
}