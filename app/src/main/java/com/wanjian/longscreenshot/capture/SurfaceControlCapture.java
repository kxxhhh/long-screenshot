package com.wanjian.longscreenshot.capture;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.wanjian.longscreenshot.permission.IPermissionProvider;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 通过反射调用 {@code SurfaceControl.screenshot()} 实现的屏幕截图。
 *
 * <p>需要 Shizuku / root 权限，由 {@link IPermissionProvider} 提供授权状态校验。
 * 截图操作在单线程后台执行，结果回调到主线程。
 */
public class SurfaceControlCapture implements IScreenCapture {

    private final IPermissionProvider permissionProvider;
    private final WindowManager windowManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** 缓存反射到的 screenshot 方法，避免每次截图重复查找。 */
    private Method screenshotMethod;

    /** 系统 UI insets，用于裁剪截图中的状态栏和导航栏区域。 */
    private Rect insets = new Rect();

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

    private Bitmap doCapture() throws Exception {
        // TODO: 通过反射获取 SurfaceControl.screenshot() 方法
        //   使用 Class.forName("android.view.SurfaceControl") 加载类，
        //   然后调用 getDeclaredMethod("screenshot", int.class, int.class) 或
        //   getDeclaredMethod("screenshot", Rect.class, int.class, int.class, int.class)
        //   (不同 Android 版本签名不同，需做版本兼容判断)，
        //   并调用 setAccessible(true) 使其可被调用。
        if (screenshotMethod == null) {
            Class<?> surfaceControlClass = Class.forName("android.view.SurfaceControl");
            screenshotMethod = surfaceControlClass.getDeclaredMethod(
                    "screenshot", int.class, int.class);
            screenshotMethod.setAccessible(true);
        }

        // TODO: 调用 screenshot()，传入屏幕宽高
        //   先通过 WindowManager.getDefaultDisplay().getRealMetrics() 获取屏幕真实分辨率
        //   （包含状态栏和导航栏），再将宽高传入 screenshot(width, height)。
        //   注意：getRealMetrics 而非 getMetrics，后者不含系统 UI 区域。
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        Bitmap raw = (Bitmap) screenshotMethod.invoke(null, width, height);
        if (raw == null) throw new RuntimeException("SurfaceControl.screenshot() returned null");

        // TODO: 根据 insets 裁剪 Bitmap，去掉状态栏/导航栏区域
        //   使用 setInsets(Rect) 传入的 insets（top = 状态栏高度，bottom = 导航栏高度），
        //   调用 Bitmap.createBitmap(raw, 0, insets.top, width, height - insets.top - insets.bottom)
        //   裁剪出纯内容区域，避免长截图拼接时出现重叠的系统 UI。
        //   裁剪完成后调用 raw.recycle() 释放原始 Bitmap 内存。
        int cropTop = insets.top;
        int cropHeight = height - insets.top - insets.bottom;
        if (cropHeight <= 0) {
            raw.recycle();
            throw new RuntimeException("Invalid insets: cropped height <= 0");
        }
        Bitmap cropped = Bitmap.createBitmap(raw, 0, cropTop, width, cropHeight);
        raw.recycle();
        return cropped;
    }

    @Override
    public void release() {
        executor.shutdown();
        screenshotMethod = null;
    }
}
