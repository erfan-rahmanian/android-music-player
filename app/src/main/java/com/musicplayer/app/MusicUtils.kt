package com.musicplayer.app

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MusicUtils {
    
    private val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.ARTIST_ID
    )
    
    private const val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1"
    private const val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
    
    suspend fun getAllSongs(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val contentResolver: ContentResolver = context.contentResolver
        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        
        try {
            val cursor: Cursor? = contentResolver.query(
                uri,
                projection,
                selection,
                null,
                sortOrder
            )
            
            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val artistIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                
                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val title = it.getString(titleColumn) ?: "Unknown Title"
                    val artist = it.getString(artistColumn) ?: "Unknown Artist"
                    val album = it.getString(albumColumn) ?: "Unknown Album"
                    val duration = it.getLong(durationColumn)
                    val data = it.getString(dataColumn) ?: ""
                    val albumId = it.getLong(albumIdColumn)
                    val artistId = it.getLong(artistIdColumn)
                    
                    val songUri = Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    
                    // Only add songs with valid duration (greater than 30 seconds)
                    if (duration > 30000) {
                        songs.add(
                            Song(
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                duration = duration,
                                data = data,
                                albumId = albumId,
                                artistId = artistId,
                                uri = songUri
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return@withContext songs
    }
    
    fun formatDuration(duration: Long): String {
        val minutes = (duration / 1000) / 60
        val seconds = (duration / 1000) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    
    fun getAlbumArtUri(albumId: Long): Uri {
        return Uri.parse("content://media/external/audio/albumart/$albumId")
    }
    
    fun searchSongs(songs: List<Song>, query: String): List<Song> {
        if (query.isBlank()) return songs
        
        val lowercaseQuery = query.lowercase()
        return songs.filter { song ->
            song.title.lowercase().contains(lowercaseQuery) ||
            song.artist.lowercase().contains(lowercaseQuery) ||
            song.album.lowercase().contains(lowercaseQuery)
        }
    }
    
    fun shuffleSongs(songs: List<Song>): List<Song> {
        return songs.shuffled()
    }
    
    fun sortSongsByTitle(songs: List<Song>): List<Song> {
        return songs.sortedBy { it.title.lowercase() }
    }
    
    fun sortSongsByArtist(songs: List<Song>): List<Song> {
        return songs.sortedBy { it.artist.lowercase() }
    }
    
    fun sortSongsByAlbum(songs: List<Song>): List<Song> {
        return songs.sortedBy { it.album.lowercase() }
    }
    
    fun sortSongsByDuration(songs: List<Song>): List<Song> {
        return songs.sortedBy { it.duration }
    }
}