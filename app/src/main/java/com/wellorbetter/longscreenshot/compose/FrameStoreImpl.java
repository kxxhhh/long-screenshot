package com.wellorbetter.longscreenshot.compose;

import java.util.ArrayList;
import java.util.List;

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
            total += frame.height;
        }
        return total;
    }
}
