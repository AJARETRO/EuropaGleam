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
    public static final String ACTION_ENTER_PIP = "com.limelight.ENTER_PIP_FROM_NOTIFICATION";

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

    public static void notifyMobileDataDisconnect(Context context) {
        if (context == null) return;
        try {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.notification_channel_background_streaming),
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                manager.createNotificationChannel(channel);
            }

            Intent openAppIntent = new Intent(context, PcView.class);
            openAppIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent contentPendingIntent = PendingIntent.getActivity(
                    context,
                    2,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle(context.getString(R.string.notification_disconnected_mobile_data_title))
                    .setContentText(context.getString(R.string.notification_disconnected_mobile_data_text))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setAutoCancel(true)
                    .setContentIntent(contentPendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_HIGH);

            manager.notify(NOTIFICATION_ID + 1, builder.build());
        } catch (Exception e) {
            e.printStackTrace();
        }
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

        // PendingIntent for Open PiP action button
        Intent pipIntent = new Intent(this, Game.class);
        pipIntent.setAction(ACTION_ENTER_PIP);
        pipIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pipPendingIntent = PendingIntent.getActivity(
                this,
                2,
                pipIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        String contentText = getString(R.string.notification_background_streaming_text);
        if (com.limelight.utils.BatteryOptimizationHelper.hasAnyBatteryRestriction(this)) {
            contentText += " " + getString(R.string.notification_battery_warning_suffix);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_background_streaming_title))
                .setContentText(contentText)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(contentPendingIntent)
                .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.notification_action_disconnect),
                        disconnectPendingIntent
                );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.addAction(
                    android.R.drawable.ic_menu_slideshow,
                    getString(R.string.notification_action_pip),
                    pipPendingIntent
            );
        }

        return builder.build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
