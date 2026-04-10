package com.wellorbetter.longscreenshot.render;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;

/**
 * IResultRenderer 的默认实现。
 * 通过回调通知 UI 展示结果，并将长图保存到本地 Pictures/LongScreenshot/ 目录。
 */
public class ResultRendererImpl implements IResultRenderer {

    /**
     * 结果展示监听器，由 Activity 实现以更新 UI。
     */
    public interface OnResultListener {
        void onShow(Bitmap result);
    }

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private OnResultListener listener;

    public ResultRendererImpl(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void setListener(OnResultListener listener) {
        this.listener = listener;
    }

    @Override
    public void showResult(Bitmap result) {
        if (listener != null) {
            listener.onShow(result);
        }
    }

    @Override
    public void saveToFile(Bitmap result, SaveCallback callback) {
        new Thread(() -> {
            try {
                File dir = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        "LongScreenshot");
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new RuntimeException("Failed to create directory: " + dir.getAbsolutePath());
                }
                File file = new File(dir, "long_screenshot_" + System.currentTimeMillis() + ".png");
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    result.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.flush();
                }
                // 通知系统相册扫描新文件
                MediaScannerConnection.scanFile(
                        appContext,
                        new String[]{file.getAbsolutePath()},
                        new String[]{"image/png"},
                        null);
                mainHandler.post(() -> callback.onSaved(file.getAbsolutePath()));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        }).start();
    }
}
