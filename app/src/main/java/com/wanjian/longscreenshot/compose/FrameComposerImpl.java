package com.wanjian.longscreenshot.compose;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;

import java.util.List;

/**
 * IFrameComposer 的默认实现。
 * 通过构造器注入 IFrameStore，实现帧裁剪、预览生成和最终长图合成。
 */
public class FrameComposerImpl implements IFrameComposer {

    /** 预览图最大宽度，降低内存压力 */
    private static final int PREVIEW_MAX_WIDTH = 360;

    private final IFrameStore store;

    public FrameComposerImpl(IFrameStore store) {
        this.store = store;
    }

    @Override
    public void appendFrame(Bitmap raw, int scrolledPx) {
        if (raw == null) return;

        int frameHeight = raw.getHeight();
        int frameWidth = raw.getWidth();

        // 第一帧 scrolledPx=0，直接存整帧
        // 后续帧：overlap = frameHeight - scrolledPx（上一帧末尾与本帧头部重叠的行数）
        // cropY = overlap 时从第 overlap 行开始才是新内容
        int overlap = frameHeight - scrolledPx;
        int cropY = (overlap > 0 && overlap < frameHeight) ? overlap : 0;
        int cropHeight = frameHeight - cropY;

        if (cropHeight <= 0) return;

        Bitmap cropped;
        if (cropY == 0) {
            // 无重叠，直接用整帧（不 copy，节省内存）
            cropped = raw;
        } else {
            cropped = Bitmap.createBitmap(raw, 0, cropY, frameWidth, cropHeight);
        }

        store.append(new Frame(cropped, cropHeight));
    }

    @Override
    public Bitmap getPreview() {
        List<Frame> all = store.getAll();
        if (all.isEmpty()) return null;

        int totalHeight = store.getTotalHeight();
        int frameWidth = all.get(0).bitmap.getWidth();
        if (frameWidth <= 0 || totalHeight <= 0) return null;

        int previewWidth = Math.min(PREVIEW_MAX_WIDTH, frameWidth);
        float scale = (float) previewWidth / frameWidth;
        int previewHeight = (int) (totalHeight * scale);
        if (previewHeight <= 0) return null;

        Bitmap preview = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(preview);
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);

        float offsetY = 0;
        for (Frame frame : all) {
            matrix.setScale(scale, scale);
            matrix.postTranslate(0, offsetY);
            canvas.drawBitmap(frame.bitmap, matrix, null);
            offsetY += frame.offsetPx * scale;
        }

        return preview;
    }

    @Override
    public Bitmap compose() {
        List<Frame> all = store.getAll();
        if (all.isEmpty()) return null;

        int totalHeight = store.getTotalHeight();
        int width = all.get(0).bitmap.getWidth();
        if (width <= 0 || totalHeight <= 0) return null;

        // RGB_565 节省内存（长图可能很大）
        Bitmap result = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(result);

        float offsetY = 0;
        for (Frame frame : all) {
            canvas.drawBitmap(frame.bitmap, 0, offsetY, null);
            offsetY += frame.offsetPx;
        }

        return result;
    }

    @Override
    public void reset() {
        // recycle 所有帧 bitmap 避免 OOM
        for (Frame frame : store.getAll()) {
            if (frame.bitmap != null && !frame.bitmap.isRecycled()) {
                frame.bitmap.recycle();
            }
        }
        store.clear();
    }
}
