package com.limelight.utils;

import android.os.Handler;
import android.os.Looper;

import com.limelight.LimeLog;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.Random;

/**
 * Manages sending periodic Scroll Lock dummy keypresses to keep the host PC session
 * active and prevent sleep, screen saver, or idle disconnects.
 * Interval: 1 min ± 30s (randomized between 30 and 90 seconds).
 * Uses a double-tap pulse so the host's Scroll Lock state remains unchanged.
 */
public class KeepaliveManager {

    // VK_SCROLL = 0x91 (145). Moonlight key prefix = 0x80.
    private static final short VK_SCROLL_KEYMAP = (short) ((0x80 << 8) | KeyMapper.VK_SCROLL);

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
                sendScrollLockPulse();
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
        LimeLog.info("KeepaliveManager started (Scroll Lock anti-timeout active)");
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

    /**
     * Sends a double-tap pulse of Scroll Lock (Down -> Up -> Down -> Up).
     * This registers user activity to prevent host sleep/screensaver,
     * while immediately reverting Scroll Lock state so LEDs and apps are unaffected.
     */
    private void sendScrollLockPulse() {
        NvConnection conn = this.connection;
        if (conn == null) {
            return;
        }

        try {
            // Pulse 1: Down
            conn.sendKeyboardInput(VK_SCROLL_KEYMAP, KeyboardPacket.KEY_DOWN, (byte) 0, (byte) 0);

            handler.postDelayed(() -> {
                NvConnection c1 = connection;
                if (c1 != null && running) {
                    try {
                        // Pulse 1: Up
                        c1.sendKeyboardInput(VK_SCROLL_KEYMAP, KeyboardPacket.KEY_UP, (byte) 0, (byte) 0);
                    } catch (Throwable ignored) {}

                    // Pulse 2: Down (reverts toggle)
                    handler.postDelayed(() -> {
                        NvConnection c2 = connection;
                        if (c2 != null && running) {
                            try {
                                c2.sendKeyboardInput(VK_SCROLL_KEYMAP, KeyboardPacket.KEY_DOWN, (byte) 0, (byte) 0);
                            } catch (Throwable ignored) {}

                            // Pulse 2: Up
                            handler.postDelayed(() -> {
                                NvConnection c3 = connection;
                                if (c3 != null && running) {
                                    try {
                                        c3.sendKeyboardInput(VK_SCROLL_KEYMAP, KeyboardPacket.KEY_UP, (byte) 0, (byte) 0);
                                    } catch (Throwable ignored) {}
                                }
                            }, 30);
                        }
                    }, 30);
                }
            }, 30);

            LimeLog.info("Scroll Lock keepalive sent to host PC (inBackground=" + inBackground + ")");
        } catch (Throwable t) {
            LimeLog.warning("Failed to send Scroll Lock keepalive: " + t.getMessage());
        }
    }
}
