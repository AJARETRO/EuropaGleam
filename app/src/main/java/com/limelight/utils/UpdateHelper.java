package com.limelight.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

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
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .build();

                Request request = new Request.Builder()
                        .url(GITHUB_LATEST_RELEASE_API)
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", "EuropaGleam-App")
                        .build();

                Response apiResponse = client.newCall(request).execute();
                if (!apiResponse.isSuccessful() || apiResponse.body() == null) {
                    throw new Exception("HTTP " + apiResponse.code());
                }

                String responseStr = apiResponse.body().string();
                apiResponse.close();

                JSONObject json = new JSONObject(responseStr);
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
        if (latestTag == null || latestTag.trim().isEmpty()) return false;
        if (currentVersion == null || currentVersion.trim().isEmpty()) return true;

        String cleanLatest = latestTag.replaceAll("^[vV]", "").trim();
        String cleanCurrent = currentVersion.replaceAll("^[vV]", "").trim();

        if (cleanLatest.equalsIgnoreCase(cleanCurrent)) {
            return false;
        }

        // Split into base version and suffix using '-'
        String[] lSplit = cleanLatest.split("-", 2);
        String[] cSplit = cleanCurrent.split("-", 2);

        String lBase = lSplit[0];
        String cBase = cSplit[0];

        // 1. Compare numeric semver base (e.g. 20.2.6 vs 20.2.7)
        String[] lTokens = lBase.split("\\.");
        String[] cTokens = cBase.split("\\.");
        int maxTokens = Math.max(lTokens.length, cTokens.length);

        for (int i = 0; i < maxTokens; i++) {
            int lVal = i < lTokens.length ? parseSafeInt(lTokens[i]) : 0;
            int cVal = i < cTokens.length ? parseSafeInt(cTokens[i]) : 0;
            if (lVal > cVal) return true;
            if (lVal < cVal) return false;
        }

        // 2. Base versions are identical (e.g. 20.2.6 == 20.2.6)
        String lSuffix = lSplit.length > 1 ? lSplit[1] : "";
        String cSuffix = cSplit.length > 1 ? cSplit[1] : "";

        if (lSuffix.equalsIgnoreCase(cSuffix)) {
            return false;
        }

        // If current build has no suffix (e.g. "20.2.6") and remote is "20.2.6-europaX",
        // don't harass the user with false positive updates on base releases.
        if (cSuffix.isEmpty() && !lSuffix.isEmpty()) {
            return false;
        }

        int lNum = extractTrailingNumber(lSuffix);
        int cNum = extractTrailingNumber(cSuffix);

        if (lNum != -1 && cNum != -1) {
            return lNum > cNum;
        }

        return lSuffix.compareToIgnoreCase(cSuffix) > 0;
    }

    private static int parseSafeInt(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s.replaceAll("\\D+", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private static int extractTrailingNumber(String s) {
        if (s == null || s.isEmpty()) return -1;
        String digits = s.replaceAll("\\D+", "");
        if (digits.isEmpty()) return -1;
        try {
            return Integer.parseInt(digits);
        } catch (Exception e) {
            return -1;
        }
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
        File cacheDir = activity.getExternalCacheDir();
        if (cacheDir == null) {
            cacheDir = activity.getCacheDir();
        }
        final File targetApk = new File(cacheDir, "EuropaGleam_update.apk");

        // If an APK is already downloaded with valid size, launch installer immediately
        if (targetApk.exists() && targetApk.length() > 5 * 1024 * 1024 &&
                (releaseInfo.assetSize <= 0 || targetApk.length() == releaseInfo.assetSize)) {
            targetApk.setReadable(true, false);
            installApk(activity, targetApk);
            return;
        }

        final ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle(R.string.update_downloading_title);
        progressDialog.setMessage(activity.getString(R.string.update_downloading_message, releaseInfo.assetName != null ? releaseInfo.assetName : "EuropaGleam.apk"));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.setMax(100);
        progressDialog.show();

        executor.execute(() -> {
            File tempApk = targetApk;
            try {
                if (tempApk.exists()) {
                    tempApk.delete();
                }

                OkHttpClient client = new OkHttpClient.Builder()
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .connectTimeout(20, TimeUnit.SECONDS)
                        .readTimeout(90, TimeUnit.SECONDS)
                        .build();

                Request request = new Request.Builder()
                        .url(releaseInfo.downloadUrl)
                        .header("User-Agent", "EuropaGleam-App")
                        .build();

                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    throw new Exception("HTTP " + response.code());
                }

                ResponseBody body = response.body();
                if (body == null) {
                    throw new Exception("Empty response body");
                }

                long fileLength = body.contentLength();
                InputStream input = body.byteStream();
                FileOutputStream output = new FileOutputStream(tempApk);

                byte[] data = new byte[16384];
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
                response.close();

                tempApk.setReadable(true, false);

                if (tempApk.length() < 5 * 1024 * 1024) {
                    throw new Exception("Downloaded file too small (" + tempApk.length() + " bytes)");
                }

                final File finalApk = tempApk;
                mainHandler.post(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    installApk(activity, finalApk);
                });

            } catch (Exception e) {
                LimeLog.warning("Download failed: " + e.getMessage());
                if (tempApk != null && tempApk.exists()) {
                    tempApk.delete();
                }
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
        if (activity == null || apkFile == null || !apkFile.exists() || apkFile.length() < 1024 * 1024) {
            if (activity != null) {
                Toast.makeText(activity, "APK file missing or invalid", Toast.LENGTH_LONG).show();
            }
            return;
        }

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

            apkFile.setReadable(true, false);

            Uri apkUri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".fileprovider",
                    apkFile
            );

            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            installIntent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);

            // Grant read permission to all packages that can handle this install intent
            PackageManager pm = activity.getPackageManager();
            List<ResolveInfo> resolveInfoList = pm.queryIntentActivities(installIntent, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolveInfoList != null) {
                for (ResolveInfo resolveInfo : resolveInfoList) {
                    if (resolveInfo.activityInfo != null && resolveInfo.activityInfo.packageName != null) {
                        activity.grantUriPermission(resolveInfo.activityInfo.packageName, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }
                }
            }

            activity.startActivity(installIntent);

        } catch (Exception e) {
            LimeLog.warning("Install failed: " + e.getMessage());
            Toast.makeText(activity, "Failed to launch installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
