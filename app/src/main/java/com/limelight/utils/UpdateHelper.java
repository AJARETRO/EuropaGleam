package com.limelight.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.limelight.BuildConfig;
import com.limelight.LimeLog;
import com.limelight.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Intelligent in-app auto-updater for EuropaGleam.
 * Queries GitHub Releases API, selects optimal per-architecture APK (e.g. arm64-v8a),
 * downloads with real-time progress, and launches package installer via FileProvider.
 */
public class UpdateHelper {
    private static final String GITHUB_LATEST_RELEASE_API =
            "https://api.github.com/repos/AJARETRO/EuropaGleam/releases/latest";
    private static final String GITHUB_RELEASES_PAGE =
            "https://github.com/AJARETRO/EuropaGleam/releases";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static class ReleaseInfo {
        public String tagName;
        public String name;
        public String changelog;
        public String downloadUrl;
        public String assetName;
        public long assetSize;
    }

    public static void checkForUpdates(final Activity activity, final boolean userInitiated) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        if (userInitiated) {
            Toast.makeText(activity, R.string.checking_for_updates, Toast.LENGTH_SHORT).show();
        }

        executor.execute(() -> {
            try {
                URL url = new URL(GITHUB_LATEST_RELEASE_API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "EuropaGleam-App");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new Exception("HTTP " + responseCode);
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                conn.disconnect();

                JSONObject json = new JSONObject(response.toString());
                String tagName = json.optString("tag_name", "");
                String releaseName = json.optString("name", tagName);
                String body = json.optString("body", "");
                JSONArray assets = json.optJSONArray("assets");

                ReleaseInfo releaseInfo = new ReleaseInfo();
                releaseInfo.tagName = tagName;
                releaseInfo.name = releaseName;
                releaseInfo.changelog = body;

                // Pick optimal asset based on device ABI
                pickBestAsset(releaseInfo, assets);

                mainHandler.post(() -> {
                    if (activity.isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed())) {
                        return;
                    }

                    if (isNewerVersion(tagName, getCurrentAppVersion())) {
                        showUpdateAvailableDialog(activity, releaseInfo);
                    } else if (userInitiated) {
                        Toast.makeText(activity,
                                activity.getString(R.string.already_latest_version, getCurrentAppVersion()),
                                Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                LimeLog.warning("Update check failed: " + e.getMessage());
                if (userInitiated) {
                    mainHandler.post(() -> {
                        if (activity.isFinishing()) return;
                        Toast.makeText(activity,
                                R.string.update_check_failed,
                                Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    public static String getCurrentAppVersion() {
        return "v" + BuildConfig.VERSION_NAME;
    }

    public static boolean isNewerVersion(String latestTag, String currentVersion) {
        if (latestTag == null || latestTag.isEmpty()) return false;
        String cleanLatest = latestTag.replaceAll("^[vV]", "").trim();
        String cleanCurrent = currentVersion.replaceAll("^[vV]", "").trim();

        if (cleanLatest.equalsIgnoreCase(cleanCurrent)) {
            return false;
        }

        // Compare version tokens
        String[] latestParts = cleanLatest.split("[-._]");
        String[] currentParts = cleanCurrent.split("[-._]");

        int len = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < len; i++) {
            String lPart = i < latestParts.length ? latestParts[i] : "0";
            String cPart = i < currentParts.length ? currentParts[i] : "0";

            try {
                int lNum = Integer.parseInt(lPart.replaceAll("\\D", ""));
                int cNum = Integer.parseInt(cPart.replaceAll("\\D", ""));
                if (lNum > cNum) return true;
                if (lNum < cNum) return false;
            } catch (Exception ignored) {
                int cmp = lPart.compareToIgnoreCase(cPart);
                if (cmp > 0) return true;
                if (cmp < 0) return false;
            }
        }
        return false;
    }

    private static void pickBestAsset(ReleaseInfo info, JSONArray assets) {
        if (assets == null || assets.length() == 0) return;

        boolean isRoot = BuildConfig.ROOT_BUILD;
        String[] supportedAbis = Build.SUPPORTED_ABIS;
        String primaryAbi = (supportedAbis != null && supportedAbis.length > 0) ? supportedAbis[0] : "arm64-v8a";

        // 1. Exact match: flavor (root/nonRoot) + primary ABI (e.g. arm64-v8a)
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            String name = asset.optString("name", "").toLowerCase();
            if (!name.endsWith(".apk")) continue;

            if (isRoot && !name.contains("root")) continue;
            if (!isRoot && name.contains("root") && !name.contains("nonroot")) continue;

            if (name.contains(primaryAbi.toLowerCase())) {
                info.downloadUrl = asset.optString("browser_download_url");
                info.assetName = asset.optString("name");
                info.assetSize = asset.optLong("size");
                return;
            }
        }

        // 2. Universal match
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            String name = asset.optString("name", "").toLowerCase();
            if (!name.endsWith(".apk")) continue;

            if (isRoot && !name.contains("root")) continue;
            if (!isRoot && name.contains("root") && !name.contains("nonroot")) continue;

            if (name.contains("universal")) {
                info.downloadUrl = asset.optString("browser_download_url");
                info.assetName = asset.optString("name");
                info.assetSize = asset.optLong("size");
                return;
            }
        }

        // 3. Fallback to first matching .apk
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            String name = asset.optString("name", "").toLowerCase();
            if (name.endsWith(".apk")) {
                info.downloadUrl = asset.optString("browser_download_url");
                info.assetName = asset.optString("name");
                info.assetSize = asset.optLong("size");
                return;
            }
        }
    }

    private static void showUpdateAvailableDialog(final Activity activity, final ReleaseInfo releaseInfo) {
        String title = activity.getString(R.string.update_available_title, releaseInfo.tagName);
        String sizeInfo = releaseInfo.assetSize > 0 ?
                "\n\nDownload Size: ~" + (releaseInfo.assetSize / (1024 * 1024)) + " MB" : "";
        String message = releaseInfo.name + "\n\n" +
                (releaseInfo.changelog != null && !releaseInfo.changelog.isEmpty() ? releaseInfo.changelog : "") +
                sizeInfo;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(title);
        builder.setMessage(message);

        if (releaseInfo.downloadUrl != null && !releaseInfo.downloadUrl.isEmpty()) {
            builder.setPositiveButton(R.string.update_install_now, (dialog, which) -> {
                downloadAndInstallApk(activity, releaseInfo);
            });
        }

        builder.setNeutralButton(R.string.update_view_on_github, (dialog, which) -> {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_PAGE));
                activity.startActivity(browserIntent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        builder.setNegativeButton(R.string.game_menu_cancel, null);
        builder.show();
    }

    private static void downloadAndInstallApk(final Activity activity, final ReleaseInfo releaseInfo) {
        final ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle(R.string.update_downloading_title);
        progressDialog.setMessage(activity.getString(R.string.update_downloading_message, releaseInfo.assetName != null ? releaseInfo.assetName : "EuropaGleam.apk"));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.setMax(100);
        progressDialog.show();

        executor.execute(() -> {
            File tempApk = null;
            try {
                URL url = new URL(releaseInfo.downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.connect();

                int responseCode = conn.getResponseCode();
                // Follow redirects (e.g. HTTP 302 to GitHub AWS S3 bucket)
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        responseCode == 307 || responseCode == 308) {
                    String redirectUrl = conn.getHeaderField("Location");
                    conn.disconnect();
                    url = new URL(redirectUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.connect();
                }

                int fileLength = conn.getContentLength();
                File cacheDir = activity.getExternalCacheDir();
                if (cacheDir == null) {
                    cacheDir = activity.getCacheDir();
                }
                tempApk = new File(cacheDir, "EuropaGleam_update.apk");
                if (tempApk.exists()) {
                    tempApk.delete();
                }

                InputStream input = conn.getInputStream();
                FileOutputStream output = new FileOutputStream(tempApk);

                byte[] data = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        mainHandler.post(() -> progressDialog.setProgress(progress));
                    }
                    output.write(data, 0, count);
                }

                output.flush();
                output.close();
                input.close();
                conn.disconnect();

                final File finalApk = tempApk;
                mainHandler.post(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    installApk(activity, finalApk);
                });

            } catch (Exception e) {
                LimeLog.warning("Download failed: " + e.getMessage());
                mainHandler.post(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show();
                    try {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(releaseInfo.downloadUrl));
                        activity.startActivity(browserIntent);
                    } catch (Exception ignored) {}
                });
            }
        });
    }

    private static void installApk(Activity activity, File apkFile) {
        if (activity == null || apkFile == null || !apkFile.exists()) return;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.getPackageManager().canRequestPackageInstalls()) {
                    Toast.makeText(activity, R.string.update_grant_install_permission, Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(intent);
                    return;
                }
            }

            Uri apkUri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".fileprovider",
                    apkFile
            );

            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(installIntent);

        } catch (Exception e) {
            LimeLog.warning("Install failed: " + e.getMessage());
            Toast.makeText(activity, "Failed to launch installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
