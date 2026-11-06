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
 *
 * <p>Zoomed, the picture is drawn larger about a centre (fractions of the upright picture, kept so
 * no edge shows empty), the bottom band keeps only the keys, and a minimap in the bottom-right
 * corner, two outlines, shows which part of the picture is on screen.
 */
public final class PlaybackView extends View {
    /** Each press of Up or ? / Trash shows less, then everything again (the camera's DISP). */
    enum Info { FULL, NAME, NONE }

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint band = new Paint();
    private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint outline = new Paint();
    private final Paint frame = new Paint();
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
    private double zoom = 1;
    private double centreX = 0.5;
    private double centreY = 0.5;

    /** Inflated from res/layout/main.xml. */
    public PlaybackView(Context context, AttributeSet attrs) {
        super(context, attrs);
        band.setColor(Color.argb(150, 0, 0, 0));
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(1);
        outline.setColor(Color.rgb(200, 200, 200));
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(2);
        frame.setColor(Color.rgb(245, 177, 92));
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

    /** Zooms to {@code z} times the whole picture (1: all of it), keeping the centre where it can. */
    void setZoom(double z) {
        zoom = Math.max(1, z);
        if (zoom == 1) {
            centreX = 0.5;
            centreY = 0.5;
        }
        clampCentre();
        invalidate();
    }

    double zoom() {
        return zoom;
    }

    /** Moves the zoomed view a quarter of the screen: {@code dx}, {@code dy} are -1, 0 or 1. */
    void pan(int dx, int dy) {
        float[] p = placement();
        if (p == null) return;
        centreX += dx * 0.25 * getWidth() / p[2];
        centreY += dy * 0.25 * getHeight() / p[3];
        clampCentre();
        invalidate();
    }

    private void clampCentre() {
        float[] p = placement();
        if (p == null) return;
        centreX = PlaybackGeometry.clampCentre(centreX, p[2], getWidth());
        centreY = PlaybackGeometry.clampCentre(centreY, p[3], getHeight());
    }

    /** {@code {left, top, width, height}} of the upright picture in view pixels, or null. */
    private float[] placement() {
        int w = getWidth();
        int h = getHeight();
        if (bitmap == null || bitmap.isRecycled() || w <= 0 || h <= 0) return null;
        boolean turned = rotation == 90 || rotation == 270;
        int tw = turned ? bitmap.getHeight() : bitmap.getWidth();
        int th = turned ? bitmap.getWidth() : bitmap.getHeight();
        float[] fit = PlaybackGeometry.fit(bitmap.getWidth(), bitmap.getHeight(), rotation, w, h, pixelAspect);
        float[] z = PlaybackGeometry.zoom(fit, tw, th, w, h, zoom, centreX, centreY);
        return new float[]{z[2], z[3], tw * z[0], th * z[1]};
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
        float[] p = placement();
        if (p != null) drawTurned(c, bitmap, p[0], p[1], p[2], p[3]);
        boolean zoomed = zoom > 1;

        float size = h / 22f;
        float pad = size * 0.4f;
        float bottomBand = h;
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
            boolean facts = !zoomed;
            int lines = keys.size() + (facts && exposure.length() > 0 ? 1 : 0) + (facts && detail.length() > 0 ? 1 : 0);
            if (lines > 0) {
                float top = h - pad * 2 - lines * size * 0.95f;
                bottomBand = top;
                c.drawRect(0, top, w, h, band);
                float y = top + pad + size * 0.75f;
                text.setTextSize(size * 0.85f);
                text.setColor(Color.WHITE);
                if (facts && exposure.length() > 0) {
                    c.drawText(TextFit.ellipsize(text, exposure, w - 2 * pad), pad, y, text);
                    y += size * 0.95f;
                }
                text.setTextSize(size * 0.75f);
                text.setColor(Color.rgb(200, 200, 200));
                if (facts && detail.length() > 0) {
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
        if (zoomed && p != null && info != Info.NONE) drawMinimap(c, p, w * 0.24f, h * 0.24f, w - pad, bottomBand - pad);
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

    /**
     * An outline of the whole picture, fitted in {@code boxWidth x boxHeight} with its bottom-right
     * corner at (right, bottom), and inside it a frame around the part {@code placement} puts on screen.
     */
    private void drawMinimap(Canvas c, float[] placement, float boxWidth, float boxHeight, float right, float bottom) {
        float[] fit = PlaybackGeometry.fit(bitmap.getWidth(), bitmap.getHeight(), rotation,
                Math.round(boxWidth), Math.round(boxHeight), pixelAspect);
        boolean turned = rotation == 90 || rotation == 270;
        float mw = (turned ? bitmap.getHeight() : bitmap.getWidth()) * fit[0];
        float mh = (turned ? bitmap.getWidth() : bitmap.getHeight()) * fit[1];
        float left = right - mw;
        float top = bottom - mh;
        c.drawRect(left, top, right, bottom, band);
        c.drawRect(left, top, right, bottom, outline);
        float[] s = PlaybackGeometry.shown(placement[0], placement[1], placement[2], placement[3], getWidth(), getHeight());
        c.drawRect(left + s[0] * mw, top + s[1] * mh, left + s[2] * mw, top + s[3] * mh, frame);
    }

    /** Draws {@code b}, turned upright by the picture's rotation, over {@code width x height} from (left, top). */
    private void drawTurned(Canvas c, Bitmap b, float left, float top, float width, float height) {
        int bw = b.getWidth();
        int bh = b.getHeight();
        boolean turned = rotation == 90 || rotation == 270;
        matrix.reset();
        // Turn about the picture's centre, then place the turned picture's corner at the origin.
        matrix.postRotate(rotation, bw / 2f, bh / 2f);
        matrix.postTranslate(turned ? (bh - bw) / 2f : 0, turned ? (bw - bh) / 2f : 0);
        matrix.postScale(width / (turned ? bh : bw), height / (turned ? bw : bh));
        matrix.postTranslate(left, top);
        c.drawBitmap(b, matrix, image);
    }
}
