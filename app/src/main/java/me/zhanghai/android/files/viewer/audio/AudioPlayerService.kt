package me.zhanghai.android.files.viewer.audio

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.zhanghai.android.files.util.DataState
import me.zhanghai.android.files.util.createIntent

// Keeps the current AudioPlayer playing in background, e.g. with the screen off.
class AudioPlayerService : Service() {
    private lateinit var notification: AudioPlayerNotification

    private val coroutineScope = MainScope()

    override fun onCreate() {
        super.onCreate()

        notification = AudioPlayerNotification(this)
        coroutineScope.launch {
            _player.filterNotNull().collectLatest { player ->
                coroutineScope {
                    // Launched, i.e. dispatched later, so that the startForeground() below always
                    // happens before stopSelf().
                    launch {
                        // There's nothing left to play or control after an error.
                        player.playerState.first { it is DataState.Error }
                        stopSelf()
                    }
                    player.isPlaying.collect { notification.startForeground(player, it) }
                }
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = _player.value
        if (player == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Every Context.startForegroundService() requires a Service.startForeground().
        notification.startForeground(player, player.isPlaying.value)
        when (val action = intent?.action) {
            null -> {}
            ACTION_TOGGLE_PLAY_PAUSE -> player.togglePlayPause()
            ACTION_STOP -> stopSelf()
            else -> throw IllegalArgumentException(action)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        coroutineScope.cancel()
        notification.stopForeground()
        val player = _player.value
        if (player != null) {
            player.release()
            // Keep a failed player so that AudioPlayerFragment can still show its error.
            if (player.playerState.value !is DataState.Error) {
                _player.value = null
            }
        }
    }

    companion object {
        private const val ACTION_TOGGLE_PLAY_PAUSE = "toggle_play_pause"
        private const val ACTION_STOP = "stop"

        private val _player = MutableStateFlow<AudioPlayer?>(null)
        val player = _player.asStateFlow()

        // A failed player is only kept for showing its error, so opening its file again retries.
        fun isOpen(uri: Uri): Boolean {
            val player = _player.value
            return player != null && player.uri == uri
                && player.playerState.value !is DataState.Error
        }

        @MainThread
        fun open(uri: Uri, title: String, context: Context) {
            if (isOpen(uri)) {
                return
            }
            _player.value?.release()
            _player.value = AudioPlayer(uri, title)
            ContextCompat.startForegroundService(
                context, Intent(context, AudioPlayerService::class.java)
            )
        }

        @MainThread
        fun stop(context: Context) {
            context.stopService(Intent(context, AudioPlayerService::class.java))
            // The service doesn't clear a failed player when it stops.
            val player = _player.value
            if (player != null && player.playerState.value is DataState.Error) {
                player.release()
                _player.value = null
            }
        }

        fun createTogglePlayPauseIntent(): Intent =
            AudioPlayerService::class.createIntent().setAction(ACTION_TOGGLE_PLAY_PAUSE)

        fun createStopIntent(): Intent =
            AudioPlayerService::class.createIntent().setAction(ACTION_STOP)
    }
}
