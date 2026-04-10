package com.wellorbetter.longscreenshot.capture;

import android.graphics.Bitmap;

/**
 * 截图结果回调。回调方法均在主线程执行。
 */
public interface CaptureCallback {

    /**
     * 截图成功。
     *
     * @param frame 裁剪后的屏幕 Bitmap（已去除状态栏 / 导航栏区域）。
     *              调用方使用完毕后应调用 {@code frame.recycle()} 释放内存。
     */
    void onCaptured(Bitmap frame);

    /**
     * 截图失败。
     *
     * @param e 失败原因，可用于日志或 UI 提示。
     */
    void onError(Throwable e);
}
