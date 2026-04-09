package com.wanjian.longscreenshot.render;

import android.graphics.Bitmap;

/**
 * Simple in-memory implementation of {@link IPreviewRenderer}.
 * Stores the latest preview bitmap; actual display is delegated to the caller via a callback.
 */
public class PreviewRendererImpl implements IPreviewRenderer {

    private Bitmap current;
    private OnPreviewUpdateListener listener;

    public interface OnPreviewUpdateListener {
        void onUpdate(Bitmap bitmap);
    }

    public PreviewRendererImpl() {}

    public void setListener(OnPreviewUpdateListener listener) {
        this.listener = listener;
    }

    @Override
    public void updatePreview(Bitmap partial) {
        current = partial;
        if (listener != null) listener.onUpdate(partial);
    }

    @Override
    public void clear() {
        current = null;
        if (listener != null) listener.onUpdate(null);
    }

    public Bitmap getCurrent() {
        return current;
    }
}
