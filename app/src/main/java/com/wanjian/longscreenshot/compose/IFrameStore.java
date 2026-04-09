package com.wanjian.longscreenshot.compose;

import java.util.List;

/**
 * 帧存储接口，纯数据存取，不包含任何业务逻辑。
 */
public interface IFrameStore {

    /**
     * 追加一帧到存储。
     *
     * @param frame 已处理好的帧数据
     */
    void append(Frame frame);

    /**
     * 返回所有已存储的帧列表（按追加顺序）。
     *
     * @return 不可修改或可直接遍历的帧列表
     */
    List<Frame> getAll();

    /**
     * 清空所有已存储的帧。
     */
    void clear();

    /**
     * 计算所有帧的有效高度之和（各帧 offsetPx 累加）。
     *
     * @return 总像素高度
     */
    int getTotalHeight();
}
