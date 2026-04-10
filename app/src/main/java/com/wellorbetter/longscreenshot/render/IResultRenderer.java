package com.wellorbetter.longscreenshot.render;

import android.graphics.Bitmap;

/**
 * 结果渲染接口，负责展示最终长图并提供保存功能。
 */
public interface IResultRenderer {

    /**
     * 展示最终合成的完整长截图。
     *
     * @param result 完整长图 Bitmap
     */
    void showResult(Bitmap result);

    /**
     * 将长截图异步保存到本地文件，通过 SaveCallback 回调通知结果。
     *
     * @param result   待保存的完整长图 Bitmap
     * @param callback 保存结果回调
     */
    void saveToFile(Bitmap result, SaveCallback callback);
}
