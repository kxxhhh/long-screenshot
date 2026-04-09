package com.wanjian.longscreenshot.scroll;

/**
 * 滚动过程事件回调。所有回调均在主线程执行。
 */
public interface ScrollCallback {

    /**
     * 每次滚动步骤完成后触发，可用于触发截图。
     *
     * @param totalScrolledPx 从开始到当前累计滚动的像素总量。
     */
    void onScrollSettled(int totalScrolledPx);

    /**
     * 检测到页面已滚动到底部（连续 N 帧内容像素无变化）。
     *
     * <p>实现类在此回调后应自动停止滚动。
     */
    void onReachedBottom();

    /**
     * 滚动过程中发生错误。
     *
     * @param e 错误原因。
     */
    void onError(Throwable e);
}
