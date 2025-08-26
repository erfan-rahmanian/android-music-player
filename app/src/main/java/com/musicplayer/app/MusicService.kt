package com.musicplayer.app

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class MusicService : Service(), MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener,
    MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "MusicPlayerChannel"
        
        // Actions
        const val ACTION_PLAY = "com.musicplayer.app.PLAY"
        const val ACTION_PAUSE = "com.musicplayer.app.PAUSE"
        const val ACTION_PREVIOUS = "com.musicplayer.app.PREVIOUS"
        const val ACTION_NEXT = "com.musicplayer.app.NEXT"
        const val ACTION_STOP = "com.musicplayer.app.STOP"
    }

    private val binder = MusicBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var songs: List<Song> = emptyList()
    private var currentSongIndex = 0
    private var isPlaying = false
    private var isPrepared = false
    private var playbackStateCallback: ((Boolean, Song?) -> Unit)? = null
    private var progressCallback: ((Int, Int) -> Unit)? = null
    
    // Audio focus
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    
    // Media Session
    private lateinit var mediaSession: MediaSessionCompat
    
    // Playback modes
    private var isShuffleEnabled = false
    private var repeatMode = RepeatMode.OFF
    
    enum class RepeatMode {
        OFF, ONE, ALL
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        initializeMediaSession()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handleIntent(intent)
        return START_STICKY
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_PLAY -> resumeMusic()
            ACTION_PAUSE -> pauseMusic()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_NEXT -> playNext()
            ACTION_STOP -> stopMusic()
        }
    }

    private fun initializeMediaSession() {
        mediaSession = MediaSessionCompat(this, "MusicService")
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                resumeMusic()
            }
            
            override fun onPause() {
                pauseMusic()
            }
            
            override fun onSkipToNext() {
                playNext()
            }
            
            override fun onSkipToPrevious() {
                playPrevious()
            }
            
            override fun onStop() {
                stopMusic()
            }
        })
        
        mediaSession.isActive = true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun setSongs(songList: List<Song>) {
        songs = songList
    }

    fun setPlaybackStateCallback(callback: (Boolean, Song?) -> Unit) {
        playbackStateCallback = callback
    }

    fun setProgressCallback(callback: (Int, Int) -> Unit) {
        progressCallback = callback
    }

    fun playSong(index: Int) {
        if (songs.isEmpty() || index < 0 || index >= songs.size) return
        
        currentSongIndex = index
        val song = songs[currentSongIndex]
        
        try {
            releaseMediaPlayer()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(applicationContext, song.uri)
                setOnPreparedListener(this@MusicService)
                setOnCompletionListener(this@MusicService)
                setOnErrorListener(this@MusicService)
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPrepared(mp: MediaPlayer?) {
        isPrepared = true
        requestAudioFocus()
        mp?.start()
        isPlaying = true
        startForeground(NOTIFICATION_ID, createNotification())
        notifyPlaybackState()
        startProgressUpdates()
    }

    override fun onCompletion(mp: MediaPlayer?) {
        when (repeatMode) {
            RepeatMode.ONE -> {
                mp?.seekTo(0)
                mp?.start()
            }
            RepeatMode.ALL -> {
                playNext()
            }
            RepeatMode.OFF -> {
                if (currentSongIndex < songs.size - 1) {
                    playNext()
                } else {
                    stopMusic()
                }
            }
        }
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        releaseMediaPlayer()
        return true
    }

    fun pauseMusic() {
        if (isPrepared && isPlaying) {
            mediaPlayer?.pause()
            isPlaying = false
            stopForeground(false)
            updateNotification()
            notifyPlaybackState()
        }
    }

    fun resumeMusic() {
        if (isPrepared && !isPlaying) {
            requestAudioFocus()
            mediaPlayer?.start()
            isPlaying = true
            startForeground(NOTIFICATION_ID, createNotification())
            notifyPlaybackState()
            startProgressUpdates()
        }
    }

    fun stopMusic() {
        isPlaying = false
        isPrepared = false
        releaseMediaPlayer()
        abandonAudioFocus()
        stopForeground(true)
        notifyPlaybackState()
        stopSelf()
    }

    fun playNext() {
        if (songs.isEmpty()) return
        
        currentSongIndex = if (isShuffleEnabled) {
            (0 until songs.size).random()
        } else {
            (currentSongIndex + 1) % songs.size
        }
        
        playSong(currentSongIndex)
    }

    fun playPrevious() {
        if (songs.isEmpty()) return
        
        currentSongIndex = if (currentSongIndex > 0) {
            currentSongIndex - 1
        } else {
            songs.size - 1
        }
        
        playSong(currentSongIndex)
    }

    fun seekTo(position: Int) {
        if (isPrepared) {
            mediaPlayer?.seekTo(position)
        }
    }

    fun getCurrentPosition(): Int {
        return if (isPrepared) mediaPlayer?.currentPosition ?: 0 else 0
    }

    fun getDuration(): Int {
        return if (isPrepared) mediaPlayer?.duration ?: 0 else 0
    }

    fun getCurrentSong(): Song? {
        return if (songs.isNotEmpty() && currentSongIndex < songs.size) {
            songs[currentSongIndex]
        } else null
    }

    fun isPlaying(): Boolean = isPlaying

    fun toggleShuffle(): Boolean {
        isShuffleEnabled = !isShuffleEnabled
        return isShuffleEnabled
    }

    fun toggleRepeat(): RepeatMode {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        return repeatMode
    }

    fun getRepeatMode(): RepeatMode = repeatMode
    fun isShuffleEnabled(): Boolean = isShuffleEnabled

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()
            
            audioManager.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (!isPlaying) resumeMusic()
                mediaPlayer?.setVolume(1.0f, 1.0f)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                stopMusic()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (isPlaying) pauseMusic()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mediaPlayer?.setVolume(0.3f, 0.3f)
            }
        }
    }

    private fun createNotification(): Notification {
        val song = getCurrentSong()
        
        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                R.drawable.ic_pause,
                getString(R.string.pause),
                createPendingIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_play,
                getString(R.string.play),
                createPendingIntent(ACTION_PLAY)
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song?.getDisplayTitle() ?: getString(R.string.unknown_title))
            .setContentText(song?.getDisplayArtist() ?: getString(R.string.unknown_artist))
            .setSmallIcon(R.drawable.ic_music_note)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(R.drawable.ic_skip_previous, getString(R.string.previous), createPendingIntent(ACTION_PREVIOUS))
            .addAction(playPauseAction)
            .addAction(R.drawable.ic_skip_next, getString(R.string.next), createPendingIntent(ACTION_NEXT))
            .setContentIntent(createContentIntent())
            .setDeleteIntent(createPendingIntent(ACTION_STOP))
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply {
            this.action = action
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        return PendingIntent.getService(this, action.hashCode(), intent, flags)
    }

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun notifyPlaybackState() {
        playbackStateCallback?.invoke(isPlaying, getCurrentSong())
    }

    private fun startProgressUpdates() {
        // Implementation for progress updates would go here
        // This would typically use a Handler or Timer to periodically update progress
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.apply {
            stop()
            reset()
            release()
        }
        mediaPlayer = null
        isPrepared = false
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaPlayer()
        abandonAudioFocus()
        mediaSession.release()
    }
}