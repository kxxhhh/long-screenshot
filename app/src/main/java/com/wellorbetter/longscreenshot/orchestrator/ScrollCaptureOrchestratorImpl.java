package com.wellorbetter.longscreenshot.orchestrator;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import com.wellorbetter.longscreenshot.capture.IScreenCapture;
import com.wellorbetter.longscreenshot.capture.CaptureCallback;
import com.wellorbetter.longscreenshot.compose.IFrameComposer;
import com.wellorbetter.longscreenshot.permission.IPermissionProvider;
import com.wellorbetter.longscreenshot.permission.PermissionCallback;
import com.wellorbetter.longscreenshot.render.IPreviewRenderer;
import com.wellorbetter.longscreenshot.scroll.IScrollController;
import com.wellorbetter.longscreenshot.scroll.ScrollCallback;

/**
 * Facade implementation of {@link IScrollCaptureOrchestrator}.
 * Coordinates permission checking, screen capture, scrolling, frame composing,
 * and preview rendering in the correct order.
 *
 * <p>All {@link OrchestratorCallback} methods are dispatched on the main thread.</p>
 */
public class ScrollCaptureOrchestratorImpl implements IScrollCaptureOrchestrator {

    private final IPermissionProvider permissionProvider;
    private final IScreenCapture screenCapture;
    private final IScrollController scrollController;
    private final IFrameComposer frameComposer;
    private final IPreviewRenderer previewRenderer;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ScrollCaptureRequest currentRequest;
    private volatile boolean running = false;
    /** Local frame counter; incremented each time a frame is appended. */
    private int frameCount = 0;

    public ScrollCaptureOrchestratorImpl(
            IPermissionProvider permissionProvider,
            IScreenCapture screenCapture,
            IScrollController scrollController,
            IFrameComposer frameComposer,
            IPreviewRenderer previewRenderer) {
        this.permissionProvider = permissionProvider;
        this.screenCapture = screenCapture;
        this.scrollController = scrollController;
        this.frameComposer = frameComposer;
        this.previewRenderer = previewRenderer;
    }

    @Override
    public void start(ScrollCaptureRequest request) {
        this.currentRequest = request;
        this.running = true;
        this.frameCount = 0;

        // permissionProvider 为 null 时表示当前方案（MediaProjection/Root）无需额外权限检查
        if (permissionProvider != null && !permissionProvider.isGranted()) {
            permissionProvider.request(new PermissionCallback() {
                @Override
                public void onGranted() {
                    captureFirstFrame();
                }

                @Override
                public void onDenied() {
                    dispatchError(new SecurityException("Screen capture permission denied"));
                }
            });
        } else {
            captureFirstFrame();
        }
    }

    /** 授权成功后，设置 insets 并截第一帧。 */
    private void captureFirstFrame() {
        if (!running) return;
        // TODO: 3. setInsets 确保截图裁剪掉状态栏/导航栏
        screenCapture.setInsets(currentRequest.captureInsets);
        screenCapture.capture(new CaptureCallback() {
            @Override
            public void onCaptured(Bitmap frame) {
                // TODO: 3. 第一帧 appendFrame(raw, scrolledPx=0) 后，调 scrollController.start()
                frameComposer.appendFrame(frame, 0);
                frameCount++;
                startScrolling();
            }

            @Override
            public void onError(Throwable e) {
                dispatchError(e);
            }
        });
    }

    /** 启动滚动控制器，每次 onScrollSettled 触发时截图并更新预览。 */
    private void startScrolling() {
        if (!running) return;
        scrollController.start(currentRequest.scrollConfig, new ScrollCallback() {

            /** Tracks cumulative scroll distance in pixels across all steps. */
            private int totalScrolledPx = 0;

            @Override
            public void onScrollSettled(int scrolledPx) {
                if (!running) return;
                totalScrolledPx += scrolledPx;
                final int capturedScrollPx = totalScrolledPx;

                // TODO: 4. ScrollCallback.onScrollSettled() 触发时，调 capture() 截下一帧
                screenCapture.capture(new CaptureCallback() {
                    @Override
                    public void onCaptured(Bitmap frame) {
                        // TODO: appendFrame(raw, scrolledPx) — 传入本次累计滚动距离
                        frameComposer.appendFrame(frame, capturedScrollPx);
                        frameCount++;

                        // TODO: 5. appendFrame 后调 previewRenderer.updatePreview(composer.getPreview())
                        Bitmap preview = frameComposer.getPreview();
                        previewRenderer.updatePreview(preview);

                        // TODO: 6. 回调 OrchestratorCallback.onProgress()，frameCount 由本地计数提供
                        final int count = frameCount;
                        mainHandler.post(() -> currentRequest.callback.onProgress(preview, count));
                    }

                    @Override
                    public void onError(Throwable e) {
                        dispatchError(e);
                    }
                });
            }

            @Override
            public void onReachedBottom() {
                // TODO: 7. ScrollCallback.onReachedBottom() 触发时，stop()，同步调 composer.compose()
                stop();
                // compose() 同步返回最终合成 Bitmap
                Bitmap result = frameComposer.compose();
                // TODO: 8. 回调 OrchestratorCallback.onComplete()
                mainHandler.post(() -> currentRequest.callback.onComplete(result));
            }

            @Override
            public void onError(Throwable e) {
                dispatchError(e);
            }
        });
    }

    @Override
    public void stop() {
        running = false;
        // TODO: 9. stop() 时调 scrollController.stop()，frameComposer.reset()
        scrollController.stop();
        frameComposer.reset();
    }

    private void dispatchError(Throwable e) {
        mainHandler.post(() -> {
            if (currentRequest != null) {
                currentRequest.callback.onError(e);
            }
        });
    }
}
