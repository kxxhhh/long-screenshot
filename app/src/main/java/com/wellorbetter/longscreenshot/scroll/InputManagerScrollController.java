package com.wellorbetter.longscreenshot.scroll;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import java.lang.reflect.Method;

import com.wellorbetter.longscreenshot.capture.CaptureCallback;
import com.wellorbetter.longscreenshot.capture.IScreenCapture;
import com.wellorbetter.longscreenshot.permission.IPermissionProvider;

/**
 * Shizuku 方案的滚动控制器，多版本兼容。
 *
 * <p>优先通过反射 IInputManager 注入 MotionEvent（低延迟），
 * 失败则 fallback 到 {@code su -c input swipe} shell 命令。
 */
public class InputManagerScrollController implements IScrollController {

    private static final int BOTTOM_DETECT_FRAMES = 3;

    private final IPermissionProvider permissionProvider;
    private final IScreenCapture screenCapture;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Object iInputManager;
    private Method injectInputEventMethod;
    private boolean useShellFallback = false;

    private ScrollConfig config;
    private ScrollCallback callback;

    private int totalScrolledPx = 0;
    private int unchangedFrameCount = 0;
    private Bitmap lastBitmap;
    private volatile boolean running = false;

    private final Runnable scrollStep = new Runnable() {
        @Override
        public void run() {
            if (!running) return;

            doScroll();
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

    public InputManagerScrollController(IPermissionProvider permissionProvider,
                                         IScreenCapture screenCapture) {
        this.permissionProvider = permissionProvider;
        this.screenCapture = screenCapture;
    }

    @Override
    public void start(ScrollConfig config, ScrollCallback callback) {
        if (running) return;
        this.config = config;
        this.callback = callback;
        this.totalScrolledPx = 0;
        this.unchangedFrameCount = 0;

        // Try to resolve IInputManager via reflection
        if (!resolveInputManager()) {
            useShellFallback = true;
        }

        running = true;

        if (!useShellFallback) {
            injectDownEvent();
        }
        mainHandler.postDelayed(scrollStep, config.intervalMs);
    }

    /**
     * Try multiple known class paths for IInputManager across Android versions.
     */
    private boolean resolveInputManager() {
        IBinder binder = permissionProvider.getSystemService(android.content.Context.INPUT_SERVICE);
        if (binder == null) return false;

        // Known class paths across Android versions
        String[] stubClasses = {
                "android.hardware.input.IInputManager$Stub",    // Android 8-15
                "android.input.IInputManager$Stub",             // Possible future path
        };

        for (String className : stubClasses) {
            try {
                Class<?> stubClass = Class.forName(className);
                Object proxy = stubClass.getMethod("asInterface", IBinder.class)
                        .invoke(null, binder);
                // Find injectInputEvent method
                Method m = proxy.getClass()
                        .getMethod("injectInputEvent", MotionEvent.class, int.class);
                iInputManager = proxy;
                injectInputEventMethod = m;
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private void doScroll() {
        if (useShellFallback) {
            shellSwipe();
        } else {
            injectMoveEvent();
        }
    }

    /**
     * Fallback: use shell command for swipe gesture.
     */
    private void shellSwipe() {
        int fromY = (int) config.targetY;
        int toY = fromY - config.stepPx;
        if (toY < 0) toY = 0;
        int x = (int) config.targetX;
        int durationMs = (int) Math.min(config.intervalMs / 2, 150);
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "su", "-c", "input swipe " + x + " " + fromY + " " + x + " " + toY + " " + durationMs
            });
            p.waitFor();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        mainHandler.removeCallbacks(scrollStep);
        if (!useShellFallback) {
            injectUpEvent();
        }
        if (lastBitmap != null) {
            lastBitmap.recycle();
            lastBitmap = null;
        }
    }

    @Override
    public boolean isScrolling() {
        return running;
    }

    // --- MotionEvent injection (when reflection works) ---

    private void injectDownEvent() {
        long now = SystemClock.uptimeMillis();
        MotionEvent ev = MotionEvent.obtain(now, now,
                MotionEvent.ACTION_DOWN, config.targetX, config.targetY, 0);
        injectEvent(ev);
        ev.recycle();
    }

    private void injectMoveEvent() {
        long now = SystemClock.uptimeMillis();
        float currentY = config.targetY - totalScrolledPx - config.stepPx;
        MotionEvent ev = MotionEvent.obtain(now, now,
                MotionEvent.ACTION_MOVE, config.targetX, currentY, 0);
        injectEvent(ev);
        ev.recycle();
    }

    private void injectUpEvent() {
        long now = SystemClock.uptimeMillis();
        float currentY = config.targetY - totalScrolledPx;
        MotionEvent ev = MotionEvent.obtain(now, now,
                MotionEvent.ACTION_UP, config.targetX, currentY, 0);
        injectEvent(ev);
        ev.recycle();
    }

    private void injectEvent(MotionEvent ev) {
        if (iInputManager == null || injectInputEventMethod == null) return;
        try {
            injectInputEventMethod.invoke(iInputManager, ev, 0);
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
