package com.wanjian.longscreenshot.scroll;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;

/**
 * 基于无障碍服务的滚动控制器。
 *
 * <p>使用 {@link LongScrollAccessibilityService} 注入系统级手势，无需 root 或 adb，
 * 但需要用户在系统设置中手动启用无障碍服务。
 *
 * <p>与 {@link InputManagerScrollController} 的区别：
 * <ul>
 *   <li>本类适用于普通用户场景（与 MediaProjection 配套使用）</li>
 *   <li>InputManagerScrollController 需要 Shizuku/adb 权限</li>
 * </ul>
 */
public class AccessibilityScrollController implements IScrollController {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile boolean running = false;
    private int totalScrolledPx = 0;
    private ScrollConfig config;
    private ScrollCallback callback;

    // 连续相同帧计数，用于检测到底
    private static final int BOTTOM_DETECT_THRESHOLD = 3;
    private int sameFrameCount = 0;

    @Override
    public void start(ScrollConfig config, ScrollCallback callback) {
        if (running) return;

        LongScrollAccessibilityService service = LongScrollAccessibilityService.getInstance();
        if (service == null) {
            mainHandler.post(() -> callback.onError(
                    new IllegalStateException(
                            "无障碍服务未启用，请前往系统设置 → 无障碍 → LongScreenshot 开启")));
            return;
        }

        this.config = config;
        this.callback = callback;
        this.running = true;
        this.totalScrolledPx = 0;
        this.sameFrameCount = 0;

        scheduleNextStep(service);
    }

    private void scheduleNextStep(LongScrollAccessibilityService service) {
        if (!running) return;

        mainHandler.postDelayed(() -> {
            if (!running) return;

            LongScrollAccessibilityService svc = LongScrollAccessibilityService.getInstance();
            if (svc == null) {
                running = false;
                callback.onError(new IllegalStateException("无障碍服务已断开"));
                return;
            }

            float fromY = config.targetY;
            float toY = config.targetY - config.stepPx;
            // 确保 toY 不超出屏幕顶部
            if (toY < 0) toY = 0;

            svc.swipe(config.targetX, fromY, toY, Math.min(config.intervalMs / 2, 200),
                    new AccessibilityService.GestureResultCallback() {
                        @Override
                        public void onCompleted(android.accessibilityservice.GestureDescription gestureDescription) {
                            if (!running) return;
                            totalScrolledPx += config.stepPx;
                            // 通知 orchestrator 截图
                            mainHandler.post(() -> {
                                if (running) callback.onScrollSettled(totalScrolledPx);
                            });
                            // 安排下一步
                            scheduleNextStep(svc);
                        }

                        @Override
                        public void onCancelled(android.accessibilityservice.GestureDescription gestureDescription) {
                            if (!running) return;
                            sameFrameCount++;
                            if (sameFrameCount >= BOTTOM_DETECT_THRESHOLD) {
                                running = false;
                                mainHandler.post(() -> callback.onReachedBottom());
                            } else {
                                scheduleNextStep(svc);
                            }
                        }
                    });
        }, config.intervalMs);
    }

    @Override
    public void stop() {
        running = false;
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public boolean isScrolling() {
        return running;
    }
}
