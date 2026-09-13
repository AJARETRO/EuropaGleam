package com.limelight.utils;

import android.os.Handler;
import android.os.Looper;

import com.limelight.LimeLog;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.Random;

/**
 * Manages sending periodic F15 dummy keypresses to keep the host PC session
 * active and prevent sleep, screen saver, or idle disconnects.
 * Interval: 1 min ± 30s (randomized between 30 and 90 seconds).
 */
public class KeepaliveManager {

    // VK_F15 = 0x7E (126). Moonlight key prefix = 0x80.
    private static final short VK_F15_KEYMAP = (short) ((0x80 << 8) | 0x7E);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private NvConnection connection;
    private PreferenceConfiguration prefConfig;
    private boolean running = false;
    private boolean inBackground = false;

    private final Runnable keepaliveRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running || connection == null) {
                return;
            }

            int mode = prefConfig != null ? prefConfig.keepaliveF15Mode : PreferenceConfiguration.KEEPALIVE_ALWAYS;

            boolean shouldSend = false;
            if (mode == PreferenceConfiguration.KEEPALIVE_ALWAYS) {
                shouldSend = true;
            } else if (mode == PreferenceConfiguration.KEEPALIVE_BACKGROUND_ONLY && inBackground) {
                shouldSend = true;
            }

            if (shouldSend) {
                sendF15Keypress();
            }

            scheduleNext();
        }
    };

    public KeepaliveManager(PreferenceConfiguration prefConfig) {
        this.prefConfig = prefConfig;
    }

    public synchronized void setConnection(NvConnection conn) {
        this.connection = conn;
    }

    public synchronized void updatePreferences(PreferenceConfiguration prefConfig) {
        this.prefConfig = prefConfig;
    }

    public synchronized void setInBackground(boolean inBackground) {
        this.inBackground = inBackground;
    }

    public synchronized void start(NvConnection conn) {
        this.connection = conn;
        if (running) {
            return;
        }
        running = true;
        scheduleNext();
        LimeLog.info("KeepaliveManager started (F15 anti-timeout active)");
    }

    public synchronized void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        LimeLog.info("KeepaliveManager stopped");
    }

    private void scheduleNext() {
        if (!running) {
            return;
        }
        // Every 1 minute ± 30 seconds: 30,000ms to 90,000ms
        long delayMs = 30_000L + random.nextInt(60_001);
        handler.postDelayed(keepaliveRunnable, delayMs);
    }

    private void sendF15Keypress() {
        NvConnection conn = this.connection;
        if (conn == null) {
            return;
        }

        try {
            // Send F15 Key Down
            conn.sendKeyboardInput(VK_F15_KEYMAP, KeyboardPacket.KEY_DOWN, (byte) 0, (byte) 0);

            // Send F15 Key Up after 50ms pulse
            handler.postDelayed(() -> {
                NvConnection c = connection;
                if (c != null && running) {
                    try {
                        c.sendKeyboardInput(VK_F15_KEYMAP, KeyboardPacket.KEY_UP, (byte) 0, (byte) 0);
                    } catch (Throwable ignored) {}
                }
            }, 50);

            LimeLog.info("F15 keepalive sent to host PC (inBackground=" + inBackground + ")");
        } catch (Throwable t) {
            LimeLog.warning("Failed to send F15 keepalive: " + t.getMessage());
        }
    }
}
