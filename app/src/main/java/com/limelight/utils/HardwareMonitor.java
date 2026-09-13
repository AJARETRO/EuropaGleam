package com.limelight.utils;

import android.os.Process;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * HardwareMonitor provides safe, periodic readings of CPU and GPU utilization
 * across various Android SoC vendors (Qualcomm Snapdragon, MediaTek, Samsung Exynos, ARM Mali).
 */
public class HardwareMonitor {
    private static HardwareMonitor instance;

    // CPU Tracking
    private long lastTotalTime = 0;
    private long lastIdleTime = 0;
    private long lastAppCpuTime = 0;
    private long lastAppRealtime = 0;
    private float cachedCpuUsage = 0f;

    // GPU Tracking
    private String detectedGpuPath = null;
    private boolean gpuPathSearched = false;
    private long lastGpuBusyTime = 0;
    private long lastGpuTotalTime = 0;
    private float cachedGpuUsage = -1f;

    // Throttle checks to avoid overhead
    private long lastUpdateTime = 0;

    public static synchronized HardwareMonitor getInstance() {
        if (instance == null) {
            instance = new HardwareMonitor();
        }
        return instance;
    }

    public synchronized void update() {
        long now = SystemClock.uptimeMillis();
        if (now - lastUpdateTime < 500 && lastUpdateTime != 0) {
            return;
        }
        lastUpdateTime = now;

        updateCpu();
        updateGpu();
    }

    private void updateCpu() {
        // Primary method: Read /proc/stat for system-wide CPU utilization
        boolean procStatSuccess = false;
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"), 1024)) {
            String line = reader.readLine();
            if (line != null && line.startsWith("cpu ")) {
                String[] toks = line.trim().split("\\s+");
                if (toks.length >= 5) {
                    long user = Long.parseLong(toks[1]);
                    long nice = Long.parseLong(toks[2]);
                    long system = Long.parseLong(toks[3]);
                    long idle = Long.parseLong(toks[4]);
                    long iowait = toks.length > 5 ? Long.parseLong(toks[5]) : 0;
                    long irq = toks.length > 6 ? Long.parseLong(toks[6]) : 0;
                    long softirq = toks.length > 7 ? Long.parseLong(toks[7]) : 0;
                    long steal = toks.length > 8 ? Long.parseLong(toks[8]) : 0;

                    long total = user + nice + system + idle + iowait + irq + softirq + steal;
                    long idleTotal = idle + iowait;

                    if (lastTotalTime > 0 && total > lastTotalTime) {
                        long deltaTotal = total - lastTotalTime;
                        long deltaIdle = idleTotal - lastIdleTime;
                        float usage = ((float) (deltaTotal - deltaIdle) / (float) deltaTotal) * 100f;
                        cachedCpuUsage = Math.max(0f, Math.min(100f, usage));
                        procStatSuccess = true;
                    }
                    lastTotalTime = total;
                    lastIdleTime = idleTotal;
                }
            }
        } catch (Throwable ignored) {
            // SELinux or restricted permissions on some Android 8+ devices
        }

        // Fallback method: Process CPU time if /proc/stat is blocked
        if (!procStatSuccess) {
            try {
                long appCpuTime = Process.getElapsedCpuTime();
                long appRealtime = SystemClock.elapsedRealtime();

                if (lastAppRealtime > 0 && appRealtime > lastAppRealtime) {
                    long deltaCpu = appCpuTime - lastAppCpuTime;
                    long deltaTime = appRealtime - lastAppRealtime;
                    int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
                    float usage = ((float) deltaCpu / (float) (deltaTime * cores)) * 100f;
                    cachedCpuUsage = Math.max(0f, Math.min(100f, usage));
                }
                lastAppCpuTime = appCpuTime;
                lastAppRealtime = appRealtime;
            } catch (Throwable ignored) {
            }
        }
    }

    private void updateGpu() {
        if (!gpuPathSearched) {
            gpuPathSearched = true;
            detectedGpuPath = findGpuPath();
        }

        if (detectedGpuPath == null) {
            cachedGpuUsage = -1f;
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(detectedGpuPath), 256)) {
            String line = reader.readLine();
            if (line != null) {
                line = line.trim();
                if (detectedGpuPath.endsWith("gpubusy")) {
                    // Qualcomm Adreno format: "busy_time total_time"
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        long busy = Long.parseLong(parts[0]);
                        long total = Long.parseLong(parts[1]);
                        if (lastGpuTotalTime > 0 && total > lastGpuTotalTime) {
                            long deltaBusy = busy - lastGpuBusyTime;
                            long deltaTotal = total - lastGpuTotalTime;
                            if (deltaTotal > 0) {
                                cachedGpuUsage = Math.max(0f, Math.min(100f, ((float) deltaBusy / deltaTotal) * 100f));
                            }
                        }
                        lastGpuBusyTime = busy;
                        lastGpuTotalTime = total;
                    }
                } else {
                    // Single number percentage (e.g. "25 %" or "25" or "128/255")
                    line = line.replace("%", "").trim();
                    float val = Float.parseFloat(line);
                    // Normalize Mali 0-255 scaling if applicable
                    if (val > 100f && val <= 255f) {
                        val = (val / 255f) * 100f;
                    }
                    cachedGpuUsage = Math.max(0f, Math.min(100f, val));
                }
            }
        } catch (Throwable e) {
            cachedGpuUsage = -1f;
        }
    }

    private String findGpuPath() {
        String[] candidates = new String[]{
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/gpubusy",
            "/sys/module/ged/parameters/gpu_loading",
            "/sys/kernel/ged/hal/gpu_utilization",
            "/sys/class/misc/mali0/device/utilization",
            "/sys/devices/platform/13040000.mali/utilization",
            "/sys/devices/platform/13000000.mali/utilization",
            "/sys/devices/platform/mali.0/utilization",
            "/sys/class/devfreq/gpufreq/load",
            "/sys/devices/platform/gpusysfs/gpu_busy",
            "/sys/devices/soc/1c00000.qcom,kgsl-3d0/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/devices/soc.0/1c00000.qcom,kgsl-3d0/kgsl/kgsl-3d0/gpu_busy_percentage"
        };

        for (String path : candidates) {
            try {
                File f = new File(path);
                if (f.exists() && f.canRead()) {
                    return path;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public float getCpuUsage() {
        return cachedCpuUsage;
    }

    public float getGpuUsage() {
        return cachedGpuUsage;
    }
}
