package com.zain.musicplayer.model;

public class FolderItem {
    public final String name;
    public final String path;
    public int songCount;

    public FolderItem(String name, String path, int songCount) {
        this.name = name;
        this.path = path;
        this.songCount = songCount;
    }
}
