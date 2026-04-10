package com.wellorbetter.longscreenshot.compose;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.Log;

import java.util.List;

/**
 * 帧合成器：通过像素对比自动检测相邻帧的滚动方向和偏移量。
 *
 * <p>支持纵向滚动（长图）和横向滚动（宽图）。
 * 算法：对比当前帧与上一帧的像素，在四个方向搜索重叠区域，
 * 取匹配度最高的方向和偏移量。
 */
public class FrameComposerImpl implements IFrameComposer {

    private static final String TAG = "LongSS";
    private static final int PREVIEW_MAX_WIDTH = 360;
    private static final int MATCH_SAMPLE_ROWS = 16;
    private static final int SAMPLE_STEP = 10;
    private static final int PIXEL_TOLERANCE = 30;
    /** 最小新内容像素，低于此值认为没有有效滚动 */
    private static final int MIN_NEW_CONTENT = 20;

    private final IFrameStore store;
    private Bitmap lastFullFrame;

    /** 累计偏移（用于宽图横向拼接） */
    private int totalOffsetX = 0;
    private int totalOffsetY = 0;

    /** 检测到的滚动方向：0=未知, 1=纵向, 2=横向 */
    private int scrollDirection = 0;

    public FrameComposerImpl(IFrameStore store) {
        this.store = store;
    }

    @Override
    public void appendFrame(Bitmap raw, int scrolledPxHint) {
        if (raw == null || raw.isRecycled()) return;

        // 必须先 copy，因为调用方可能 recycle 原始 bitmap
        Bitmap frame = raw.copy(Bitmap.Config.ARGB_8888, false);
        if (frame == null) return;

        if (lastFullFrame == null) {
            // 第一帧
            Bitmap storeCopy = frame.copy(Bitmap.Config.RGB_565, false);
            store.append(new Frame(storeCopy, storeCopy.getHeight(), storeCopy.getWidth(), 0, 0));
            lastFullFrame = frame;
            Log.i(TAG, "First frame stored: " + storeCopy.getWidth() + "x" + storeCopy.getHeight());
            return;
        }

        // 检测滚动偏移
        int[] offset = detectOffset(lastFullFrame, frame);
        int dx = offset[0];
        int dy = offset[1];

        if (dx == 0 && dy == 0) {
            // 没有滚动，跳过
            frame.recycle();
            return;
        }

        Log.i(TAG, "Detected offset: dx=" + dx + " dy=" + dy);

        // 裁剪出新内容
        Bitmap newContent = cropNewContent(frame, dx, dy);
        if (newContent == null) {
            frame.recycle();
            return;
        }

        Bitmap storeCopy = newContent.copy(Bitmap.Config.RGB_565, false);
        if (newContent != frame) newContent.recycle();

        totalOffsetX += Math.abs(dx);
        totalOffsetY += Math.abs(dy);

        store.append(new Frame(storeCopy, storeCopy.getHeight(), storeCopy.getWidth(),
                dx, dy));

        Log.i(TAG, "Appended frame: " + storeCopy.getWidth() + "x" + storeCopy.getHeight()
                + " total frames=" + store.getAll().size());

        lastFullFrame.recycle();
        lastFullFrame = frame;
    }

    /**
     * 检测 curr 相对于 prev 的滚动偏移量。
     * @return [dx, dy]，正值表示内容向左/上移动（用户向右/下滑动）
     */
    private int[] detectOffset(Bitmap prev, Bitmap curr) {
        if (prev.getWidth() != curr.getWidth() || prev.getHeight() != curr.getHeight()) {
            return new int[]{0, 0};
        }

        int w = prev.getWidth();
        int h = prev.getHeight();

        // 尝试纵向滚动（向下）：curr 顶部 = prev 中下部
        if (scrollDirection == 0 || scrollDirection == 1) {
            int vertDown = findVerticalOverlap(prev, curr, h);
            if (vertDown > MIN_NEW_CONTENT && vertDown < h - MIN_NEW_CONTENT) {
                scrollDirection = 1;
                return new int[]{0, h - vertDown}; // dy = 新内容在底部
            }
        }

        // 尝试横向滚动（向右）：curr 左部 = prev 中右部
        if (scrollDirection == 0 || scrollDirection == 2) {
            int horizRight = findHorizontalOverlap(prev, curr, w);
            if (horizRight > MIN_NEW_CONTENT && horizRight < w - MIN_NEW_CONTENT) {
                scrollDirection = 2;
                return new int[]{w - horizRight, 0}; // dx = 新内容在右侧
            }
        }

        return new int[]{0, 0};
    }

    /**
     * 在 prev 底部搜索与 curr 顶部匹配的位置。
     * @return 重叠行数（prev 底部多少行 == curr 顶部多少行）
     */
    private int findVerticalOverlap(Bitmap prev, Bitmap curr, int height) {
        int w = prev.getWidth();
        int sampleRows = Math.min(MATCH_SAMPLE_ROWS, height / 6);
        if (sampleRows < 3) return 0;

        // 从 prev 底部向上搜索
        for (int overlap = height * 4 / 5; overlap >= sampleRows; overlap--) {
            int prevStartY = height - overlap;
            if (matchRegion(prev, 0, prevStartY, curr, 0, 0, w, sampleRows)) {
                return overlap;
            }
        }
        return 0;
    }

    /**
     * 在 prev 右侧搜索与 curr 左侧匹配的位置。
     * @return 重叠列数
     */
    private int findHorizontalOverlap(Bitmap prev, Bitmap curr, int width) {
        int h = prev.getHeight();
        int sampleCols = Math.min(MATCH_SAMPLE_ROWS, width / 6);
        if (sampleCols < 3) return 0;

        for (int overlap = width * 4 / 5; overlap >= sampleCols; overlap--) {
            int prevStartX = width - overlap;
            if (matchRegionH(prev, prevStartX, 0, curr, 0, 0, h, sampleCols)) {
                return overlap;
            }
        }
        return 0;
    }

    /** 比较纵向区域：prev[px, py..py+rows] vs curr[cx, cy..cy+rows] */
    private boolean matchRegion(Bitmap prev, int px, int py,
                                 Bitmap curr, int cx, int cy,
                                 int width, int rows) {
        int mismatch = 0;
        int total = 0;
        for (int row = 0; row < rows; row++) {
            for (int x = 0; x < width; x += SAMPLE_STEP) {
                total++;
                if (!pixelClose(prev.getPixel(px + x, py + row),
                        curr.getPixel(cx + x, cy + row))) {
                    mismatch++;
                }
            }
        }
        return total > 0 && mismatch <= total * 0.05;
    }

    /** 比较横向区域：prev[px..px+cols, py] vs curr[cx..cx+cols, cy] */
    private boolean matchRegionH(Bitmap prev, int px, int py,
                                  Bitmap curr, int cx, int cy,
                                  int height, int cols) {
        int mismatch = 0;
        int total = 0;
        for (int col = 0; col < cols; col++) {
            for (int y = 0; y < height; y += SAMPLE_STEP) {
                total++;
                if (!pixelClose(prev.getPixel(px + col, py + y),
                        curr.getPixel(cx + col, cy + y))) {
                    mismatch++;
                }
            }
        }
        return total > 0 && mismatch <= total * 0.05;
    }

    private boolean pixelClose(int p1, int p2) {
        int dr = Math.abs(((p1 >> 16) & 0xFF) - ((p2 >> 16) & 0xFF));
        int dg = Math.abs(((p1 >> 8) & 0xFF) - ((p2 >> 8) & 0xFF));
        int db = Math.abs((p1 & 0xFF) - (p2 & 0xFF));
        return (dr + dg + db) < PIXEL_TOLERANCE;
    }

    /** 根据偏移方向裁剪出新内容 */
    private Bitmap cropNewContent(Bitmap frame, int dx, int dy) {
        int w = frame.getWidth();
        int h = frame.getHeight();

        if (dy > 0 && dy < h) {
            // 纵向向下滚动：新内容在底部
            int newH = h - (h - dy);
            // 实际上 dy 就是 frameH - overlap，新内容 = 底部 dy 行
            // 不对，dy = h - overlap，所以新内容高度 = dy，从 h-dy 开始
            return Bitmap.createBitmap(frame, 0, h - dy, w, dy);
        }
        if (dx > 0 && dx < w) {
            // 横向向右滚动：新内容在右侧
            return Bitmap.createBitmap(frame, w - dx, 0, dx, h);
        }
        return null;
    }

    @Override
    public Bitmap getPreview() {
        List<Frame> all = store.getAll();
        if (all.isEmpty()) return null;

        // 计算总尺寸
        int totalW, totalH;
        if (scrollDirection == 2) {
            // 横向
            totalW = 0;
            totalH = all.get(0).height;
            for (Frame f : all) totalW += f.width;
        } else {
            // 纵向（默认）
            totalW = all.get(0).width;
            totalH = 0;
            for (Frame f : all) totalH += f.height;
        }

        if (totalW <= 0 || totalH <= 0) return null;

        float scale = Math.min(1f, (float) PREVIEW_MAX_WIDTH / totalW);
        int pw = (int) (totalW * scale);
        int ph = Math.min((int) (totalH * scale), 4096);
        if (pw <= 0 || ph <= 0) return null;

        Bitmap preview = Bitmap.createBitmap(pw, ph, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(preview);
        Matrix matrix = new Matrix();

        float offset = 0;
        for (Frame f : all) {
            matrix.reset();
            matrix.setScale(scale, scale);
            if (scrollDirection == 2) {
                matrix.postTranslate(offset, 0);
                offset += f.width * scale;
            } else {
                matrix.postTranslate(0, offset);
                offset += f.height * scale;
            }
            if (offset > (scrollDirection == 2 ? pw : ph)) break;
            canvas.drawBitmap(f.bitmap, matrix, null);
        }
        return preview;
    }

    @Override
    public Bitmap compose() {
        List<Frame> all = store.getAll();
        if (all.isEmpty()) return null;

        int totalW, totalH;
        if (scrollDirection == 2) {
            totalW = 0;
            totalH = all.get(0).height;
            for (Frame f : all) totalW += f.width;
        } else {
            totalW = all.get(0).width;
            totalH = 0;
            for (Frame f : all) totalH += f.height;
        }

        if (totalW <= 0 || totalH <= 0) return null;

        Log.i(TAG, "Composing: " + totalW + "x" + totalH
                + " frames=" + all.size() + " dir=" + scrollDirection);

        Bitmap result = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(result);

        float offset = 0;
        for (Frame f : all) {
            if (scrollDirection == 2) {
                canvas.drawBitmap(f.bitmap, offset, 0, null);
                offset += f.width;
            } else {
                canvas.drawBitmap(f.bitmap, 0, offset, null);
                offset += f.height;
            }
        }
        return result;
    }

    @Override
    public void reset() {
        for (Frame frame : store.getAll()) {
            if (frame.bitmap != null && !frame.bitmap.isRecycled()) {
                frame.bitmap.recycle();
            }
        }
        store.clear();
        if (lastFullFrame != null) {
            lastFullFrame.recycle();
            lastFullFrame = null;
        }
        scrollDirection = 0;
        totalOffsetX = 0;
        totalOffsetY = 0;
    }
}
