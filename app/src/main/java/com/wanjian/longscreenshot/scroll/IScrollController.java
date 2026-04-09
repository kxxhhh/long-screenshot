package com.wanjian.longscreenshot.scroll;

/**
 * 滚动控制器接口，抽象不同的滚动注入方式。
 */
public interface IScrollController {

    /**
     * 按照给定配置开始自动滚动。
     *
     * <p>实现类应在内部创建循环机制（如 Handler.postDelayed），每隔
     * {@link ScrollConfig#intervalMs} 毫秒向下滚动 {@link ScrollConfig#stepPx} 像素，
     * 直到调用 {@link #stop()} 或检测到已到达底部。
     *
     * @param config   滚动参数配置。
     * @param callback 滚动过程中的事件回调。
     */
    void start(ScrollConfig config, ScrollCallback callback);

    /**
     * 停止自动滚动并释放相关资源。
     *
     * <p>实现类应注入 ACTION_UP 事件并取消所有待执行的 Handler 回调。
     */
    void stop();

    /**
     * 查询当前是否正在滚动中。
     *
     * @return true 表示滚动进行中；false 表示已停止或尚未启动。
     */
    boolean isScrolling();
}
