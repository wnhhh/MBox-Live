package com.github.tvbox.osc.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class LivePlaybackRetryTest {
    @Test
    public void newChannelDiscardsOldCallbackAndFailureCount() {
        LivePlaybackRetry retry = new LivePlaybackRetry();
        retry.begin(false);
        long first = retry.generation();
        assertTrue(retry.tryNextSource(4));
        assertTrue(retry.tryNextSource(4));
        retry.begin(false);
        assertFalse(retry.isCurrent(first));
        assertTrue(retry.tryNextSource(2));
        assertFalse(retry.tryNextSource(2));
    }

    @Test
    public void automaticRetriesPreserveBoundAcrossRequests() {
        LivePlaybackRetry retry = new LivePlaybackRetry();
        retry.begin(false);
        assertTrue(retry.tryNextSource(3));
        long first = retry.generation();
        retry.begin(true);
        assertFalse(retry.isCurrent(first));
        assertTrue(retry.tryNextSource(3));
        retry.begin(true);
        assertFalse(retry.tryNextSource(3));
    }

    @Test
    public void shrinkingSourceListCannotBypassLimit() {
        LivePlaybackRetry retry = new LivePlaybackRetry();
        retry.begin(false);
        assertTrue(retry.tryNextSource(5));
        assertTrue(retry.tryNextSource(5));
        assertFalse(retry.tryNextSource(1));
        assertFalse(retry.tryNextSource(0));
    }

    @Test
    public void pauseInvalidatesPendingCallback() {
        LivePlaybackRetry retry = new LivePlaybackRetry();
        retry.begin(false);
        long request = retry.generation();
        retry.invalidate();
        assertFalse(retry.isCurrent(request));
    }

    @Test
    public void successfulPlaybackResetsFailureCount() {
        LivePlaybackRetry retry = new LivePlaybackRetry();
        retry.begin(false);
        assertTrue(retry.tryNextSource(2));
        retry.onPlaying();
        assertTrue(retry.tryNextSource(2));
        assertFalse(retry.tryNextSource(2));
    }

    @Test
    public void singleSourceStopsAfterFirstFailure() {
        LivePlaybackRetry retry = new LivePlaybackRetry();
        retry.begin(false);
        assertFalse(retry.tryNextSource(1));
        retry.begin(false);
        assertTrue(retry.tryNextSource(2));
    }
}
