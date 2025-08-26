package com.musicplayer.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class SongAdapter(
    private val onSongClick: (Song, Int) -> Unit,
    private val onMenuClick: (Song, View) -> Unit
) : ListAdapter<Song, SongAdapter.SongViewHolder>(SongDiffCallback()) {

    private var currentPlayingPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = getItem(position)
        holder.bind(song, position)
    }

    fun setCurrentPlayingPosition(position: Int) {
        val oldPosition = currentPlayingPosition
        currentPlayingPosition = position
        
        if (oldPosition != -1) {
            notifyItemChanged(oldPosition)
        }
        if (currentPlayingPosition != -1) {
            notifyItemChanged(currentPlayingPosition)
        }
    }

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgSongArt: ImageView = itemView.findViewById(R.id.imgSongArt)
        private val txtSongTitle: TextView = itemView.findViewById(R.id.txtSongTitle)
        private val txtSongArtist: TextView = itemView.findViewById(R.id.txtSongArtist)
        private val txtSongDuration: TextView = itemView.findViewById(R.id.txtSongDuration)
        private val btnSongMenu: ImageButton = itemView.findViewById(R.id.btnSongMenu)

        fun bind(song: Song, position: Int) {
            txtSongTitle.text = song.getDisplayTitle()
            txtSongArtist.text = song.getDisplayArtist()
            txtSongDuration.text = song.getFormattedDuration()

            // Load album art
            val albumArtUri = MusicUtils.getAlbumArtUri(song.albumId)
            Glide.with(itemView.context)
                .load(albumArtUri)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.album_art_placeholder)
                .error(R.drawable.album_art_placeholder)
                .into(imgSongArt)

            // Highlight currently playing song
            if (position == currentPlayingPosition) {
                itemView.alpha = 0.7f
                txtSongTitle.setTextColor(itemView.context.getColor(R.color.text_accent))
            } else {
                itemView.alpha = 1.0f
                txtSongTitle.setTextColor(itemView.context.getColor(R.color.text_primary))
            }

            // Set click listeners
            itemView.setOnClickListener {
                onSongClick(song, position)
            }

            btnSongMenu.setOnClickListener {
                onMenuClick(song, it)
            }
        }
    }

    private class SongDiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean {
            return oldItem == newItem
        }
    }
}