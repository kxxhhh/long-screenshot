package com.wanjian.longscreenshot.capture;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.ByteBuffer;

/**
 * 基于 MediaProjection 的屏幕截图实现。
 *
 * <p>无需 root 或 adb，用户只需在首次使用时同意一次屏幕录制授权弹窗。
 * Android 10+ 需要前台服务（{@link MediaProjectionService}），由本类自动管理。
 *
 * <p>使用方式：
 * <pre>
 * // 1. 在 Activity 里请求授权
 * startActivityForResult(MediaProjectionCapture.createScreenCaptureIntent(this), REQ_CODE);
 *
 * // 2. onActivityResult 里创建实例
 * screenCapture = MediaProjectionCapture.fromActivityResult(this, resultCode, data);
 * </pre>
 */
public class MediaProjectionCapture implements IScreenCapture {

    private final Context appContext;
    private final MediaProjection projection;
    private final int screenWidth;
    private final int screenHeight;
    private final int screenDensity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Rect insets = new Rect();
    private HandlerThread captureThread;
    private Handler captureHandler;

    private MediaProjectionCapture(Context context, MediaProjection projection) {
        this.appContext = context.getApplicationContext();
        this.projection = projection;

        DisplayMetrics metrics = new DisplayMetrics();
        ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getRealMetrics(metrics);
        this.screenWidth = metrics.widthPixels;
        this.screenHeight = metrics.heightPixels;
        this.screenDensity = metrics.densityDpi;

        // 启动专用后台线程处理截图，避免阻塞主线程
        captureThread = new HandlerThread("MediaProjectionCapture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());
    }

    /**
     * 创建屏幕录制授权 Intent，传入 startActivityForResult。
     */
    public static Intent createScreenCaptureIntent(Context context) {
        MediaProjectionManager mgr =
                (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        return mgr.createScreenCaptureIntent();
    }

    /**
     * 从 onActivityResult 结果创建 MediaProjectionCapture 实例。
     * 同时启动前台服务（Android 10+ 要求）。
     *
     * @param context    Activity context
     * @param resultCode onActivityResult 的 resultCode
     * @param data       onActivityResult 的 data Intent
     * @return 实例，resultCode != RESULT_OK 时返回 null
     */
    public static MediaProjectionCapture fromActivityResult(
            Context context, int resultCode, Intent data) {
        if (resultCode != android.app.Activity.RESULT_OK || data == null) return null;

        // Android 10+ 启动前台服务
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            context.startForegroundService(
                    new Intent(context, MediaProjectionService.class));
        }

        MediaProjectionManager mgr =
                (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        MediaProjection projection = mgr.getMediaProjection(resultCode, data);
        return new MediaProjectionCapture(context, projection);
    }

    @Override
    public void setInsets(Rect insets) {
        this.insets = insets != null ? insets : new Rect();
    }

    @Override
    public void capture(CaptureCallback callback) {
        captureHandler.post(() -> {
            VirtualDisplay virtualDisplay = null;
            ImageReader imageReader = null;
            try {
                int contentHeight = screenHeight - insets.top - insets.bottom;
                imageReader = ImageReader.newInstance(
                        screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);

                virtualDisplay = projection.createVirtualDisplay(
                        "LongScreenshot",
                        screenWidth, screenHeight, screenDensity,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.getSurface(), null, null);

                // 等待第一帧可用（最多等 500ms）
                Image image = null;
                long deadline = System.currentTimeMillis() + 500;
                while (image == null && System.currentTimeMillis() < deadline) {
                    image = imageReader.acquireLatestImage();
                    if (image == null) {
                        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                    }
                }

                if (image == null) {
                    mainHandler.post(() -> callback.onError(
                            new RuntimeException("Timeout waiting for screen frame")));
                    return;
                }

                // Image.Plane[0] 是 RGBA 数据
                Image.Plane plane = image.getPlanes()[0];
                int rowStride = plane.getRowStride();
                int pixelStride = plane.getPixelStride();
                ByteBuffer buffer = plane.getBuffer();

                // 处理行填充（rowStride 可能大于 width * pixelStride）
                int bmpWidth = rowStride / pixelStride;
                Bitmap rawBitmap = Bitmap.createBitmap(
                        bmpWidth, screenHeight, Bitmap.Config.ARGB_8888);
                rawBitmap.copyPixelsFromBuffer(buffer);
                image.close();

                // 裁剪到实际屏幕宽度和去除 insets
                Bitmap cropped = Bitmap.createBitmap(
                        rawBitmap, 0, insets.top, screenWidth, contentHeight);
                if (bmpWidth != screenWidth || insets.top != 0) {
                    rawBitmap.recycle();
                }

                mainHandler.post(() -> callback.onCaptured(cropped));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            } finally {
                if (virtualDisplay != null) virtualDisplay.release();
                if (imageReader != null) imageReader.close();
            }
        });
    }

    @Override
    public void release() {
        if (projection != null) projection.stop();
        if (captureThread != null) {
            captureThread.quitSafely();
            captureThread = null;
        }
        // 停止前台服务
        appContext.stopService(new Intent(appContext, MediaProjectionService.class));
    }
}
