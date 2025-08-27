package com.musicplayer.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var songAdapter: SongAdapter
    private lateinit var playerContainer: View
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: View
    
    // Player controls
    private lateinit var imgAlbumArt: ImageView
    private lateinit var txtCurrentSong: TextView
    private lateinit var txtCurrentArtist: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var txtCurrentTime: TextView
    private lateinit var txtTotalTime: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPrevious: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var btnMenu: ImageButton

    private var musicService: MusicService? = null
    private var isServiceBound = false
    private var songs: List<Song> = emptyList()
    private var currentSong: Song? = null
    private var isPlaying = false
    
    private val handler = Handler(Looper.getMainLooper())
    private var progressUpdateRunnable: Runnable? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isServiceBound = true
            
            musicService?.apply {
                setSongs(songs)
                setPlaybackStateCallback { playing, song ->
                    runOnUiThread {
                        updatePlaybackState(playing, song)
                    }
                }
                setProgressCallback { current, total ->
                    runOnUiThread {
                        updateProgress(current, total)
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isServiceBound = false
            musicService = null
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadMusic()
        } else {
            showPermissionDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initializeViews()
        setupRecyclerView()
        setupPlayerControls()
        setupSeekBar()
        
        checkPermissionAndLoadMusic()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.recyclerViewSongs)
        playerContainer = findViewById(R.id.playerContainer)
        progressBar = findViewById(R.id.progressBar)
        emptyState = findViewById(R.id.emptyState)
        
        // Player views
        imgAlbumArt = findViewById(R.id.imgAlbumArt)
        txtCurrentSong = findViewById(R.id.txtCurrentSong)
        txtCurrentArtist = findViewById(R.id.txtCurrentArtist)
        seekBar = findViewById(R.id.seekBar)
        txtCurrentTime = findViewById(R.id.txtCurrentTime)
        txtTotalTime = findViewById(R.id.txtTotalTime)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnNext = findViewById(R.id.btnNext)
        btnShuffle = findViewById(R.id.btnShuffle)
        btnRepeat = findViewById(R.id.btnRepeat)
        btnMenu = findViewById(R.id.btnMenu)
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            onSongClick = { song, position ->
                playSong(position)
            },
            onMenuClick = { song, view ->
                showSongMenu(song, view)
            }
        )
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = songAdapter
        }
    }

    private fun setupPlayerControls() {
        btnPlayPause.setOnClickListener {
            togglePlayPause()
        }
        
        btnPrevious.setOnClickListener {
            musicService?.playPrevious()
        }
        
        btnNext.setOnClickListener {
            musicService?.playNext()
        }
        
        btnShuffle.setOnClickListener {
            toggleShuffle()
        }
        
        btnRepeat.setOnClickListener {
            toggleRepeat()
        }
        
        btnMenu.setOnClickListener {
            showMainMenu()
        }
    }

    private fun setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = musicService?.getDuration() ?: 0
                    val position = (progress.toFloat() / 100 * duration).toInt()
                    musicService?.seekTo(position)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun checkPermissionAndLoadMusic() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                loadMusic()
            }
            
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU -> {
                when {
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_MEDIA_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        loadMusic()
                    }
                    else -> {
                        permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                    }
                }
            }
            
            else -> {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun loadMusic() {
        progressBar.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        recyclerView.visibility = View.GONE
        
        lifecycleScope.launch {
            try {
                songs = MusicUtils.getAllSongs(this@MainActivity)
                
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    
                    if (songs.isEmpty()) {
                        emptyState.visibility = View.VISIBLE
                        playerContainer.visibility = View.GONE
                    } else {
                        recyclerView.visibility = View.VISIBLE
                        playerContainer.visibility = View.VISIBLE
                        songAdapter.submitList(songs)
                        
                        // Start and bind to music service
                        startMusicService()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                    Toast.makeText(this@MainActivity, getString(R.string.error_loading_music), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startMusicService() {
        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun playSong(index: Int) {
        if (index >= 0 && index < songs.size) {
            musicService?.playSong(index)
            songAdapter.setCurrentPlayingPosition(index)
        }
    }

    private fun togglePlayPause() {
        musicService?.let { service ->
            if (service.isPlaying()) {
                service.pauseMusic()
            } else {
                if (service.getCurrentSong() == null && songs.isNotEmpty()) {
                    // No song is selected, play the first one
                    playSong(0)
                } else {
                    service.resumeMusic()
                }
            }
        }
    }

    private fun toggleShuffle() {
        musicService?.let { service ->
            val isShuffleEnabled = service.toggleShuffle()
            updateShuffleButton(isShuffleEnabled)
            
            val message = if (isShuffleEnabled) {
                getString(R.string.shuffle_on)
            } else {
                getString(R.string.shuffle_off)
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleRepeat() {
        musicService?.let { service ->
            val repeatMode = service.toggleRepeat()
            updateRepeatButton(repeatMode)
            
            val message = when (repeatMode) {
                MusicService.RepeatMode.OFF -> getString(R.string.repeat_off)
                MusicService.RepeatMode.ONE -> getString(R.string.repeat_one)
                MusicService.RepeatMode.ALL -> getString(R.string.repeat_all)
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePlaybackState(playing: Boolean, song: Song?) {
        isPlaying = playing
        currentSong = song
        
        // Update play/pause button
        val playPauseIcon = if (playing) R.drawable.ic_pause else R.drawable.ic_play
        btnPlayPause.setImageResource(playPauseIcon)
        
        // Update song info
        song?.let {
            txtCurrentSong.text = it.getDisplayTitle()
            txtCurrentArtist.text = it.getDisplayArtist()
            
            // Load album art
            val albumArtUri = MusicUtils.getAlbumArtUri(it.albumId)
            Glide.with(this)
                .load(albumArtUri)
                .placeholder(R.drawable.album_art_placeholder)
                .error(R.drawable.album_art_placeholder)
                .into(imgAlbumArt)
        } ?: run {
            txtCurrentSong.text = getString(R.string.unknown_title)
            txtCurrentArtist.text = getString(R.string.unknown_artist)
            imgAlbumArt.setImageResource(R.drawable.album_art_placeholder)
        }
        
        // Start or stop progress updates
        if (playing) {
            startProgressUpdates()
        } else {
            stopProgressUpdates()
        }
    }

    private fun updateProgress(current: Int, total: Int) {
        if (total > 0) {
            val progress = (current.toFloat() / total * 100).toInt()
            seekBar.progress = progress
            
            txtCurrentTime.text = MusicUtils.formatDuration(current.toLong())
            txtTotalTime.text = MusicUtils.formatDuration(total.toLong())
        }
    }

    private fun updateShuffleButton(isEnabled: Boolean) {
        val alpha = if (isEnabled) 1.0f else 0.5f
        btnShuffle.alpha = alpha
    }

    private fun updateRepeatButton(repeatMode: MusicService.RepeatMode) {
        val alpha = if (repeatMode != MusicService.RepeatMode.OFF) 1.0f else 0.5f
        btnRepeat.alpha = alpha
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressUpdateRunnable = object : Runnable {
            override fun run() {
                musicService?.let { service ->
                    val current = service.getCurrentPosition()
                    val total = service.getDuration()
                    updateProgress(current, total)
                    
                    if (service.isPlaying()) {
                        handler.postDelayed(this, 1000)
                    }
                }
            }
        }
        handler.post(progressUpdateRunnable!!)
    }

    private fun stopProgressUpdates() {
        progressUpdateRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun showSongMenu(song: Song, anchorView: View) {
        val popup = PopupMenu(this, anchorView)
        popup.menuInflater.inflate(R.menu.song_menu, popup.menu)
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_play_next -> {
                    // Implement play next functionality
                    true
                }
                R.id.action_add_to_queue -> {
                    // Implement add to queue functionality
                    true
                }
                R.id.action_song_info -> {
                    showSongInfo(song)
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }

    private fun showMainMenu() {
        val popup = PopupMenu(this, btnMenu)
        popup.menuInflater.inflate(R.menu.main_menu, popup.menu)
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_refresh -> {
                    loadMusic()
                    true
                }
                R.id.action_settings -> {
                    // Implement settings
                    true
                }
                R.id.action_about -> {
                    showAboutDialog()
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }

    private fun showSongInfo(song: Song) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.song_information))
            .setMessage("""
                ${getString(R.string.title_label)} ${song.getDisplayTitle()}
                ${getString(R.string.artist_label)} ${song.getDisplayArtist()}
                ${getString(R.string.album_label)} ${song.getDisplayAlbum()}
                ${getString(R.string.duration_label)} ${song.getFormattedDuration()}
            """.trimIndent())
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.about_title))
            .setMessage(getString(R.string.about_message))
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_title))
            .setMessage(getString(R.string.permission_needed))
            .setPositiveButton(getString(R.string.grant_permission)) { _, _ ->
                checkPermissionAndLoadMusic()
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                finish()
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
        }
        stopProgressUpdates()
    }

    override fun onResume() {
        super.onResume()
        // Update UI with current service state
        musicService?.let { service ->
            updatePlaybackState(service.isPlaying(), service.getCurrentSong())
            updateShuffleButton(service.isShuffleEnabled())
            updateRepeatButton(service.getRepeatMode())
        }
    }
}