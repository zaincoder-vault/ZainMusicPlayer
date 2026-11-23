package com.zain.musicplayer;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.zain.musicplayer.adapter.FolderAdapter;
import com.zain.musicplayer.adapter.SongAdapter;
import com.zain.musicplayer.model.FolderItem;
import com.zain.musicplayer.model.SongItem;
import com.zain.musicplayer.service.PlaybackService;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerMedia;
    private TextView tabAll, tabFolders, tabAlbums, tabRecent, tvHeaderTitle;
    private TextView tvMiniTitle, tvMiniArtist;
    private ImageButton btnMiniPlayPause, btnMiniNext;
    private ImageView ivMiniArt;

    private ListenableFuture<MediaController> controllerFuture;
    @Nullable
    private MediaController controller;

    private final List<SongItem> allSongs = new ArrayList<>();
    private final List<SongItem> displayedSongs = new ArrayList<>();
    private final List<FolderItem> foldersList = new ArrayList<>();

    private boolean isShowingFolders = false;
    private String activeFolderPath = null;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    requestNotificationPermissionIfNeeded();
                    loadDeviceAudio();
                }
            });

    private final ActivityResultLauncher<String> notifPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupTabs();
        connectMediaController();

        if (hasAudioPermission()) {
            requestNotificationPermissionIfNeeded();
            loadDeviceAudio();
        } else {
            requestAudioPermission();
        }
    }

    private void initViews() {
        recyclerMedia = findViewById(R.id.recyclerMedia);
        recyclerMedia.setLayoutManager(new LinearLayoutManager(this));

        tabAll = findViewById(R.id.tabAll);
        tabFolders = findViewById(R.id.tabFolders);
        tabAlbums = findViewById(R.id.tabAlbums);
        tabRecent = findViewById(R.id.tabRecent);
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle);

        tvMiniTitle = findViewById(R.id.tvMiniTitle);
        tvMiniArtist = findViewById(R.id.tvMiniArtist);
        btnMiniPlayPause = findViewById(R.id.btnMiniPlayPause);
        btnMiniNext = findViewById(R.id.btnMiniNext);
        ivMiniArt = findViewById(R.id.ivMiniArt);

        findViewById(R.id.btnBack).setOnClickListener(v -> {
            if (activeFolderPath != null) {
                activeFolderPath = null;
                showFoldersTab();
            } else if (isShowingFolders) {
                showAllSongsTab();
            }
        });

        btnMiniPlayPause.setOnClickListener(v -> {
            if (controller == null) return;
            if (controller.isPlaying()) {
                controller.pause();
            } else {
                controller.play();
            }
        });

        btnMiniNext.setOnClickListener(v -> {
            if (controller != null && controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem();
            }
        });
    }

    private void connectMediaController() {
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
                controller.addListener(new Player.Listener() {
                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        btnMiniPlayPause.setImageResource(
                                isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play
                        );
                    }

                    @Override
                    public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                        updateMiniPlayerFromMetadata(mediaItem);
                    }
                });
                if (controller.getCurrentMediaItem() != null) {
                    updateMiniPlayerFromMetadata(controller.getCurrentMediaItem());
                    btnMiniPlayPause.setImageResource(
                            controller.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play
                    );
                }
            } catch (Exception ignored) {
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void updateMiniPlayerFromMetadata(@Nullable MediaItem item) {
        if (item == null) return;
        MediaMetadata meta = item.mediaMetadata;
        if (meta.title != null) {
            tvMiniTitle.setText(meta.title);
        }
        if (meta.artist != null) {
            tvMiniArtist.setText(meta.artist);
        }
        if (meta.artworkUri != null) {
            Glide.with(this).load(meta.artworkUri).into(ivMiniArt);
        }
    }

    private void setupTabs() {
        tabAll.setOnClickListener(v -> {
            activeFolderPath = null;
            showAllSongsTab();
        });

        tabFolders.setOnClickListener(v -> {
            activeFolderPath = null;
            showFoldersTab();
        });

        tabAlbums.setOnClickListener(v -> {
            activeFolderPath = null;
            selectTabVisual(tabAlbums);
            isShowingFolders = false;
            displayedSongs.clear();
            displayedSongs.addAll(allSongs);
            bindSongsAdapter(displayedSongs);
        });

        tabRecent.setOnClickListener(v -> {
            activeFolderPath = null;
            selectTabVisual(tabRecent);
            isShowingFolders = false;
            displayedSongs.clear();
            int limit = Math.min(allSongs.size(), 25);
            displayedSongs.addAll(allSongs.subList(0, limit));
            bindSongsAdapter(displayedSongs);
        });
    }

    private void selectTabVisual(TextView activeTab) {
        TextView[] allTabs = {tabAll, tabFolders, tabAlbums, tabRecent};
        for (TextView t : allTabs) {
            if (t == activeTab) {
                t.setBackgroundResource(R.drawable.bg_pill_active);
                t.setTextColor(Color.parseColor("#111215"));
            } else {
                t.setBackgroundResource(R.drawable.bg_pill_inactive);
                t.setTextColor(Color.parseColor("#E0E2E7"));
            }
        }
    }

    private void showAllSongsTab() {
        selectTabVisual(tabAll);
        isShowingFolders = false;
        tvHeaderTitle.setText("My Music");
        displayedSongs.clear();
        displayedSongs.addAll(allSongs);
        bindSongsAdapter(displayedSongs);
    }

    private void showFoldersTab() {
        selectTabVisual(tabFolders);
        isShowingFolders = true;
        tvHeaderTitle.setText("Folders");

        FolderAdapter adapter = new FolderAdapter(foldersList, folder -> {
            activeFolderPath = folder.path;
            isShowingFolders = false;
            tvHeaderTitle.setText(folder.name);

            displayedSongs.clear();
            for (SongItem s : allSongs) {
                if (folder.path.equals(s.folderPath)) {
                    displayedSongs.add(s);
                }
            }
            bindSongsAdapter(displayedSongs);
        });
        recyclerMedia.setAdapter(adapter);
    }

    private void bindSongsAdapter(List<SongItem> list) {
        SongAdapter adapter = new SongAdapter(list, (song, position) -> playQueueAt(list, position));
        recyclerMedia.setAdapter(adapter);
    }

    private void playQueueAt(List<SongItem> queue, int startIndex) {
        if (controller == null || queue.isEmpty()) return;

        List<MediaItem> mediaItems = new ArrayList<>();
        for (SongItem s : queue) {
            MediaMetadata.Builder metaBuilder = new MediaMetadata.Builder()
                    .setTitle(s.title)
                    .setArtist(s.artist);
            if (s.albumArtUri != null) {
                metaBuilder.setArtworkUri(s.albumArtUri);
            }
            MediaItem item = new MediaItem.Builder()
                    .setUri(s.contentUri)
                    .setMediaMetadata(metaBuilder.build())
                    .build();
            mediaItems.add(item);
        }

        controller.setMediaItems(mediaItems, startIndex, 0L);
        controller.prepare();
        controller.play();
        updateMiniPlayerFromMetadata(mediaItems.get(startIndex));
    }

    private void loadDeviceAudio() {
        allSongs.clear();
        foldersList.clear();
        Map<String, FolderItem> folderMap = new LinkedHashMap<>();

        ContentResolver resolver = getContentResolver();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA
        };

        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String sortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = resolver.query(collection, projection, selection, null, sortOrder)) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);

                Uri albumArtBase = Uri.parse("content://media/external/audio/albumart");

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String title = cursor.getString(titleCol);
                    String artist = cursor.getString(artistCol);
                    long albumId = cursor.getLong(albumIdCol);
                    long duration = cursor.getLong(durationCol);
                    String filePath = cursor.getString(dataCol);

                    Uri contentUri = ContentUris.withAppendedId(collection, id);
                    Uri albumArtUri = ContentUris.withAppendedId(albumArtBase, albumId);

                    String folderName = "Storage";
                    String folderPath = "";
                    if (filePath != null) {
                        File f = new File(filePath);
                        File parent = f.getParentFile();
                        if (parent != null) {
                            folderName = parent.getName();
                            folderPath = parent.getAbsolutePath();
                        }
                    }

                    SongItem song = new SongItem(
                            id,
                            title != null ? title : "Unknown Title",
                            artist != null ? artist : "Unknown Artist",
                            folderName,
                            folderPath,
                            duration,
                            contentUri,
                            albumArtUri
                    );
                    allSongs.add(song);

                    FolderItem existing = folderMap.get(folderPath);
                    if (existing == null) {
                        folderMap.put(folderPath, new FolderItem(folderName, folderPath, 1));
                    } else {
                        existing.songCount++;
                    }
                }
            }
        }

        foldersList.addAll(folderMap.values());
        showAllSongsTab();
    }

    private boolean hasAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
        }
    }
}
