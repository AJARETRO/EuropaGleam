package com.limelight.binding.video;

public interface PerfOverlayListener {
    void onPerfUpdate(final String text);
    default void onPerfStatsUpdate(final StreamPerformanceStats stats) {}
}
