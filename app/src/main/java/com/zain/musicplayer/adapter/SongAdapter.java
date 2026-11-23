package com.zain.musicplayer.adapter;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.zain.musicplayer.R;
import com.zain.musicplayer.model.SongItem;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {

    public interface OnSongClickListener {
        void onSongClick(SongItem song, int position);
    }

    private final List<SongItem> songs;
    private final OnSongClickListener listener;
    private final ExecutorService thumbExecutor = Executors.newFixedThreadPool(2);

    public SongAdapter(List<SongItem> songs, OnSongClickListener listener) {
        this.songs = songs;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        SongItem item = songs.get(position);
        holder.tvTitle.setText(item.title);
        holder.tvSubtitle.setText(item.artist + " • " + item.folderName);
        holder.ivArt.setImageResource(android.R.drawable.ic_media_play);

        if (item.albumArtUri != null) {
            Glide.with(holder.itemView.getContext())
                    .load(item.albumArtUri)
                    .placeholder(android.R.drawable.ic_media_play)
                    .error(android.R.drawable.ic_media_play)
                    .into(holder.ivArt);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && item.contentUri != null) {
            final Uri targetUri = item.contentUri;
            holder.ivArt.setTag(targetUri);
            ContentResolver resolver = holder.itemView.getContext().getContentResolver();

            thumbExecutor.execute(() -> {
                try {
                    Bitmap bmp = resolver.loadThumbnail(targetUri, new Size(160, 160), null);
                    if (bmp != null) {
                        holder.ivArt.post(() -> {
                            if (targetUri.equals(holder.ivArt.getTag())) {
                                holder.ivArt.setImageBitmap(bmp);
                            }
                        });
                    }
                } catch (Exception ignored) {
                }
            });
        }

        View.OnClickListener playAction = v -> listener.onSongClick(item, position);
        holder.itemView.setOnClickListener(playAction);
        holder.btnPlay.setOnClickListener(playAction);
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        ImageView ivArt;
        TextView tvTitle, tvSubtitle;
        ImageButton btnPlay;

        SongViewHolder(@NonNull View itemView) {
            super(itemView);
            ivArt = itemView.findViewById(R.id.ivSongArt);
            tvTitle = itemView.findViewById(R.id.tvSongTitle);
            tvSubtitle = itemView.findViewById(R.id.tvSongSubtitle);
            btnPlay = itemView.findViewById(R.id.btnRowPlay);
        }
    }
}
