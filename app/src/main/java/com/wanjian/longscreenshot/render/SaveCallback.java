package com.wanjian.longscreenshot.render;

/**
 * 长截图保存到本地文件的结果回调。
 */
public interface SaveCallback {

    /**
     * 保存成功时回调。
     *
     * @param filePath 保存后的文件绝对路径
     */
    void onSaved(String filePath);

    /**
     * 保存失败时回调。
     *
     * @param e 失败原因
     */
    void onError(Throwable e);
}
