package com.wanjian.longscreenshot.capture;

import android.graphics.Rect;

/**
 * 屏幕截图接口，抽象不同截图实现（SurfaceControl / MediaProjection 等）的差异。
 */
public interface IScreenCapture {

    /**
     * 异步截取当前屏幕，结果通过 {@link CaptureCallback} 回调。
     *
     * <p>实现类应在子线程执行截图操作，并将结果 post 回主线程。
     *
     * @param callback 截图结果回调，包含裁剪后的 Bitmap 或错误信息。
     */
    void capture(CaptureCallback callback);

    /**
     * 设置系统 UI 的 insets，用于裁剪截图中的状态栏和导航栏区域。
     *
     * <p>应在首次 {@link #capture} 前调用，或在系统 UI 尺寸变化时更新。
     *
     * @param insets 系统 UI 占用区域（top = 状态栏高度，bottom = 导航栏高度）。
     */
    void setInsets(Rect insets);

    /**
     * 释放截图相关资源（如反射缓存、线程池等）。
     * 不再使用时应调用此方法。
     */
    void release();
}
