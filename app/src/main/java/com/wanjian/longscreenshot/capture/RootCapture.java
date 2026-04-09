package com.wanjian.longscreenshot.capture;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 基于 Root 权限的屏幕截图实现。
 *
 * <p>通过 {@code su -c screencap -p <path>} 调用系统截图工具，将结果写入临时文件后读取为 Bitmap。
 * 需要设备已 root，优先级最低（用于开发者/调试场景）。
 *
 * <p>临时文件存储在应用私有目录，无需存储权限。
 */
public class RootCapture implements IScreenCapture {

    private final File cacheDir;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final HandlerThread captureThread;
    private final Handler captureHandler;

    private Rect insets = new Rect();

    public RootCapture(File cacheDir) {
        this.cacheDir = cacheDir;
        captureThread = new HandlerThread("RootCapture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());
    }

    @Override
    public void setInsets(Rect insets) {
        this.insets = insets != null ? insets : new Rect();
    }

    @Override
    public void capture(CaptureCallback callback) {
        captureHandler.post(() -> {
            File tmpFile = new File(cacheDir, "root_cap_" + System.currentTimeMillis() + ".png");
            try {
                // 请求 su 权限并执行 screencap
                Process suProcess = Runtime.getRuntime().exec("su");
                OutputStream os = suProcess.getOutputStream();
                String cmd = "screencap -p " + tmpFile.getAbsolutePath() + "\nexit\n";
                os.write(cmd.getBytes());
                os.flush();

                int exitCode = suProcess.waitFor();
                if (exitCode != 0 || !tmpFile.exists() || tmpFile.length() == 0) {
                    mainHandler.post(() -> callback.onError(
                            new IOException("screencap 失败，exit=" + exitCode
                                    + "，请确认设备已 root 并授权本应用 su 权限")));
                    return;
                }

                // 解码 PNG
                Bitmap raw = BitmapFactory.decodeFile(tmpFile.getAbsolutePath());
                if (raw == null) {
                    mainHandler.post(() -> callback.onError(
                            new IOException("无法解码截图文件")));
                    return;
                }

                // 裁剪 insets
                int cropTop = insets.top;
                int cropHeight = raw.getHeight() - insets.top - insets.bottom;
                if (cropHeight <= 0) {
                    mainHandler.post(() -> callback.onError(
                            new IllegalArgumentException("insets 过大导致裁剪高度为 0")));
                    raw.recycle();
                    return;
                }
                Bitmap cropped = (cropTop == 0 && insets.bottom == 0) ? raw
                        : Bitmap.createBitmap(raw, 0, cropTop, raw.getWidth(), cropHeight);
                if (cropped != raw) raw.recycle();

                mainHandler.post(() -> callback.onCaptured(cropped));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            } finally {
                // 删除临时文件
                if (tmpFile.exists()) //noinspection ResultOfMethodCallIgnored
                    tmpFile.delete();
            }
        });
    }

    @Override
    public void release() {
        captureThread.quitSafely();
    }

    /**
     * 检测当前设备是否有 root 权限（快速探测，非阻塞）。
     */
    public static boolean isRootAvailable() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            InputStream is = p.getInputStream();
            byte[] buf = new byte[64];
            int len = is.read(buf);
            p.destroy();
            return len > 0 && new String(buf, 0, len).contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }
}
