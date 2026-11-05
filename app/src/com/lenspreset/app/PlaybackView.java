package com.lenspreset.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.lenspreset.adapter.PlaybackGeometry;

import java.util.List;

/**
 * The playback screen: one picture full screen in its physical proportions, a band at the top
 * (position, file name) and one at the bottom (exposure, date, keys), as the camera's own playback
 * lays them out. Canvas-drawn like OverlayView: the camera's font has no symbol glyphs.
 */
public final class PlaybackView extends View {
    /** Each press of Up or ? / Trash shows less, then everything again (the camera's DISP). */
    enum Info { FULL, NAME, NONE }

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint band = new Paint();
    private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();

    private Bitmap bitmap;
    private int rotation;
    private double pixelAspect = 1;
    private Info info = Info.FULL;
    private String position = "";
    private String name = "";
    private String exposure = "";
    private String detail = "";
    private String legend = "";
    private String message = "";
    private boolean messageWarn;

    /** Inflated from res/layout/main.xml. */
    public PlaybackView(Context context, AttributeSet attrs) {
        super(context, attrs);
        band.setColor(Color.argb(150, 0, 0, 0));
    }

    /** The picture to show, already decoded; the view does not recycle it. */
    void setPicture(Bitmap b, int rotationDegrees) {
        bitmap = b;
        rotation = rotationDegrees;
        invalidate();
    }

    Bitmap picture() {
        return bitmap;
    }

    void setPixelAspect(double aspect) {
        pixelAspect = aspect > 0 ? aspect : 1;
        invalidate();
    }

    void setTexts(String position, String name, String exposure, String detail) {
        this.position = position;
        this.name = name;
        this.exposure = exposure;
        this.detail = detail;
        invalidate();
    }

    void setLegend(String legend) {
        this.legend = legend;
        invalidate();
    }

    void setMessage(String message, boolean warn) {
        this.message = message == null ? "" : message;
        this.messageWarn = warn;
        invalidate();
    }

    void cycleInfo() {
        info = Info.values()[(info.ordinal() + 1) % Info.values().length];
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        c.drawColor(Color.BLACK);
        if (bitmap != null && !bitmap.isRecycled()) drawPicture(c, w, h);

        float size = h / 22f;
        float pad = size * 0.4f;
        if (info != Info.NONE) {
            float bandBottom = pad * 2 + size;
            c.drawRect(0, 0, w, bandBottom, band);
            text.setTextSize(size * 0.85f);
            text.setColor(Color.rgb(245, 177, 92));
            float y = pad + size * 0.85f;
            c.drawText(position, pad, y, text);
            float left = pad + text.measureText(position) + size;
            text.setColor(Color.WHITE);
            String n = TextFit.ellipsize(text, name, w - pad - left);
            c.drawText(n, w - pad - text.measureText(n), y, text);
        }
        if (info == Info.FULL) {
            text.setTextSize(size * 0.75f);
            List<String> keys = TextFit.wrap(text, legend, w - 2 * pad);
            int lines = keys.size() + (exposure.length() > 0 ? 1 : 0) + (detail.length() > 0 ? 1 : 0);
            if (lines > 0) {
                float top = h - pad * 2 - lines * size * 0.95f;
                c.drawRect(0, top, w, h, band);
                float y = top + pad + size * 0.75f;
                text.setTextSize(size * 0.85f);
                text.setColor(Color.WHITE);
                if (exposure.length() > 0) {
                    c.drawText(TextFit.ellipsize(text, exposure, w - 2 * pad), pad, y, text);
                    y += size * 0.95f;
                }
                text.setTextSize(size * 0.75f);
                text.setColor(Color.rgb(200, 200, 200));
                if (detail.length() > 0) {
                    c.drawText(TextFit.ellipsize(text, detail, w - 2 * pad), pad, y, text);
                    y += size * 0.95f;
                }
                text.setColor(Color.rgb(170, 170, 170));
                for (String l : keys) {
                    c.drawText(l, pad, y, text);
                    y += size * 0.95f;
                }
            }
        }
        if (message.length() > 0) {
            text.setTextSize(size * 0.9f);
            List<String> lines = TextFit.wrap(text, message, w * 0.8f);
            float lh = size * 1.1f;
            float top = (h - lines.size() * lh) / 2;
            c.drawRect(w * 0.08f, top - pad, w * 0.92f, top + lines.size() * lh + pad, band);
            text.setColor(messageWarn ? Color.rgb(255, 196, 80) : Color.WHITE);
            float y = top + size * 0.9f;
            for (String l : lines) {
                c.drawText(l, (w - text.measureText(l)) / 2, y, text);
                y += lh;
            }
        }
    }

    private void drawPicture(Canvas c, int w, int h) {
        int bw = bitmap.getWidth();
        int bh = bitmap.getHeight();
        float[] fit = PlaybackGeometry.fit(bw, bh, rotation, w, h, pixelAspect);
        matrix.reset();
        // Turn about the picture's centre, then place the turned picture's corner at the origin.
        matrix.postRotate(rotation, bw / 2f, bh / 2f);
        boolean turned = rotation == 90 || rotation == 270;
        float dx = turned ? (bh - bw) / 2f : 0;
        float dy = turned ? (bw - bh) / 2f : 0;
        matrix.postTranslate(dx, dy);
        matrix.postScale(fit[0], fit[1]);
        matrix.postTranslate(fit[2], fit[3]);
        c.drawBitmap(bitmap, matrix, image);
    }
}
