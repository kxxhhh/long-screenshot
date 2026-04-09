package com.wanjian.longscreenshot;

/**
 * 截图方案枚举。
 *
 * <ul>
 *   <li>{@link #MEDIA_PROJECTION}：普通用户，弹一次授权窗，无需特殊权限</li>
 *   <li>{@link #SHIZUKU}：adb 用户，通过 Shizuku 调用系统隐藏 API，无弹窗</li>
 *   <li>{@link #ROOT}：Root 设备，调用 su + screencap，最低兼容性兜底</li>
 * </ul>
 */
public enum CaptureStrategy {

    /**
     * MediaProjection 方案：
     * - 截图：MediaProjectionCapture
     * - 滑动：AccessibilityScrollController（或 InputManagerScrollController）
     * - 要求：用户同意屏幕录制授权；Android 10+ 需前台服务
     */
    MEDIA_PROJECTION,

    /**
     * Shizuku 方案：
     * - 截图：SurfaceControlCapture（反射 SurfaceControl.screenshot）
     * - 滑动：InputManagerScrollController（反射 IInputManager）
     * - 要求：Shizuku 已运行并已授权本应用
     */
    SHIZUKU,

    /**
     * Root 方案：
     * - 截图：RootCapture（su + screencap）
     * - 滑动：AccessibilityScrollController（无障碍服务）
     * - 要求：设备已 root，已授权 su
     */
    ROOT
}
