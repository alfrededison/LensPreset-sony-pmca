package com.lenspreset.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.lenspreset.core.engine.Message;
import com.lenspreset.core.engine.Hud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cardBounds = new RectF();

    private Rect video;
    private String title = "";
    private String subtitle = "";
    private String status = "";
    private String legend = "";
    private List<Hud.Chip> chips = Collections.emptyList();
    private List<Hud.Chip> options = Collections.emptyList();
    private List<Line> messages = Collections.emptyList();
    private List<String> guides = Collections.emptyList();
    private DetailLevel detailLevel = DetailLevel.FULL;
    private String selectedControl = Hud.EV;
    private final Map<String, RectF> controlBounds = new LinkedHashMap<String, RectF>();
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

    void show(String title, String subtitle, List<Hud.Chip> chips, List<Line> messages, List<String> guides) {
        this.title = title;
        this.subtitle = subtitle;
        this.chips = new ArrayList<Hud.Chip>();
        this.options = new ArrayList<Hud.Chip>();
        for (Hud.Chip chip : chips) {
            if (chip.presetOption) this.options.add(chip);
            else this.chips.add(chip);
        }
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

    void setSelectedControl(String key) {
        selectedControl = key;
        invalidate();
    }

    String controlAt(float x, float y) {
        for (Map.Entry<String, RectF> entry : controlBounds.entrySet())
            if (entry.getValue().contains(x, y)) return entry.getKey();
        return null;
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
        controlBounds.clear();
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
        float optionsTop = bandBottom + size * 0.15f;
        List<ChipBox> optionBoxes = layoutChips(options, size, w - 2 * pad);
        if (!optionBoxes.isEmpty())
            bandBottom = optionsTop + optionBoxes.get(optionBoxes.size() - 1).bounds.bottom + pad;
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
        for (ChipBox box : optionBoxes) drawChip(c, box, pad, optionsTop, size, true);

        // Bottom: legend, chips, then messages above them.
        List<String> lines = new ArrayList<String>();
        text.setTextSize(size * 0.75f);
        if (detailLevel != DetailLevel.NO_HINTS_OR_KEYS && legend.length() > 0)
            lines.addAll(TextFit.wrap(text, legend, w - 2 * pad));
        float legendHeight = lines.size() * size * 0.95f;

        List<ChipBox> boxes = layoutChips(chips, size, w - 2 * pad);
        float chipsHeight = boxes.isEmpty() ? 0 : boxes.get(boxes.size() - 1).bounds.bottom;
        float gap = size * 0.3f;
        float legendTop = h - pad - legendHeight;
        float chipsTop = legendTop - (legendHeight > 0 && chipsHeight > 0 ? gap : 0) - chipsHeight;

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
        float messagesTop = chipsTop - (shown.isEmpty() ? 0 : gap) - messagesHeight;
        c.drawRect(0, messagesTop - pad, w, h, band);

        float y = legendTop + size * 0.75f;
        text.setTextSize(size * 0.75f);
        text.setColor(Color.rgb(170, 170, 170));
        for (String line : lines) {
            c.drawText(line, pad, y, text);
            y += size * 0.95f;
        }
        for (ChipBox box : boxes) {
            drawChip(c, box, pad, chipsTop, size, false);
        }
        text.setFakeBoldText(false);
        text.setTextSize(size * 0.9f);
        y = messagesTop + size * 0.9f;
        for (Line l : shown) {
            text.setColor(colorOf(l.level));
            c.drawText(l.text, pad, y, text);
            y += size * 1.1f;
        }
    }

    private static final class ChipBox {
        final String label;
        final String value;
        final RectF bounds;
        final String controlKey;

        ChipBox(String label, String value, RectF bounds, String controlKey) {
            this.label = label;
            this.value = value;
            this.bounds = bounds;
            this.controlKey = controlKey;
        }
    }

    private void drawChip(Canvas c, ChipBox box, float x, float y, float size, boolean option) {
        boolean selected = selectedControl.equals(box.controlKey);
        boolean highlighted = option || selected;
        cardBounds.set(box.bounds);
        cardBounds.offset(x, y);
        if (box.controlKey != null) controlBounds.put(box.controlKey, new RectF(cardBounds));
        card.setStyle(Paint.Style.FILL);
        card.setColor(selected ? Color.argb(240, 105, 65, 20)
                : highlighted ? Color.argb(225, 53, 43, 29) : Color.argb(220, 47, 49, 54));
        c.drawRoundRect(cardBounds, size * 0.22f, size * 0.22f, card);
        card.setStyle(Paint.Style.STROKE);
        card.setStrokeWidth(selected ? 2.5f : 1);
        card.setColor(highlighted ? Color.rgb(245, 177, 92) : Color.argb(75, 255, 255, 255));
        c.drawRoundRect(cardBounds, size * 0.22f, size * 0.22f, card);

        text.setFakeBoldText(false);
        text.setTextSize(size * 0.55f);
        text.setColor(highlighted ? Color.rgb(245, 177, 92) : Color.rgb(185, 189, 198));
        c.drawText(box.label, cardBounds.centerX() - text.measureText(box.label) / 2,
                cardBounds.top + size * 0.62f, text);
        text.setFakeBoldText(true);
        text.setTextSize(size * 0.87f);
        text.setColor(selected ? Color.rgb(255, 210, 120) : Color.WHITE);
        c.drawText(box.value, cardBounds.centerX() - text.measureText(box.value) / 2,
                cardBounds.top + size * 1.65f, text);
        text.setFakeBoldText(false);
    }

    /** Measure and draw with the same fonts; wrap complete cards instead of splitting values. */
    private List<ChipBox> layoutChips(List<Hud.Chip> items, float size, float width) {
        List<ChipBox> boxes = measureChips(items, size, width);
        float gap = size * 0.3f;
        // Use the fewest rows, then balance their widths to avoid a lone card on the last row.
        int n = boxes.size();
        int[] rows = new int[n + 1];
        int[] next = new int[n];
        float[] slack = new float[n + 1];
        for (int i = n - 1; i >= 0; i--) {
            rows[i] = Integer.MAX_VALUE;
            float used = 0;
            for (int j = i; j < n; j++) {
                used += (j == i ? 0 : gap) + boxes.get(j).bounds.width();
                if (used > width + 0.01f) break;
                int count = 1 + rows[j + 1];
                float cost = (width - used) * (width - used) + slack[j + 1];
                if (count < rows[i] || (count == rows[i] && cost < slack[i])) {
                    rows[i] = count;
                    slack[i] = cost;
                    next[i] = j + 1;
                }
            }
        }
        float y = 0;
        for (int i = 0; i < n; y += size * 2 + gap) {
            float x = 0;
            int end = next[i];
            while (i < end) {
                RectF bounds = boxes.get(i++).bounds;
                bounds.offsetTo(x, y);
                x += bounds.width() + gap;
            }
        }
        text.setFakeBoldText(false);
        return boxes;
    }

    private List<ChipBox> measureChips(List<Hud.Chip> items, float size, float width) {
        List<ChipBox> boxes = new ArrayList<ChipBox>();
        float inset = size * 0.4f;
        for (Hud.Chip chip : items) {
            text.setTextSize(size * 0.55f);
            text.setFakeBoldText(false);
            String label = TextFit.ellipsize(text, chip.label, width - 2 * inset);
            float labelWidth = text.measureText(label);
            text.setTextSize(size * 0.87f);
            text.setFakeBoldText(true);
            String value = TextFit.ellipsize(text, chip.value, width - 2 * inset);
            float chipWidth = Math.max(size * 1.8f, Math.max(labelWidth, text.measureText(value)) + 2 * inset);
            chipWidth = Math.min(width, chipWidth);
            boxes.add(new ChipBox(label, value, new RectF(0, 0, chipWidth, size * 2), chip.controlKey));
        }
        text.setFakeBoldText(false);
        return boxes;
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
