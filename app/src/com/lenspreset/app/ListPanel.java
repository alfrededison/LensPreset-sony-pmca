package com.lenspreset.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * A full-height list over the left of the live view: the preset list, a preset's options, the
 * app menu. Rows with a value show it between arrows; left / right change it. Touch is only
 * enabled on bodies whose profile says they have a touchscreen.
 */
public final class ListPanel extends View {
    static final class Row {
        final String label;
        /** Shown between arrows when not null; left / right change it. */
        final String value;
        /** Secondary text under the label, e.g. a preset's description. */
        final String detail;
        final boolean marked;

        Row(String label, String value, String detail, boolean marked) {
            this.label = label;
            this.value = value;
            this.detail = detail;
            this.marked = marked;
        }
    }

    interface TapListener {
        void onTap(int index);
    }

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint();

    private String title = "";
    private String legend = "";
    private List<Row> rows = new ArrayList<Row>();
    private int selected;
    private int first;
    private TapListener tapListener;
    private float rowHeight = 1;
    private float rowsTop;

    /** Inflated from res/layout/main.xml. */
    public ListPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void show(String title, List<Row> rows, int selected, String legend) {
        this.title = title;
        this.rows = rows;
        this.legend = legend;
        this.selected = rows.isEmpty() ? 0 : Math.max(0, Math.min(rows.size() - 1, selected));
        this.first = 0;
        setVisibility(VISIBLE);
        invalidate();
    }

    void hide() {
        setVisibility(GONE);
    }

    boolean isOpen() {
        return getVisibility() == VISIBLE;
    }

    int selected() {
        return selected;
    }

    int size() {
        return rows.size();
    }

    /** Moves the selection, wrapping at the ends. */
    void move(int delta) {
        if (rows.isEmpty()) return;
        selected = ((selected + delta) % rows.size() + rows.size()) % rows.size();
        invalidate();
    }

    void setTapListener(TapListener l) {
        tapListener = l;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (tapListener == null || e.getAction() != MotionEvent.ACTION_UP) return tapListener != null;
        int idx = first + (int) ((e.getY() - rowsTop) / rowHeight);
        if (e.getY() >= rowsTop && idx >= 0 && idx < rows.size()) {
            selected = idx;
            invalidate();
            tapListener.onTap(idx);
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        float size = h / 22f;
        float pad = size * 0.5f;
        float panelW = w * 0.62f;

        fill.setColor(Color.argb(215, 16, 16, 20));
        c.drawRect(0, 0, panelW, h, fill);

        text.setTextSize(size * 1.15f);
        text.setFakeBoldText(true);
        text.setColor(Color.WHITE);
        c.drawText(title, pad, pad + size * 1.15f, text);
        text.setFakeBoldText(false);

        text.setTextSize(size * 0.75f);
        List<String> legendLines = TextFit.wrap(text, legend, panelW - 2 * pad);
        float legendTop = h - pad - legendLines.size() * size * 0.95f;

        rowsTop = pad * 2 + size * 1.6f;
        rowHeight = size * 2.2f;
        int visible = Math.max(1, (int) ((legendTop - rowsTop) / rowHeight));
        if (selected < first) first = selected;
        if (selected >= first + visible) first = selected - visible + 1;

        for (int i = first; i < rows.size() && i < first + visible; i++) {
            Row r = rows.get(i);
            float top = rowsTop + (i - first) * rowHeight;
            if (i == selected) {
                fill.setColor(Color.rgb(230, 120, 30));
                c.drawRect(0, top, panelW, top + rowHeight - 2, fill);
            }
            text.setTextSize(size);
            text.setColor(Color.WHITE);
            String label = (r.marked ? "• " : "") + r.label;
            float labelY = top + (r.detail != null ? size * 1.05f : rowHeight / 2 + size * 0.35f);
            float valueW = 0;
            if (r.value != null) {
                String v = "<  " + r.value + "  >";
                valueW = text.measureText(v);
                c.drawText(v, panelW - pad - valueW, labelY, text);
            }
            c.drawText(TextFit.ellipsize(text, label, panelW - 2 * pad - valueW - pad), pad, labelY, text);
            if (r.detail != null) {
                text.setTextSize(size * 0.75f);
                text.setColor(i == selected ? Color.rgb(255, 235, 215) : Color.rgb(170, 170, 170));
                c.drawText(TextFit.ellipsize(text, r.detail, panelW - 2 * pad), pad, top + size * 1.95f, text);
            }
        }

        text.setTextSize(size * 0.75f);
        text.setColor(Color.rgb(170, 170, 170));
        float y = legendTop + size * 0.75f;
        for (String l : legendLines) {
            c.drawText(l, pad, y, text);
            y += size * 0.95f;
        }
    }
}
