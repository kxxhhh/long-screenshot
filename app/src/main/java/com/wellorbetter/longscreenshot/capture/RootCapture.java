package com.wellorbetter.longscreenshot.capture;

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
 * <p>通过 {@code su -c screencap} 调用系统截图工具。
 * 优先从 stdout 直接读取 PNG 流（无需临时文件），失败则 fallback 到临时文件方式。
 */
public class RootCapture implements IScreenCapture {

    private static final String TMP_PATH = "/data/local/tmp/ls_cap.png";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final HandlerThread captureThread;
    private final Handler captureHandler;

    private Rect insets = new Rect();

    public RootCapture(File cacheDir) {
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
            try {
                // 优先：从 stdout 直接读 PNG 流，无需写文件
                Bitmap raw = captureViaStdout();

                // Fallback：写临时文件再读
                if (raw == null) {
                    raw = captureViaFile();
                }

                if (raw == null) {
                    mainHandler.post(() -> callback.onError(
                            new IOException("无法解码截图")));
                    return;
                }

                // 裁剪 insets
                Bitmap result = cropInsets(raw);
                mainHandler.post(() -> callback.onCaptured(result));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private Bitmap captureViaStdout() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "screencap -p"});
            InputStream is = p.getInputStream();
            Bitmap bmp = BitmapFactory.decodeStream(is);
            is.close();
            p.waitFor();
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap captureViaFile() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "su", "-c", "screencap -p " + TMP_PATH + " && chmod 644 " + TMP_PATH
            });
            int exit = p.waitFor();
            if (exit != 0) return null;

            File f = new File(TMP_PATH);
            if (!f.exists() || f.length() == 0) return null;

            Bitmap bmp = BitmapFactory.decodeFile(TMP_PATH);
            f.delete();
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap cropInsets(Bitmap raw) {
        int cropTop = insets.top;
        int cropHeight = raw.getHeight() - insets.top - insets.bottom;
        if (cropHeight <= 0 || cropHeight > raw.getHeight()) {
            return raw;
        }
        Bitmap cropped = Bitmap.createBitmap(raw, 0, cropTop, raw.getWidth(), cropHeight);
        if (cropped != raw) raw.recycle();
        return cropped;
    }

    @Override
    public void release() {
        captureThread.quitSafely();
    }

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
