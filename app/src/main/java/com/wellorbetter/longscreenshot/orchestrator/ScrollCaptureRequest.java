package com.wellorbetter.longscreenshot.orchestrator;

import android.graphics.Rect;
import com.wellorbetter.longscreenshot.scroll.ScrollConfig;

/**
 * Request object passed to {@link IScrollCaptureOrchestrator#start(ScrollCaptureRequest)}.
 * Bundles all parameters needed to perform a scroll-capture session.
 */
public class ScrollCaptureRequest {

    /** Configuration controlling scroll speed, step size, etc. */
    public final ScrollConfig scrollConfig;

    /**
     * Insets (in pixels) for status bar and navigation bar areas that should be
     * cropped out of each captured frame.
     */
    public final Rect captureInsets;

    /** Callback that receives progress, completion, and error events. */
    public final OrchestratorCallback callback;

    public ScrollCaptureRequest(ScrollConfig scrollConfig, Rect captureInsets, OrchestratorCallback callback) {
        this.scrollConfig = scrollConfig;
        this.captureInsets = captureInsets;
        this.callback = callback;
    }
}
