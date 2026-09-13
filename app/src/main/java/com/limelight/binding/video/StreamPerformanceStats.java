package com.limelight.binding.video;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StreamPerformanceStats {
    public float incomingFps;
    public float renderFps;
    public int pingMs;
    public float hostEncodeMs;
    public float receiverDecodeMs;
    public double bitrateMbps;
    public float cpuUsagePercent;
    public float gpuUsagePercent;
    public float packetLossPercent;
    public int streamWidth;
    public int streamHeight;
    public String decoderName;

    /**
     * Builds a clean, single-line horizontal string formatted for the top performance bar.
     */
    public String buildTopBarString(boolean showFps,
                                   boolean showPing,
                                   boolean showHostEncode,
                                   boolean showDecode,
                                   boolean showBitrate,
                                   boolean showCpu,
                                   boolean showGpu) {
        List<String> items = new ArrayList<>();

        if (showFps) {
            items.add(String.format(Locale.US, "FPS: %.0f", incomingFps));
        }
        if (showPing) {
            items.add(String.format(Locale.US, "Ping: %dms", pingMs));
        }
        if (showHostEncode) {
            if (hostEncodeMs > 0) {
                items.add(String.format(Locale.US, "Host: %.1fms", hostEncodeMs));
            } else {
                items.add("Host: 0ms");
            }
        }
        if (showDecode) {
            items.add(String.format(Locale.US, "Dec: %.1fms", receiverDecodeMs));
        }
        if (showBitrate) {
            if (bitrateMbps >= 1.0) {
                items.add(String.format(Locale.US, "Bitrate: %.1f Mbps", bitrateMbps));
            } else if (bitrateMbps > 0) {
                items.add(String.format(Locale.US, "Bitrate: %.0f Kbps", bitrateMbps * 1000.0));
            } else {
                items.add("Bitrate: 0 Mbps");
            }
        }
        if (showCpu) {
            items.add(String.format(Locale.US, "CPU: %.0f%%", cpuUsagePercent));
        }
        if (showGpu) {
            if (gpuUsagePercent >= 0) {
                items.add(String.format(Locale.US, "GPU: %.0f%%", gpuUsagePercent));
            } else {
                items.add("GPU: N/A");
            }
        }

        if (items.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append("   •   ");
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }
}
