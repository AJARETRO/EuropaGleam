package com.limelight;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

/**
 * Discreet foreground service to ensure Android and Funtouch OS / Vivo battery managers
 * do not kill EuropaGleam while streaming in the background.
 */
public class StreamKeepaliveService extends Service {

    public static final String ACTION_START = "com.limelight.START_BACKGROUND_STREAM";
    public static final String ACTION_STOP = "com.limelight.STOP_BACKGROUND_STREAM";
    public static final String ACTION_DISCONNECT = "com.limelight.DISCONNECT_BACKGROUND_STREAM";

    private static final String CHANNEL_ID = "europagleam_bg_stream";
    private static final int NOTIFICATION_ID = 90210;

    public static void start(Context context) {
        Intent intent = new Intent(context, StreamKeepaliveService.class);
        intent.setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, StreamKeepaliveService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification());
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_background_streaming),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.notification_background_streaming_text));
            channel.setShowBadge(false);
            channel.enableLights(false);
            channel.enableVibration(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        // PendingIntent to bring Game back to foreground when notification is tapped
        Intent openAppIntent = new Intent(this, Game.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        // PendingIntent for Disconnect action button
        Intent disconnectIntent = new Intent(this, Game.class);
        disconnectIntent.setAction(ACTION_DISCONNECT);
        disconnectIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent disconnectPendingIntent = PendingIntent.getActivity(
                this,
                1,
                disconnectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_background_streaming_title))
                .setContentText(getString(R.string.notification_background_streaming_text))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(contentPendingIntent)
                .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.notification_action_disconnect),
                        disconnectPendingIntent
                )
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
