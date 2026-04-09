package com.wanjian.longscreenshot.orchestrator;

import android.graphics.Bitmap;

/**
 * Callback interface for scroll-capture lifecycle events.
 * All methods are guaranteed to be invoked on the main thread.
 */
public interface OrchestratorCallback {

    /**
     * Called after each frame is captured and appended.
     *
     * @param preview    a stitched preview bitmap of all frames captured so far
     * @param frameCount total number of frames captured so far
     */
    void onProgress(Bitmap preview, int frameCount);

    /**
     * Called when the scroll capture session has completed successfully.
     *
     * @param result the final composed long-screenshot bitmap
     */
    void onComplete(Bitmap result);

    /**
     * Called if an unrecoverable error occurs during the session.
     *
     * @param e the error that caused the session to fail
     */
    void onError(Throwable e);
}
