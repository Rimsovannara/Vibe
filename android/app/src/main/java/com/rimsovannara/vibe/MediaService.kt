package com.rimsovannara.vibe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder

class MediaService : Service() {

    private lateinit var mediaSession: MediaSession
    private var currentTitle = "Vibe"
    private var currentArtist = "Loading..."
    private var isPlaying = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        mediaSession = MediaSession(this, "VibeMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    sendBroadcast(Intent(ACTION_PLAY).setPackage(packageName))
                }

                override fun onPause() {
                    sendBroadcast(Intent(ACTION_PAUSE).setPackage(packageName))
                }

                override fun onSkipToNext() {
                    sendBroadcast(Intent(ACTION_NEXT).setPackage(packageName))
                }

                override fun onSkipToPrevious() {
                    sendBroadcast(Intent(ACTION_PREV).setPackage(packageName))
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_UPDATE_METADATA -> {
                    currentTitle = intent.getStringExtra("title") ?: currentTitle
                    currentArtist = intent.getStringExtra("artist") ?: currentArtist
                    updateMetadata()
                    updateNotification()
                }
                ACTION_UPDATE_STATE -> {
                    isPlaying = intent.getBooleanExtra("isPlaying", false)
                    updatePlaybackState()
                    updateNotification()
                }
                ACTION_PLAY, ACTION_PAUSE, ACTION_NEXT, ACTION_PREV -> {
                    // Forward bouncing service intents as explicit broadcasts
                    sendBroadcast(Intent(action).setPackage(packageName))
                }
            }
        }
        return START_STICKY
    }

    private fun updateMetadata() {
        val builder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, currentArtist)

        try {
            val iconRes = resources.getIdentifier("ic_launcher", "mipmap", packageName)
            if (iconRes != 0) {
                builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, BitmapFactory.decodeResource(resources, iconRes))
            }
        } catch (ignored: Exception) {}

        mediaSession.setMetadata(builder.build())
    }

    private fun updatePlaybackState() {
        val builder = PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
            .setState(
                if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                if (isPlaying) 1.0f else 0.0f
            )
        mediaSession.setPlaybackState(builder.build())
    }

    private fun updateNotification() {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE)

        builder.setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)

        try {
            val iconRes = resources.getIdentifier("ic_launcher", "mipmap", packageName)
            if (iconRes != 0) {
                builder.setLargeIcon(BitmapFactory.decodeResource(resources, iconRes))
            }
        } catch (ignored: Exception) {}

        builder.addAction(Notification.Action.Builder(
            android.R.drawable.ic_media_previous, "Previous",
            getPendingIntent(ACTION_PREV)
        ).build())

        if (isPlaying) {
            builder.addAction(Notification.Action.Builder(
                android.R.drawable.ic_media_pause, "Pause",
                getPendingIntent(ACTION_PAUSE)
            ).build())
        } else {
            builder.addAction(Notification.Action.Builder(
                android.R.drawable.ic_media_play, "Play",
                getPendingIntent(ACTION_PLAY)
            ).build())
        }

        builder.addAction(Notification.Action.Builder(
            android.R.drawable.ic_media_next, "Next",
            getPendingIntent(ACTION_NEXT)
        ).build())

        builder.style = Notification.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
            .setMediaSession(mediaSession.sessionToken)

        val notification = builder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!isPlaying) {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
    }

    private fun getPendingIntent(action: String): PendingIntent {
        val intent = Intent(action).apply { `package` = packageName }
        return PendingIntent.getBroadcast(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows media controls for Vibe"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "vibe_media_channel"
        private const val NOTIFICATION_ID = 1

        const val ACTION_UPDATE_METADATA = "com.rimsovannara.vibe.UPDATE_METADATA"
        const val ACTION_UPDATE_STATE = "com.rimsovannara.vibe.UPDATE_STATE"
        
        const val ACTION_PLAY = "com.rimsovannara.vibe.ACTION_PLAY"
        const val ACTION_PAUSE = "com.rimsovannara.vibe.ACTION_PAUSE"
        const val ACTION_NEXT = "com.rimsovannara.vibe.ACTION_NEXT"
        const val ACTION_PREV = "com.rimsovannara.vibe.ACTION_PREV"
    }
}
