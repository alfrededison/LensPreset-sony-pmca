package com.lenspreset.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.lenspreset.core.engine.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Drawn over the live view: composition guides, the preset and kit in use, the settings as chips,
 * and the hints and notices. Everything is Canvas-drawn because the camera's font has no symbol
 * glyphs and the layout must fit 640x480.
 */
public final class OverlayView extends View {
    static final class Line {
        final String text;
        final Message.Level level;

        Line(String text, Message.Level level) {
            this.text = text;
            this.level = level;
        }
    }

    private static final int MAX_MESSAGE_LINES = 5;

    /** Each press removes another layer of the live-view overlay, then restores all layers. */
    private enum DetailLevel { FULL, NO_HINTS, NO_HINTS_OR_KEYS, HIDDEN }

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint band = new Paint();
    private final Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Rect video;
    private String title = "";
    private String subtitle = "";
    private String status = "";
    private String legend = "";
    private List<String> chips = Collections.emptyList();
    private List<Line> messages = Collections.emptyList();
    private List<String> guides = Collections.emptyList();
    private DetailLevel detailLevel = DetailLevel.FULL;
    /** Runs once, on the first draw: the log records when the screen first showed something. */
    private Runnable onFirstDraw;
    private boolean statusWarn;
    /** A full-screen list is open, covering the live-view overlay. */
    private boolean underPanel;

    /** Inflated from res/layout/main.xml. */
    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        band.setColor(Color.argb(150, 0, 0, 0));
        guide.setStrokeWidth(1.5f);
    }

    void setOnFirstDraw(Runnable r) {
        onFirstDraw = r;
    }

    void setVideoRect(Rect r) {
        video = r;
        invalidate();
    }

    void show(String title, String subtitle, List<String> chips, List<Line> messages, List<String> guides) {
        this.title = title;
        this.subtitle = subtitle;
        this.chips = chips;
        this.messages = messages;
        this.guides = guides;
        invalidate();
    }

    void setStatus(String status) {
        setStatus(status, false);
    }

    /** {@code warn} draws it in amber: something did not work. */
    void setStatus(String status, boolean warn) {
        this.status = status == null ? "" : status;
        this.statusWarn = warn;
        invalidate();
    }

    void setLegend(String legend) {
        this.legend = legend;
        invalidate();
    }

    void setUnderPanel(boolean under) {
        underPanel = under;
        invalidate();
    }

    void cycleDetails() {
        DetailLevel[] levels = DetailLevel.values();
        detailLevel = levels[(detailLevel.ordinal() + 1) % levels.length];
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (onFirstDraw != null) {
            Runnable r = onFirstDraw;
            onFirstDraw = null;
            r.run();
        }
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        if (underPanel) return;
        if (detailLevel == DetailLevel.HIDDEN) return;
        Rect v = video != null ? video : new Rect(0, 0, w, h);
        drawGuides(c, v);

        float size = h / 22f;
        float pad = size * 0.4f;

        // Top band: preset, kit, status.
        float top = pad + size * 1.05f;
        float bandBottom = top + pad + (subtitle.length() > 0 ? size * 0.75f + pad : 0);
        c.drawRect(0, 0, w, bandBottom, band);
        text.setTextSize(size * 0.75f);
        String statusText = TextFit.ellipsize(text, status, w * 0.32f);
        float statusWidth = text.measureText(statusText);
        text.setTextSize(size * 1.05f);
        text.setColor(Color.WHITE);
        text.setFakeBoldText(true);
        c.drawText(TextFit.ellipsize(text, title, w - 3 * pad - statusWidth), pad, top, text);
        text.setFakeBoldText(false);
        if (status.length() > 0) {
            text.setTextSize(size * 0.75f);
            text.setColor(statusWarn ? Color.rgb(255, 196, 80) : Color.rgb(140, 220, 140));
            c.drawText(statusText, w - pad - statusWidth, top, text);
        }
        if (subtitle.length() > 0) {
            text.setTextSize(size * 0.75f);
            text.setColor(Color.rgb(200, 200, 200));
            c.drawText(TextFit.ellipsize(text, subtitle, w - 2 * pad), pad, top + pad + size * 0.75f, text);
        }

        // Bottom: legend, chips, then messages above them.
        float y = h - pad;
        List<String> lines = new ArrayList<String>();
        text.setTextSize(size * 0.75f);
        if (detailLevel != DetailLevel.NO_HINTS_OR_KEYS && legend.length() > 0)
            lines.addAll(TextFit.wrap(text, legend, w - 2 * pad));
        float legendHeight = lines.size() * size * 0.95f;

        text.setTextSize(size * 0.9f);
        text.setFakeBoldText(true);
        List<String> chipLines = TextFit.chips(text, chips, w - 2 * pad);
        text.setFakeBoldText(false);
        float chipsHeight = chipLines.size() * size * 1.15f;

        List<Line> shown = new ArrayList<Line>();
        if (detailLevel == DetailLevel.FULL) {
            text.setTextSize(size * 0.9f);
            for (Line m : messages) {
                for (String part : TextFit.wrap(text, m.text, w - 2 * pad)) {
                    if (shown.size() >= MAX_MESSAGE_LINES) break;
                    shown.add(new Line(part, m.level));
                }
            }
        }
        float messagesHeight = shown.size() * size * 1.1f;
        float bottomBand = legendHeight + chipsHeight + messagesHeight + 2 * pad;
        c.drawRect(0, h - bottomBand, w, h, band);

        text.setTextSize(size * 0.75f);
        text.setColor(Color.rgb(170, 170, 170));
        for (int i = lines.size() - 1; i >= 0; i--) {
            c.drawText(lines.get(i), pad, y, text);
            y -= size * 0.95f;
        }
        text.setTextSize(size * 0.9f);
        text.setColor(Color.WHITE);
        text.setFakeBoldText(true);
        for (int i = chipLines.size() - 1; i >= 0; i--) {
            c.drawText(chipLines.get(i), pad, y, text);
            y -= size * 1.15f;
        }
        text.setFakeBoldText(false);
        text.setTextSize(size * 0.9f);
        for (int i = shown.size() - 1; i >= 0; i--) {
            Line l = shown.get(i);
            text.setColor(colorOf(l.level));
            c.drawText(l.text, pad, y, text);
            y -= size * 1.1f;
        }
    }

    private void drawGuides(Canvas c, Rect v) {
        guide.setColor(Color.argb(110, 255, 255, 255));
        if (guides.contains("thirds")) {
            for (int i = 1; i <= 2; i++) {
                float x = v.left + v.width() * i / 3f;
                float y = v.top + v.height() * i / 3f;
                c.drawLine(x, v.top, x, v.bottom, guide);
                c.drawLine(v.left, y, v.right, y, guide);
            }
        }
        if (guides.contains("center")) {
            float cx = v.exactCenterX();
            float cy = v.exactCenterY();
            float r = v.height() / 20f;
            c.drawLine(cx - r, cy, cx + r, cy, guide);
            c.drawLine(cx, cy - r, cx, cy + r, guide);
        }
        if (guides.contains("horizon")) {
            guide.setColor(Color.argb(140, 255, 220, 120));
            float y = v.exactCenterY();
            c.drawLine(v.left + v.width() * 0.1f, y, v.right - v.width() * 0.1f, y, guide);
        }
    }

    private static int colorOf(Message.Level level) {
        switch (level) {
            case WARN: return Color.rgb(255, 196, 80);
            case INFO: return Color.rgb(150, 200, 255);
            default: return Color.WHITE;
        }
    }

}
