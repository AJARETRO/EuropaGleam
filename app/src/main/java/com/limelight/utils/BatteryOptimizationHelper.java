package com.limelight.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;

import com.limelight.LimeLog;
import com.limelight.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects system battery saver mode, Android Doze / app standby optimizations,
 * and manufacturer background restrictions (Vivo/Funtouch OS, Xiaomi, Samsung, etc.).
 */
public class BatteryOptimizationHelper {

    public static class BatteryStatus {
        public final boolean isPowerSaveMode;
        public final boolean isBatteryOptimized;
        public final boolean isBackgroundRestricted;

        public BatteryStatus(boolean isPowerSaveMode, boolean isBatteryOptimized, boolean isBackgroundRestricted) {
            this.isPowerSaveMode = isPowerSaveMode;
            this.isBatteryOptimized = isBatteryOptimized;
            this.isBackgroundRestricted = isBackgroundRestricted;
        }

        public boolean hasAnyRestriction() {
            return isPowerSaveMode || isBatteryOptimized || isBackgroundRestricted;
        }

        public String getShortStatusText(Context context) {
            if (!hasAnyRestriction()) {
                return context.getString(R.string.battery_status_unrestricted);
            }
            List<String> active = new ArrayList<>();
            if (isPowerSaveMode) {
                active.add(context.getString(R.string.battery_status_powersave_on));
            }
            if (isBackgroundRestricted) {
                active.add(context.getString(R.string.battery_status_background_restricted));
            } else if (isBatteryOptimized) {
                active.add(context.getString(R.string.battery_status_optimized));
            }
            return TextUtils.join(" • ", active) + " ⚠️";
        }

        public String getDetailedMessage(Context context) {
            StringBuilder sb = new StringBuilder();
            if (isPowerSaveMode) {
                sb.append("• ").append(context.getString(R.string.battery_status_powersave_on))
                        .append(": System battery saver throttles CPU, network, and background service execution.\n");
            }
            if (isBackgroundRestricted) {
                sb.append("• ").append(context.getString(R.string.battery_status_background_restricted))
                        .append(": App is placed in restricted background execution bucket.\n");
            } else if (isBatteryOptimized) {
                sb.append("• ").append(context.getString(R.string.battery_status_optimized))
                        .append(": Android Doze may suspend stream network packets when screen is off or app is minimized.\n");
            }
            sb.append("\n").append(context.getString(R.string.battery_dialog_recommendation));
            return sb.toString();
        }
    }

    public static boolean isPowerSaveMode(Context context) {
        if (context == null) {
            return false;
        }
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isPowerSaveMode();
    }

    public static boolean isBatteryOptimizationActive(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null && !pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    public static boolean isBackgroundRestricted(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false;
        }
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        return am != null && am.isBackgroundRestricted();
    }

    public static boolean hasAnyBatteryRestriction(Context context) {
        return isPowerSaveMode(context) || isBatteryOptimizationActive(context) || isBackgroundRestricted(context);
    }

    public static BatteryStatus getBatteryStatus(Context context) {
        return new BatteryStatus(
                isPowerSaveMode(context),
                isBatteryOptimizationActive(context),
                isBackgroundRestricted(context)
        );
    }

    public static void openBatterySettings(Context context) {
        if (context == null) {
            return;
        }

        // 1. Try opening direct battery optimization settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            } catch (Exception e) {
                LimeLog.info("Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS not supported: " + e.getMessage());
            }
        }

        // 2. Fallback to App details settings
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            LimeLog.warning("Failed to open application details settings: " + e.getMessage());
        }
    }

    public static void openPowerSaverSettings(Context context) {
        if (context == null) {
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            openBatterySettings(context);
        }
    }
}
