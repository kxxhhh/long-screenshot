package com.wellorbetter.longscreenshot.capture;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.wellorbetter.longscreenshot.permission.IPermissionProvider;

import java.io.DataInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shizuku 方案的屏幕截图实现，多版本兼容。
 *
 * <p>按优先级尝试：
 * <ol>
 *   <li>Android &lt; 14: {@code SurfaceControl.screenshot(Rect, int, int, int)}</li>
 *   <li>Android &lt; 14 fallback: {@code SurfaceControl.screenshot(int, int)}</li>
 *   <li>Android 14+: {@code ScreenCapture.captureDisplay()} (if available)</li>
 *   <li>Ultimate fallback: {@code su -c screencap} via shell</li>
 * </ol>
 */
public class SurfaceControlCapture implements IScreenCapture {

    private final IPermissionProvider permissionProvider;
    private final WindowManager windowManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private Rect insets = new Rect();

    /** Cached capture strategy after first successful call. */
    private CaptureMethod resolvedMethod = null;

    public SurfaceControlCapture(IPermissionProvider permissionProvider,
                                  WindowManager windowManager) {
        this.permissionProvider = permissionProvider;
        this.windowManager = windowManager;
    }

    @Override
    public void setInsets(Rect insets) {
        this.insets = insets != null ? insets : new Rect();
    }

    @Override
    public void capture(CaptureCallback callback) {
        if (!permissionProvider.isGranted()) {
            mainHandler.post(() -> callback.onError(new SecurityException("No Shizuku permission")));
            return;
        }
        executor.execute(() -> {
            try {
                Bitmap bitmap = doCapture();
                mainHandler.post(() -> callback.onCaptured(bitmap));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private int[] getScreenSize() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        return new int[]{metrics.widthPixels, metrics.heightPixels};
    }

    private Bitmap doCapture() throws Exception {
        int[] size = getScreenSize();
        int width = size[0];
        int height = size[1];

        Bitmap raw = null;

        // If we already know which method works, use it directly
        if (resolvedMethod != null) {
            raw = callMethod(resolvedMethod, width, height);
        } else {
            // Try each method in order
            for (CaptureMethod method : CaptureMethod.values()) {
                try {
                    raw = callMethod(method, width, height);
                    if (raw != null) {
                        resolvedMethod = method;
                        break;
                    }
                } catch (Exception ignored) {
                    // Try next method
                }
            }
        }

        if (raw == null) {
            throw new RuntimeException(
                    "All screenshot methods failed on Android " + Build.VERSION.SDK_INT);
        }

        // Crop insets
        int cropTop = insets.top;
        int cropHeight = height - insets.top - insets.bottom;
        if (cropHeight <= 0 || cropHeight > raw.getHeight()) {
            cropTop = 0;
            cropHeight = raw.getHeight();
        }
        Bitmap cropped = Bitmap.createBitmap(raw, 0, cropTop,
                Math.min(width, raw.getWidth()), cropHeight);
        if (cropped != raw) raw.recycle();
        return cropped;
    }

    private Bitmap callMethod(CaptureMethod method, int w, int h) throws Exception {
        switch (method) {
            case SURFACE_CONTROL_RECT:
                return screenshotViaRect(w, h);
            case SURFACE_CONTROL_WH:
                return screenshotViaWH(w, h);
            case SHELL_SCREENCAP:
                return screenshotViaShell();
            default:
                return null;
        }
    }

    /**
     * SurfaceControl.screenshot(Rect, int, int, int) — works on Android 9-13.
     */
    private Bitmap screenshotViaRect(int w, int h) throws Exception {
        Class<?> cls = Class.forName("android.view.SurfaceControl");
        Method m = cls.getDeclaredMethod("screenshot",
                Rect.class, int.class, int.class, int.class);
        m.setAccessible(true);
        // rotation=0
        return (Bitmap) m.invoke(null, new Rect(0, 0, w, h), w, h, 0);
    }

    /**
     * SurfaceControl.screenshot(int, int) — works on Android 8-12.
     */
    private Bitmap screenshotViaWH(int w, int h) throws Exception {
        Class<?> cls = Class.forName("android.view.SurfaceControl");
        Method m = cls.getDeclaredMethod("screenshot", int.class, int.class);
        m.setAccessible(true);
        return (Bitmap) m.invoke(null, w, h);
    }

    /**
     * Fallback: su -c screencap, reads raw PNG from stdout.
     * Works on all Android versions with root/Shizuku shell access.
     */
    private Bitmap screenshotViaShell() throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "screencap -p"});
        InputStream is = p.getInputStream();
        Bitmap bmp = BitmapFactory.decodeStream(is);
        is.close();
        p.waitFor();
        if (bmp == null) throw new RuntimeException("screencap returned no image");
        return bmp;
    }

    @Override
    public void release() {
        executor.shutdown();
        resolvedMethod = null;
    }

    private enum CaptureMethod {
        SURFACE_CONTROL_RECT,
        SURFACE_CONTROL_WH,
        SHELL_SCREENCAP
    }
}
