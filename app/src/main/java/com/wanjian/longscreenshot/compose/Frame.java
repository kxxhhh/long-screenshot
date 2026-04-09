package com.wanjian.longscreenshot.compose;

import android.graphics.Bitmap;

/**
 * 表示一帧截图及其相对上一帧的实际偏移像素数。
 */
public class Frame {

    /** 该帧裁剪后的 Bitmap（已去除与上一帧重叠的部分）。 */
    public final Bitmap bitmap;

    /** 该帧相对于上一帧的有效偏移像素数（即新增内容高度）。 */
    public final int offsetPx;

    public Frame(Bitmap bitmap, int offsetPx) {
        this.bitmap = bitmap;
        this.offsetPx = offsetPx;
    }
}
