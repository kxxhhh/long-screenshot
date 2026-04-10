package com.wellorbetter.longscreenshot.scroll;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import com.wellorbetter.longscreenshot.capture.CaptureCallback;
import com.wellorbetter.longscreenshot.capture.IScreenCapture;

/**
 * 基于 Root shell 命令的滚动控制器。
 *
 * <p>通过 {@code su -c input swipe} 注入滑动手势，无需无障碍服务。
 * 需要设备已 root。配合 {@link com.wellorbetter.longscreenshot.capture.RootCapture} 使用。
 */
public class RootShellScrollController implements IScrollController {

    private static final int BOTTOM_DETECT_FRAMES = 3;

    private final IScreenCapture screenCapture;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile boolean running = false;
    private int totalScrolledPx = 0;
    private int unchangedFrameCount = 0;
    private Bitmap lastBitmap;
    private ScrollConfig config;
    private ScrollCallback callback;

    private final Runnable scrollStep = new Runnable() {
        @Override
        public void run() {
            if (!running) return;

            shellSwipe();
            totalScrolledPx += config.stepPx;

            screenCapture.capture(new CaptureCallback() {
                @Override
                public void onCaptured(Bitmap bitmap) {
                    if (!running) { bitmap.recycle(); return; }
                    boolean reached = checkReachedBottom(bitmap);
                    if (lastBitmap != null) lastBitmap.recycle();
                    lastBitmap = bitmap;

                    callback.onScrollSettled(totalScrolledPx);

                    if (reached) {
                        stop();
                        callback.onReachedBottom();
                    } else {
                        mainHandler.postDelayed(scrollStep, config.intervalMs);
                    }
                }

                @Override
                public void onError(Throwable e) {
                    mainHandler.postDelayed(scrollStep, config.intervalMs);
                }
            });
        }
    };

    public RootShellScrollController(IScreenCapture screenCapture) {
        this.screenCapture = screenCapture;
    }

    @Override
    public void start(ScrollConfig config, ScrollCallback callback) {
        if (running) return;
        this.config = config;
        this.callback = callback;
        this.totalScrolledPx = 0;
        this.unchangedFrameCount = 0;
        this.running = true;
        mainHandler.postDelayed(scrollStep, config.intervalMs);
    }

    @Override
    public void stop() {
        running = false;
        mainHandler.removeCallbacks(scrollStep);
        if (lastBitmap != null) {
            lastBitmap.recycle();
            lastBitmap = null;
        }
    }

    @Override
    public boolean isScrolling() {
        return running;
    }

    private void shellSwipe() {
        int x = (int) config.targetX;
        int fromY = (int) config.targetY;
        int toY = fromY - config.stepPx;
        if (toY < 0) toY = 0;
        int durationMs = (int) Math.min(config.intervalMs / 2, 150);
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "su", "-c", "input swipe " + x + " " + fromY + " " + x + " " + toY + " " + durationMs
            });
            p.waitFor();
        } catch (Exception ignored) {
        }
    }

    private boolean checkReachedBottom(Bitmap current) {
        if (lastBitmap == null || current == null) return false;
        if (lastBitmap.getWidth() != current.getWidth()
                || lastBitmap.getHeight() != current.getHeight()) return false;

        int y = current.getHeight() / 2;
        int sampleStep = Math.max(1, current.getWidth() / 20);
        boolean same = true;
        for (int x = 0; x < current.getWidth(); x += sampleStep) {
            if (lastBitmap.getPixel(x, y) != current.getPixel(x, y)) {
                same = false;
                break;
            }
        }
        unchangedFrameCount = same ? unchangedFrameCount + 1 : 0;
        return unchangedFrameCount >= BOTTOM_DETECT_FRAMES;
    }
}
