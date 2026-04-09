package com.wanjian.longscreenshot.compose;

import android.graphics.Bitmap;

/**
 * 帧合成业务接口，负责裁剪重叠区域、生成预览图和最终长图。
 */
public interface IFrameComposer {

    /**
     * 追加一帧原始截图。
     * 内部根据 scrolledPx 裁剪掉与上一帧重叠的区域后，将有效部分存入 IFrameStore。
     *
     * @param raw       原始截图 Bitmap（屏幕全高）
     * @param scrolledPx 自上一帧以来页面实际滚动的像素数
     */
    void appendFrame(Bitmap raw, int scrolledPx);

    /**
     * 返回当前已合成内容的缩略预览图，用于截图过程中实时展示进度。
     *
     * @return 预览 Bitmap，可能为 null（尚无帧时）
     */
    Bitmap getPreview();

    /**
     * 将所有已存储的帧拼接为完整长图并返回。
     * 调用方负责在不再需要时 recycle 返回值。
     *
     * @return 完整长截图 Bitmap
     */
    Bitmap compose();

    /**
     * 重置合成器状态，清空所有已存储的帧，释放相关资源。
     */
    void reset();
}
