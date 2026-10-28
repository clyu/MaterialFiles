package me.zhanghai.android.files.viewer.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.app.audioManager
import me.zhanghai.android.files.app.contentResolver
import me.zhanghai.android.files.util.DataState
import me.zhanghai.android.files.util.RuntimeBroadcastReceiver
import java.io.FileNotFoundException
import java.io.IOException

class AudioPlayer(val uri: Uri, val title: String) {
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val mediaPlayer = MediaPlayer().apply {
        setAudioAttributes(audioAttributes)
        // Keep the CPU awake while playing with the screen off.
        setWakeMode(application, PowerManager.PARTIAL_WAKE_LOCK)
    }

    private val coroutineScope = MainScope()

    private val _playerState = MutableStateFlow<DataState<Unit>>(DataState.Loading())
    val playerState = _playerState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _isLooping = MutableStateFlow(false)
    val isLooping = _isLooping.asStateFlow()

    private val audioFocusChangeListener =
        AudioManager.OnAudioFocusChangeListener { onAudioFocusChange(it) }

    private val audioFocusRequest =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
        } else {
            null
        }

    private var resumeOnAudioFocusGain = false

    private val becomingNoisyReceiver = RuntimeBroadcastReceiver(
        IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Headphones have been unplugged, so don't continue playing out loud.
                pause()
            }
        },
        application
    )

    private var isReleased = false

    private val isPrepared: Boolean
        get() = !isReleased && _playerState.value is DataState.Success

    val duration: Int
        get() = if (isPrepared) mediaPlayer.duration.coerceAtLeast(0) else 0

    val currentPosition: Int
        get() = if (isPrepared) mediaPlayer.currentPosition else 0

    init {
        mediaPlayer.setOnPreparedListener {
            _playerState.value = DataState.Success(Unit)
            play()
        }
        mediaPlayer.setOnCompletionListener {
            setPlaying(false)
            abandonAudioFocus()
        }
        mediaPlayer.setOnErrorListener { _, what, extra ->
            onError(IOException("MediaPlayer error: what=$what, extra=$extra"))
            true
        }
        coroutineScope.launch {
            // Opening a file through our FileProvider may block for network storages.
            var fileDescriptor: AssetFileDescriptor? = null
            try {
                withContext(Dispatchers.IO) {
                    fileDescriptor = contentResolver.openAssetFileDescriptor(uri, "r")
                        ?: throw FileNotFoundException(uri.toString())
                }
            } catch (e: CancellationException) {
                // withContext() drops its result if we are released while the file is opening.
                fileDescriptor?.close()
                throw e
            } catch (e: Exception) {
                // We are exported and content providers may throw anything for a URI they don't
                // like, e.g. IllegalArgumentException from our own FileProvider for a crafted URI.
                onError(e)
                return@launch
            }
            // MediaPlayer duplicates the file descriptor, so we can close ours afterwards.
            fileDescriptor!!.use {
                try {
                    if (it.declaredLength == AssetFileDescriptor.UNKNOWN_LENGTH) {
                        mediaPlayer.setDataSource(it.fileDescriptor)
                    } else {
                        mediaPlayer.setDataSource(
                            it.fileDescriptor, it.startOffset, it.declaredLength
                        )
                    }
                } catch (e: Exception) {
                    // Also IllegalArgumentException or IllegalStateException.
                    onError(e)
                    return@launch
                }
            }
            mediaPlayer.prepareAsync()
        }
    }

    private fun onError(throwable: Throwable) {
        setPlaying(false)
        _playerState.value = DataState.Error(null, throwable)
    }

    fun play() {
        if (!isPrepared || _isPlaying.value) {
            return
        }
        if (!requestAudioFocus()) {
            return
        }
        mediaPlayer.start()
        setPlaying(true)
    }

    fun pause() {
        resumeOnAudioFocusGain = false
        if (!isPrepared || !_isPlaying.value) {
            return
        }
        mediaPlayer.pause()
        setPlaying(false)
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(position: Int) {
        if (!isPrepared) {
            return
        }
        mediaPlayer.seekTo(position)
    }

    fun toggleLooping() {
        // MediaPlayer.setLooping() is invalid in the error state.
        if (isReleased || _playerState.value is DataState.Error) {
            return
        }
        val isLooping = !_isLooping.value
        mediaPlayer.isLooping = isLooping
        _isLooping.value = isLooping
    }

    private fun setPlaying(isPlaying: Boolean) {
        if (_isPlaying.value == isPlaying) {
            return
        }
        _isPlaying.value = isPlaying
        if (isPlaying) {
            becomingNoisyReceiver.register()
        } else {
            becomingNoisyReceiver.unregister()
        }
    }

    private fun requestAudioFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN ->
                if (resumeOnAudioFocusGain) {
                    resumeOnAudioFocusGain = false
                    play()
                }
            AudioManager.AUDIOFOCUS_LOSS -> {
                pause()
                // There won't be another AUDIOFOCUS_GAIN, and play() requests focus again.
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ->
                if (_isPlaying.value) {
                    pause()
                    resumeOnAudioFocusGain = true
                }
            // The system ducks for us since Android 8, and we simply keep playing before that.
        }
    }

    fun release() {
        if (isReleased) {
            return
        }
        isReleased = true
        coroutineScope.cancel()
        setPlaying(false)
        abandonAudioFocus()
        mediaPlayer.release()
    }
}
