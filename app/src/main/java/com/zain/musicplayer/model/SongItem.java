package com.zain.musicplayer.model;

import android.net.Uri;

public class SongItem {
    public final long id;
    public final String title;
    public final String artist;
    public final String folderName;
    public final String folderPath;
    public final long duration;
    public final Uri contentUri;
    public final Uri albumArtUri;

    public SongItem(long id, String title, String artist, String folderName,
                    String folderPath, long duration, Uri contentUri, Uri albumArtUri) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.folderName = folderName;
        this.folderPath = folderPath;
        this.duration = duration;
        this.contentUri = contentUri;
        this.albumArtUri = albumArtUri;
    }
}
