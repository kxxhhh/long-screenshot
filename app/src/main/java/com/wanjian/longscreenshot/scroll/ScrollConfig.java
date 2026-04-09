package com.wanjian.longscreenshot.scroll;

/**
 * 自动滚动参数配置。
 */
public class ScrollConfig {

    /** 每次滚动的像素步长（向上滑动 = 内容向下移动），单位 px。 */
    public final int stepPx;

    /** 相邻两次滚动的时间间隔，单位 ms。 */
    public final long intervalMs;

    /** 触摸事件的 X 坐标（屏幕中水平位置），单位 px。 */
    public final float targetX;

    /** 触摸事件的起始 Y 坐标，单位 px。 */
    public final float targetY;

    public ScrollConfig(int stepPx, long intervalMs, float targetX, float targetY) {
        this.stepPx = stepPx;
        this.intervalMs = intervalMs;
        this.targetX = targetX;
        this.targetY = targetY;
    }
}
