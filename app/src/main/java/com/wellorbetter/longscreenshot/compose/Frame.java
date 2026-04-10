package com.wellorbetter.longscreenshot.compose;

import android.graphics.Bitmap;

/**
 * 表示一帧裁剪后的截图。
 */
public class Frame {
    public final Bitmap bitmap;
    public final int height;
    public final int width;
    /** 相对上一帧的 X 偏移（横向滚动时有值） */
    public final int dx;
    /** 相对上一帧的 Y 偏移（纵向滚动时有值） */
    public final int dy;

    /** 兼容旧构造器 */
    public Frame(Bitmap bitmap, int offsetPx) {
        this(bitmap, bitmap.getHeight(), bitmap.getWidth(), 0, offsetPx);
    }

    public Frame(Bitmap bitmap, int height, int width, int dx, int dy) {
        this.bitmap = bitmap;
        this.height = height;
        this.width = width;
        this.dx = dx;
        this.dy = dy;
    }

    /** 兼容旧代码 */
    public int getOffsetPx() {
        return dy != 0 ? dy : height;
    }
}
