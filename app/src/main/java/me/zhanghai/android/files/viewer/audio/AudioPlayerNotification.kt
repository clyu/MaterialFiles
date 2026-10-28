package me.zhanghai.android.files.viewer.audio

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.NotificationIds
import me.zhanghai.android.files.compat.stopForegroundCompat
import me.zhanghai.android.files.util.NotificationChannelTemplate
import me.zhanghai.android.files.util.NotificationTemplate
import me.zhanghai.android.files.util.createIntent

val audioPlayerNotificationTemplate =
    NotificationTemplate(
        NotificationChannelTemplate(
            "audio_player",
            R.string.notification_channel_audio_player_name,
            NotificationManagerCompat.IMPORTANCE_LOW,
            descriptionRes = R.string.notification_channel_audio_player_description,
            showBadge = false
        ),
        colorRes = R.color.color_primary,
        smallIcon = R.drawable.notification_icon,
        ongoing = true,
        onlyAlertOnce = true,
        category = NotificationCompat.CATEGORY_TRANSPORT,
        priority = NotificationCompat.PRIORITY_LOW
    )

class AudioPlayerNotification(private val service: Service) {
    fun startForeground(player: AudioPlayer, isPlaying: Boolean) {
        val contentIntent = AudioPlayerActivity::class.createIntent()
            .setData(player.uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentPendingIntent = PendingIntent.getActivity(
            service, AudioPlayerActivity::class.hashCode(), contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val togglePlayPausePendingIntent = PendingIntent.getService(
            service, AudioPlayerService::class.hashCode(),
            AudioPlayerService.createTogglePlayPauseIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPendingIntent = PendingIntent.getService(
            service, AudioPlayerService::class.hashCode(), AudioPlayerService.createStopIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = audioPlayerNotificationTemplate.createBuilder(service)
            .setContentTitle(player.title)
            .setContentIntent(contentPendingIntent)
            // Allow controlling playback from the lock screen.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .apply {
                if (isPlaying) {
                    addAction(
                        R.drawable.pause_icon_white_24dp,
                        service.getString(R.string.audio_player_pause),
                        togglePlayPausePendingIntent
                    )
                } else {
                    addAction(
                        R.drawable.play_icon_white_24dp,
                        service.getString(R.string.audio_player_play),
                        togglePlayPausePendingIntent
                    )
                }
            }
            .addAction(
                R.drawable.stop_icon_white_24dp, service.getString(R.string.stop), stopPendingIntent
            )
            .build()
        service.startForeground(NotificationIds.AUDIO_PLAYER, notification)
    }

    fun stopForeground() {
        service.stopForegroundCompat(ServiceCompat.STOP_FOREGROUND_REMOVE)
    }
}
