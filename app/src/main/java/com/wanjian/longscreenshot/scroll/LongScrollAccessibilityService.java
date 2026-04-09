package com.wanjian.longscreenshot.scroll;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;

import java.lang.ref.WeakReference;

/**
 * 无障碍服务实现，提供系统级手势注入能力。
 *
 * <p>使用前需在系统设置 → 无障碍 中启用本服务。
 * 使用 WeakReference 持有自身，防止 Activity 内存泄漏。
 */
public class LongScrollAccessibilityService extends AccessibilityService {

    private static WeakReference<LongScrollAccessibilityService> instance;

    /**
     * 获取当前服务实例，未启用时返回 null。
     */
    public static LongScrollAccessibilityService getInstance() {
        return instance != null ? instance.get() : null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = new WeakReference<>(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不需要监听任何事件，仅用于手势注入
    }

    @Override
    public void onInterrupt() {
        // 服务被中断时清理引用
        instance = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    /**
     * 注入单步滑动手势。
     *
     * @param fromX     起始 X
     * @param fromY     起始 Y
     * @param toY       目标 Y（fromY - stepPx 实现上滑）
     * @param durationMs 手势持续时间
     * @param callback  手势执行结果回调
     */
    public void swipe(float fromX, float fromY, float toY, long durationMs,
                      GestureResultCallback callback) {
        Path path = new Path();
        path.moveTo(fromX, fromY);
        path.lineTo(fromX, toY);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs))
                .build();

        dispatchGesture(gesture, callback, null);
    }
}
