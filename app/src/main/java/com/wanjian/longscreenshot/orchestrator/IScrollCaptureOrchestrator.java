package com.wanjian.longscreenshot.orchestrator;

/**
 * Orchestrator interface for coordinating the scroll-capture process.
 */
public interface IScrollCaptureOrchestrator {

    /**
     * Start the scroll capture session with the given request parameters.
     *
     * @param request encapsulates scroll config, capture insets, and callback
     */
    void start(ScrollCaptureRequest request);

    /**
     * Stop an in-progress scroll capture session and release resources.
     */
    void stop();
}
