package com.wanjian.longscreenshot.render;

import android.graphics.Bitmap;

/**
 * 预览渲染接口，在截图过程中实时展示合成进度。
 */
public interface IPreviewRenderer {

    /**
     * 更新预览画面，展示截图过程中的实时进度图。
     *
     * @param partial 当前已合成的部分预览 Bitmap
     */
    void updatePreview(Bitmap partial);

    /**
     * 清空预览内容，恢复到初始空白状态。
     */
    void clear();
}
