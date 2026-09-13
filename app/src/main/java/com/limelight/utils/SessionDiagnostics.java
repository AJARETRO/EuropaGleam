package com.limelight.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.LimeLog;
import com.limelight.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Diagnostic logger and manager for stream connections and disconnect reasons.
 * Records timestamped network, stream, and lifecycle events, persists them to SharedPreferences,
 * and provides rich user-facing diagnostics dialogs with one-tap clipboard export.
 */
public class SessionDiagnostics {
    private static final String PREFS_NAME = "EuropaGleamDiagnostics";
    private static final String KEY_LAST_REASON = "last_disconnect_reason";
    private static final String KEY_LAST_DETAIL = "last_disconnect_detail";
    private static final String KEY_LAST_TIMESTAMP = "last_disconnect_timestamp";
    private static final String KEY_LAST_FULL_LOG = "last_full_log";

    private static final int MAX_LOG_ENTRIES = 150;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private static final SimpleDateFormat FULL_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    private static final SessionDiagnostics instance = new SessionDiagnostics();

    private final List<String> memoryLogs = new ArrayList<>();
    private String lastDisconnectReason = "";
    private String lastDisconnectDetail = "";
    private String lastDisconnectTimestamp = "";

    public static SessionDiagnostics getInstance() {
        return instance;
    }

    private SessionDiagnostics() {}

    public synchronized void recordEvent(String tag, String message) {
        String timestamp = TIME_FORMAT.format(new Date());
        String line = "[" + timestamp + "] [" + tag + "] " + message;
        if (memoryLogs.size() >= MAX_LOG_ENTRIES) {
            memoryLogs.remove(0);
        }
        memoryLogs.add(line);
        LimeLog.info("[Diagnostics] " + tag + ": " + message);
    }

    public synchronized void onSessionStart(Context context, String host, String appName, int width, int height, int fps) {
        memoryLogs.clear();
        String startTime = FULL_DATE_FORMAT.format(new Date());
        recordEvent("SESSION_START", "Host: " + host + " | App: " + appName +
                " | Target: " + width + "x" + height + " @" + fps + "fps | Started: " + startTime);

        if (context != null) {
            String netType = NetHelper.isWifiOrEthernet(context) ? "Wi-Fi / Ethernet" :
                    (NetHelper.isCellularNetwork(context) ? "Cellular Mobile Data" : "Unknown");
            boolean vpn = NetHelper.isActiveNetworkVpn(context);
            recordEvent("NETWORK_INIT", "Active Transport: " + netType + " | VPN Active: " + vpn);
        }
    }

    public synchronized void onNetworkChange(String changeDetails) {
        recordEvent("NETWORK_CHANGE", changeDetails);
    }

    public synchronized void onKeepalive(String type, int intervalSec) {
        recordEvent("KEEPALIVE", "Pulse sent (" + type + ", interval: ~" + intervalSec + "s)");
    }

    public synchronized void onDisconnected(Context context, String humanReason, int errorCode, String technicalDetail) {
        this.lastDisconnectReason = humanReason;
        this.lastDisconnectDetail = (technicalDetail != null ? technicalDetail : "") +
                (errorCode != 0 ? " (Code: " + errorCode + ")" : "");
        this.lastDisconnectTimestamp = FULL_DATE_FORMAT.format(new Date());

        recordEvent("DISCONNECT", humanReason + (technicalDetail != null && !technicalDetail.isEmpty() ? " -> " + technicalDetail : ""));
        saveToPreferences(context);
    }

    public synchronized String getLastDisconnectReason() {
        return (lastDisconnectReason != null && !lastDisconnectReason.isEmpty()) ?
                lastDisconnectReason : "No recent disconnect recorded.";
    }

    public synchronized String getLastDisconnectDetail() {
        return lastDisconnectDetail;
    }

    public synchronized String getFullFormattedLog() {
        if (memoryLogs.isEmpty()) {
            return "No diagnostic events recorded in current session.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("=== EuropaGleam Stream Diagnostic Log ===\n");
        sb.append("Generated: ").append(FULL_DATE_FORMAT.format(new Date())).append("\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
                .append(" (Android API ").append(Build.VERSION.SDK_INT).append(")\n");
        if (!lastDisconnectReason.isEmpty()) {
            sb.append("Last Disconnect Cause: ").append(lastDisconnectReason).append("\n");
            if (!lastDisconnectDetail.isEmpty()) {
                sb.append("Technical Details: ").append(lastDisconnectDetail).append("\n");
            }
        }
        sb.append("-----------------------------------------\n");
        for (String entry : memoryLogs) {
            sb.append(entry).append("\n");
        }
        sb.append("=========================================");
        return sb.toString();
    }

    public synchronized void saveToPreferences(Context context) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .putString(KEY_LAST_REASON, lastDisconnectReason)
                    .putString(KEY_LAST_DETAIL, lastDisconnectDetail)
                    .putString(KEY_LAST_TIMESTAMP, lastDisconnectTimestamp)
                    .putString(KEY_LAST_FULL_LOG, getFullFormattedLog())
                    .apply();
        } catch (Throwable t) {
            LimeLog.warning("Failed to save diagnostics to prefs: " + t.getMessage());
        }
    }

    public synchronized void loadFromPreferences(Context context) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            lastDisconnectReason = prefs.getString(KEY_LAST_REASON, "");
            lastDisconnectDetail = prefs.getString(KEY_LAST_DETAIL, "");
            lastDisconnectTimestamp = prefs.getString(KEY_LAST_TIMESTAMP, "");
            String savedLog = prefs.getString(KEY_LAST_FULL_LOG, "");
            if (memoryLogs.isEmpty() && savedLog != null && !savedLog.isEmpty()) {
                String[] lines = savedLog.split("\n");
                for (String line : lines) {
                    if (!line.isEmpty()) {
                        memoryLogs.add(line);
                    }
                }
            }
        } catch (Throwable t) {
            LimeLog.warning("Failed to load diagnostics from prefs: " + t.getMessage());
        }
    }

    public static void copyLogToClipboard(Context context) {
        if (context == null) return;
        try {
            String log = getInstance().getFullFormattedLog();
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                ClipData clip = ClipData.newPlainText("EuropaGleam Diagnostics", log);
                cm.setPrimaryClip(clip);
                Toast.makeText(context, R.string.diagnostics_copied_toast, Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable t) {
            Toast.makeText(context, "Failed to copy log: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Renders a modern, sleek dark diagnostic dialog with log viewing and one-tap clipboard copy.
     */
    public static void showDiagnosticsDialog(final Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        getInstance().loadFromPreferences(activity);

        activity.runOnUiThread(() -> {
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(Color.parseColor("#0F141C"));
            int padding = (int) (16 * activity.getResources().getDisplayMetrics().density);
            root.setPadding(padding, padding, padding, padding);

            // Header Banner
            TextView reasonView = new TextView(activity);
            reasonView.setText("⚠️ " + getInstance().getLastDisconnectReason());
            reasonView.setTextColor(Color.parseColor("#00E5FF")); // Gleam cyan
            reasonView.setTextSize(14f);
            reasonView.setTypeface(Typeface.DEFAULT_BOLD);
            reasonView.setPadding(0, 0, 0, (int) (10 * activity.getResources().getDisplayMetrics().density));
            root.addView(reasonView);

            if (!getInstance().getLastDisconnectDetail().isEmpty()) {
                TextView detailView = new TextView(activity);
                detailView.setText(getInstance().getLastDisconnectDetail());
                detailView.setTextColor(Color.parseColor("#94A3B8"));
                detailView.setTextSize(12f);
                detailView.setPadding(0, 0, 0, (int) (12 * activity.getResources().getDisplayMetrics().density));
                root.addView(detailView);
            }

            // Monospace Log Container
            ScrollView scrollView = new ScrollView(activity);
            LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (260 * activity.getResources().getDisplayMetrics().density)
            );
            scrollView.setLayoutParams(scrollParams);
            scrollView.setBackgroundColor(Color.parseColor("#080B10"));
            scrollView.setPadding(padding / 2, padding / 2, padding / 2, padding / 2);

            TextView logView = new TextView(activity);
            logView.setText(getInstance().getFullFormattedLog());
            logView.setTextColor(Color.parseColor("#CBD5E1"));
            logView.setTextSize(11f);
            logView.setTypeface(Typeface.MONOSPACE);
            scrollView.addView(logView);

            root.addView(scrollView);

            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.diagnostics_dialog_title))
                    .setView(root)
                    .setPositiveButton(R.string.diagnostics_copy_button, (d, w) -> copyLogToClipboard(activity))
                    .setNegativeButton(R.string.game_menu_cancel, null)
                    .create();

            dialog.show();
        });
    }
}
