package com.github.tvbox.osc.util;

public final class LivePlaybackRetry {
    private long generation;
    private int failures;

    public void begin(boolean automaticRetry) {
        generation++;
        if (!automaticRetry) failures = 0;
    }

    public long generation() {
        return generation;
    }

    public boolean isCurrent(long request) {
        return generation == request;
    }

    public void invalidate() {
        generation++;
    }

    public void onPlaying() {
        failures = 0;
    }

    public boolean tryNextSource(int sourceCount) {
        failures++;
        return failures < sourceCount;
    }
}
