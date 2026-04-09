package com.wanjian.longscreenshot.scroll;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import java.lang.reflect.Method;

import com.wanjian.longscreenshot.capture.CaptureCallback;
import com.wanjian.longscreenshot.capture.IScreenCapture;
import com.wanjian.longscreenshot.permission.IPermissionProvider;

/**
 * 通过注入 MotionEvent 到 InputManager 实现自动滚动。
 *
 * <p>需要 {@link IPermissionProvider} 提供具有 Shizuku 授权的 IInputManager 代理，
 * 以及 {@link IScreenCapture} 用于在每步滚动后截图对比像素变化（检测到底部）。
 */
public class InputManagerScrollController implements IScrollController {

    /** 连续判定为"到达底部"所需的像素不变帧数阈值。 */
    private static final int BOTTOM_DETECT_FRAMES = 3;

    private final IPermissionProvider permissionProvider;
    private final IScreenCapture screenCapture;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** IInputManager proxy obtained via reflection (hidden API). */
    private Object iInputManager;
    private Method injectInputEventMethod;
    private ScrollConfig config;
    private ScrollCallback callback;

    private int totalScrolledPx = 0;
    private int unchangedFrameCount = 0;
    private Bitmap lastBitmap;
    private volatile boolean running = false;

    /** 下一步滚动任务，通过 Handler.postDelayed 循环调度。 */
    private final Runnable scrollStep = new Runnable() {
        @Override
        public void run() {
            if (!running) return;

            // TODO: 用 Handler.postDelayed 循环，每次构造 ACTION_MOVE，Y坐标减stepPx
            //   ACTION_MOVE 的 Y 坐标 = config.targetY - totalScrolledPx - config.stepPx，
            //   模拟手指从下往上滑动（内容向下滚动）。每轮 postDelayed 完成一步。
            injectMoveEvent();
            totalScrolledPx += config.stepPx;

            // 每步滚动后截图，用于"到达底部"检测和进度回调
            screenCapture.capture(new CaptureCallback() {
                @Override
                public void onCaptured(Bitmap bitmap) {
                    if (!running) { bitmap.recycle(); return; }
                    boolean reached = checkReachedBottom(bitmap);
                    if (lastBitmap != null) lastBitmap.recycle();
                    lastBitmap = bitmap;

                    // TODO: 累计滑动距离，回调 onScrollSettled
                    //   每完成一步滚动并获取到截图后，调用 callback.onScrollSettled(totalScrolledPx)
                    //   通知上层（Orchestrator）可以进行图像拼接。
                    callback.onScrollSettled(totalScrolledPx);

                    if (reached) {
                        // TODO: 检测到底部时（连续N帧像素不变），回调 onReachedBottom
                        //   当 unchangedFrameCount >= BOTTOM_DETECT_FRAMES 时认为已到达底部，
                        //   调用 stop() 后再回调 onReachedBottom()，确保滚动已停止。
                        stop();
                        callback.onReachedBottom();
                    } else {
                        mainHandler.postDelayed(scrollStep, config.intervalMs);
                    }
                }

                @Override
                public void onError(Throwable e) {
                    // 截图失败不中断滚动，继续下一步
                    mainHandler.postDelayed(scrollStep, config.intervalMs);
                }
            });
        }
    };

    public InputManagerScrollController(IPermissionProvider permissionProvider,
                                         IScreenCapture screenCapture) {
        this.permissionProvider = permissionProvider;
        this.screenCapture = screenCapture;
    }

    @Override
    public void start(ScrollConfig config, ScrollCallback callback) {
        if (running) return;
        this.config = config;
        this.callback = callback;
        this.totalScrolledPx = 0;
        this.unchangedFrameCount = 0;

        // TODO: 通过 IPermissionProvider.getSystemService 拿 IInputManager
        //   调用 permissionProvider.getSystemService(Context.INPUT_SERVICE) 拿到 IBinder，
        //   再调用 IInputManager.Stub.asInterface(binder) 完成类型转换。
        android.os.IBinder binder = permissionProvider.getSystemService(
                android.content.Context.INPUT_SERVICE);
        if (binder == null) {
            callback.onError(new IllegalStateException(
                    "Failed to get IInputManager: no permission or service unavailable"));
            return;
        }
        // TODO: IInputManager is a hidden API — use reflection to obtain the proxy
        //   Class.forName("android.hardware.input.IInputManager$Stub")
        //   .getMethod("asInterface", IBinder.class).invoke(null, binder)
        try {
            Class<?> stubClass = Class.forName("android.hardware.input.IInputManager$Stub");
            iInputManager = stubClass.getMethod("asInterface", IBinder.class).invoke(null, binder);
            injectInputEventMethod = iInputManager.getClass()
                    .getMethod("injectInputEvent", MotionEvent.class, int.class);
        } catch (Exception e) {
            callback.onError(new IllegalStateException("Failed to resolve IInputManager via reflection", e));
            return;
        }
        running = true;

        // TODO: 构造 MotionEvent.ACTION_DOWN，注入起始点
        //   使用 MotionEvent.obtain() 构造 ACTION_DOWN，坐标为 (config.targetX, config.targetY)，
        //   通过 iInputManager.injectInputEvent(event, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC) 注入，
        //   注入后调用 event.recycle() 释放。
        injectDownEvent();
        mainHandler.postDelayed(scrollStep, config.intervalMs);
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        // TODO: stop() 时注入 ACTION_UP，取消Handler回调
        //   构造 MotionEvent.ACTION_UP，坐标为当前手指位置，通过 iInputManager.injectInputEvent 注入，
        //   确保系统触摸状态机正常复位，避免后续触摸事件异常。
        //   同时调用 mainHandler.removeCallbacks(scrollStep) 取消所有待执行的滚动任务。
        mainHandler.removeCallbacks(scrollStep);
        injectUpEvent();
        if (lastBitmap != null) {
            lastBitmap.recycle();
            lastBitmap = null;
        }
    }

    @Override
    public boolean isScrolling() {
        return running;
    }

    // --- 私有辅助方法 ---

    private void injectDownEvent() {
        long now = SystemClock.uptimeMillis();
        MotionEvent ev = MotionEvent.obtain(now, now,
                MotionEvent.ACTION_DOWN, config.targetX, config.targetY, 0);
        injectEvent(ev);
        ev.recycle();
    }

    private void injectMoveEvent() {
        long now = SystemClock.uptimeMillis();
        float currentY = config.targetY - totalScrolledPx - config.stepPx;
        MotionEvent ev = MotionEvent.obtain(now, now,
                MotionEvent.ACTION_MOVE, config.targetX, currentY, 0);
        injectEvent(ev);
        ev.recycle();
    }

    private void injectUpEvent() {
        long now = SystemClock.uptimeMillis();
        float currentY = config.targetY - totalScrolledPx;
        MotionEvent ev = MotionEvent.obtain(now, now,
                MotionEvent.ACTION_UP, config.targetX, currentY, 0);
        injectEvent(ev);
        ev.recycle();
    }

    private void injectEvent(MotionEvent ev) {
        if (iInputManager == null || injectInputEventMethod == null) return;
        try {
            // INJECT_INPUT_EVENT_MODE_ASYNC = 0，异步注入不阻塞当前线程
            injectInputEventMethod.invoke(iInputManager, ev, 0);
        } catch (Exception e) {
            // 注入失败时忽略，不中断滚动流程
        }
    }

    /**
     * 对比当前帧与上一帧，判断内容是否发生变化以检测是否到达底部。
     *
     * @return true 表示连续 {@link #BOTTOM_DETECT_FRAMES} 帧像素无变化，即到达底部。
     */
    private boolean checkReachedBottom(Bitmap current) {
        if (lastBitmap == null || current == null) return false;
        if (lastBitmap.getWidth() != current.getWidth()
                || lastBitmap.getHeight() != current.getHeight()) return false;

        // 取中部行像素做快速采样比较，降低 CPU 开销
        int y = current.getHeight() / 2;
        int sampleStep = Math.max(1, current.getWidth() / 20);
        boolean same = true;
        for (int x = 0; x < current.getWidth(); x += sampleStep) {
            if (lastBitmap.getPixel(x, y) != current.getPixel(x, y)) {
                same = false;
                break;
            }
        }

        unchangedFrameCount = same ? unchangedFrameCount + 1 : 0;
        return unchangedFrameCount >= BOTTOM_DETECT_FRAMES;
    }
}
