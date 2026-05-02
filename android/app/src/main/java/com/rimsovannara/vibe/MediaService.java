package com.rimsovannara.vibe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;

public class MediaService extends Service {
    private static final String CHANNEL_ID = "vibe_media_channel";
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_UPDATE_METADATA = "com.rimsovannara.vibe.UPDATE_METADATA";
    public static final String ACTION_UPDATE_STATE = "com.rimsovannara.vibe.UPDATE_STATE";
    
    public static final String ACTION_PLAY = "com.rimsovannara.vibe.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.rimsovannara.vibe.ACTION_PAUSE";
    public static final String ACTION_NEXT = "com.rimsovannara.vibe.ACTION_NEXT";
    public static final String ACTION_PREV = "com.rimsovannara.vibe.ACTION_PREV";

    private MediaSession mediaSession;
    private String currentTitle = "Vibe";
    private String currentArtist = "Loading...";
    private boolean isPlaying = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        
        mediaSession = new MediaSession(this, "VibeMediaSession");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                Intent b = new Intent(ACTION_PLAY);
                b.setPackage(getPackageName());
                sendBroadcast(b);
            }

            @Override
            public void onPause() {
                Intent b = new Intent(ACTION_PAUSE);
                b.setPackage(getPackageName());
                sendBroadcast(b);
            }

            @Override
            public void onSkipToNext() {
                Intent b = new Intent(ACTION_NEXT);
                b.setPackage(getPackageName());
                sendBroadcast(b);
            }

            @Override
            public void onSkipToPrevious() {
                Intent b = new Intent(ACTION_PREV);
                b.setPackage(getPackageName());
                sendBroadcast(b);
            }
        });
        mediaSession.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_UPDATE_METADATA:
                    currentTitle = intent.getStringExtra("title");
                    currentArtist = intent.getStringExtra("artist");
                    updateMetadata();
                    updateNotification();
                    break;
                case ACTION_UPDATE_STATE:
                    isPlaying = intent.getBooleanExtra("isPlaying", false);
                    updatePlaybackState();
                    updateNotification();
                    break;
                case ACTION_PLAY:
                case ACTION_PAUSE:
                case ACTION_NEXT:
                case ACTION_PREV:
                    // If the notification button pending intents bounce back here, forward them as explicit broadcast
                    Intent b = new Intent(intent.getAction());
                    b.setPackage(getPackageName());
                    sendBroadcast(b);
                    break;
            }
        }
        return START_STICKY;
    }

    private void updateMetadata() {
        MediaMetadata.Builder builder = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, currentArtist);
                
        // Try to load icon from mipmap to use as album art in notification
        try {
            int iconRes = getResources().getIdentifier("ic_launcher", "mipmap", getPackageName());
            if (iconRes != 0) {
                builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, BitmapFactory.decodeResource(getResources(), iconRes));
            }
        } catch (Exception ignored) {}
        
        mediaSession.setMetadata(builder.build());
    }

    private void updatePlaybackState() {
        PlaybackState.Builder builder = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(isPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f);
        mediaSession.setPlaybackState(builder.build());
    }

    private void updateNotification() {
        Notification.Builder builder = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Intent openAppIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE);

        builder.setContentTitle(currentTitle)
                .setContentText(currentArtist)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(contentIntent)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(isPlaying);

        try {
            int iconRes = getResources().getIdentifier("ic_launcher", "mipmap", getPackageName());
            if (iconRes != 0) {
                builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), iconRes));
            }
        } catch (Exception ignored) {}

        // Add actions
        builder.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_media_previous, "Previous",
                getPendingIntent(ACTION_PREV)).build());

        if (isPlaying) {
            builder.addAction(new Notification.Action.Builder(
                    android.R.drawable.ic_media_pause, "Pause",
                    getPendingIntent(ACTION_PAUSE)).build());
        } else {
            builder.addAction(new Notification.Action.Builder(
                    android.R.drawable.ic_media_play, "Play",
                    getPendingIntent(ACTION_PLAY)).build());
        }

        builder.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_media_next, "Next",
                getPendingIntent(ACTION_NEXT)).build());

        builder.setStyle(new Notification.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setMediaSession(mediaSession.getSessionToken()));

        Notification notification = builder.build();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        
        if (!isPlaying) {
            stopForeground(false);
        }
    }

    private PendingIntent getPendingIntent(String action) {
        Intent intent = new Intent(this, MediaService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Media Playback",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows media controls for Vibe");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaSession != null) {
            mediaSession.release();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
