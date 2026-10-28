package me.zhanghai.android.files.viewer.audio

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.AudioPlayerFragmentBinding
import me.zhanghai.android.files.util.DataState
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.fadeInUnsafe
import me.zhanghai.android.files.util.fadeOutUnsafe
import me.zhanghai.android.files.util.finish

class AudioPlayerFragment : Fragment() {
    private val args by args<Args>()

    private lateinit var binding: AudioPlayerFragmentBinding

    private var player: AudioPlayer? = null

    private var isSeeking = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        AudioPlayerFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val argsUri = args.intent.data
        if (argsUri == null) {
            // TODO: Show a toast.
            finish()
            return
        }

        val activity = requireActivity() as AppCompatActivity
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.CREATED) {
                activity.setSupportActionBar(binding.toolbar)
                activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            }
        }

        if (savedInstanceState == null) {
            if (!AudioPlayerService.isOpen(argsUri)) {
                val title = args.intent.extraPath?.fileName?.toString()
                    ?: getString(R.string.audio_player_title)
                AudioPlayerService.open(argsUri, title, requireContext())
            }
        } else if (AudioPlayerService.player.value?.uri != argsUri) {
            // Playback was stopped while we were in background.
            finish()
            return
        }

        binding.loopButton.setOnClickListener { player?.toggleLooping() }
        binding.playPauseButton.setOnClickListener { player?.togglePlayPause() }
        binding.stopButton.setOnClickListener { AudioPlayerService.stop(requireContext()) }
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.positionText.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isSeeking = false
                player?.seekTo(seekBar.progress)
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AudioPlayerService.player.collectLatest { player ->
                    if (player == null || player.uri != argsUri) {
                        // Playback was stopped, or another file is being played instead.
                        this@AudioPlayerFragment.player = null
                        finish()
                        return@collectLatest
                    }
                    this@AudioPlayerFragment.player = player
                    requireActivity().title = player.title
                    coroutineScope {
                        launch { player.playerState.collect { onPlayerStateChanged(player, it) } }
                        launch { player.isPlaying.collect { onIsPlayingChanged(it) } }
                        launch { player.isLooping.collect { onIsLoopingChanged(it) } }
                        launch {
                            while (true) {
                                updatePosition(player)
                                delay(POSITION_UPDATE_INTERVAL_MILLIS)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onPlayerStateChanged(player: AudioPlayer, state: DataState<Unit>) {
        when (state) {
            is DataState.Loading -> {
                binding.progress.fadeInUnsafe()
                binding.errorText.fadeOutUnsafe()
                binding.playerLayout.fadeOutUnsafe()
            }
            is DataState.Success -> {
                binding.progress.fadeOutUnsafe()
                binding.errorText.fadeOutUnsafe()
                binding.playerLayout.fadeInUnsafe()
                val duration = player.duration
                binding.seekBar.max = duration
                binding.durationText.text = formatTime(duration)
                updatePosition(player)
            }
            is DataState.Error -> {
                state.throwable.printStackTrace()
                binding.progress.fadeOutUnsafe()
                binding.errorText.fadeInUnsafe()
                binding.playerLayout.fadeOutUnsafe()
            }
        }
    }

    private fun onIsPlayingChanged(isPlaying: Boolean) {
        binding.playPauseButton.setImageResource(
            if (isPlaying) R.drawable.pause_icon_white_24dp else R.drawable.play_icon_white_24dp
        )
        binding.playPauseButton.contentDescription = getString(
            if (isPlaying) R.string.audio_player_pause else R.string.audio_player_play
        )
    }

    private fun onIsLoopingChanged(isLooping: Boolean) {
        // Selected state also tints the button and is announced by accessibility services.
        binding.loopButton.isSelected = isLooping
    }

    private fun updatePosition(player: AudioPlayer) {
        if (isSeeking) {
            return
        }
        val position = player.currentPosition
        binding.seekBar.progress = position
        binding.positionText.text = formatTime(position)
    }

    private fun formatTime(millis: Int): String = DateUtils.formatElapsedTime(millis / 1000L)

    @Parcelize
    class Args(val intent: Intent) : ParcelableArgs

    companion object {
        private const val POSITION_UPDATE_INTERVAL_MILLIS = 200L
    }
}
