package com.wanjian.longscreenshot.compose;

import java.util.ArrayList;
import java.util.List;

/**
 * IFrameStore 的默认实现，使用 ArrayList 内存存储。
 * 纯数据操作，无任何业务逻辑。
 */
public class FrameStoreImpl implements IFrameStore {

    private final List<Frame> frames = new ArrayList<>();

    @Override
    public void append(Frame frame) {
        frames.add(frame);
    }

    @Override
    public List<Frame> getAll() {
        return frames;
    }

    @Override
    public void clear() {
        frames.clear();
    }

    @Override
    public int getTotalHeight() {
        int total = 0;
        for (Frame frame : frames) {
            total += frame.offsetPx;
        }
        return total;
    }
}
