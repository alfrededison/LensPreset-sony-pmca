package com.lenspreset.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * A full-screen list: one line per choice, with the selected description below the list.
 * Adjustable values show arrows on selection; summary values simply align right. Touch is only
 * enabled on bodies whose profile says they have a touchscreen.
 */
public final class ListPanel extends View {
    static final class Row {
        final String label;
        /** Optional value aligned to the right of the label. */
        final String value;
        final boolean adjustable;
        final boolean submenu;
        /** Explanation shown below the list when this row is selected. */
        final String detail;
        final boolean marked;

        Row(String label, String value, String detail, boolean marked) {
            this(label, value, detail, marked, true);
        }

        Row(String label, String value, String detail, boolean marked, boolean adjustable) {
            this(label, value, detail, marked, adjustable, false);
        }

        private Row(String label, String value, String detail, boolean marked, boolean adjustable, boolean submenu) {
            this.label = label;
            this.value = value;
            this.detail = detail;
            this.marked = marked;
            this.adjustable = adjustable;
            this.submenu = submenu;
        }

        static Row submenu(String label, String value, String detail) {
            return new Row(label, value, detail, false, false, true);
        }
    }

    interface TapListener {
        void onTap(int index);
    }

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint();
    private final Paint chevron = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path chevronPath = new Path();

    private String title = "";
    private String legend = "";
    private List<Row> rows = new ArrayList<Row>();
    private int selected;
    private int first;
    private TapListener tapListener;
    private float rowHeight = 1;
    private float rowsTop;
    private int visibleRows;
    private boolean reading;
    private List<String> paragraphs;

    /** Inflated from res/layout/main.xml. */
    public ListPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        chevron.setStyle(Paint.Style.STROKE);
        chevron.setStrokeCap(Paint.Cap.ROUND);
        chevron.setStrokeJoin(Paint.Join.ROUND);
    }

    void show(String title, List<Row> rows, int selected, String legend) {
        reading = false;
        paragraphs = null;
        this.title = title;
        this.rows = rows;
        this.legend = legend;
        this.selected = rows.isEmpty() ? 0 : Math.max(0, Math.min(rows.size() - 1, selected));
        this.first = 0;
        setVisibility(VISIBLE);
        invalidate();
    }

    void showText(String title, List<String> paragraphs, String legend) {
        show(title, new ArrayList<Row>(), 0, legend);
        reading = true;
        this.paragraphs = paragraphs;
        wrapParagraphs();
    }

    private void wrapParagraphs() {
        if (paragraphs == null || getWidth() == 0) return;
        text.setTextSize(getHeight() / 22f * 0.9f);
        rows = new ArrayList<Row>();
        for (String paragraph : paragraphs) {
            for (String line : TextFit.wrap(text, paragraph, getWidth() - getHeight() / 22f * 1.8f))
                rows.add(new Row(line, null, null, false));
        }
        selected = Math.min(selected, Math.max(0, rows.size() - 1));
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        wrapParagraphs();
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
        int offset = (int) ((e.getY() - rowsTop) / rowHeight);
        int idx = first + offset;
        if (e.getY() >= rowsTop && offset < visibleRows && idx >= 0 && idx < rows.size()) {
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
        float pad = size * 0.65f;

        fill.setColor(Color.rgb(16, 17, 20));
        c.drawRect(0, 0, w, h, fill);

        text.setTextSize(size * 0.85f);
        text.setColor(Color.rgb(245, 177, 92));
        String count = rows.isEmpty() ? "" : (selected + 1) + "/" + rows.size();
        float countW = text.measureText(count);
        c.drawText(count, w - pad - countW, pad + size, text);
        text.setTextSize(size * 1.05f);
        text.setFakeBoldText(true);
        text.setColor(Color.WHITE);
        c.drawText(TextFit.ellipsize(text, title, w - 3 * pad - countW), pad, pad + size, text);
        text.setFakeBoldText(false);

        text.setTextSize(size * 0.75f);
        List<String> legendLines = TextFit.wrap(text, legend, w - 2 * pad);
        float legendTop = h - pad - legendLines.size() * size * 0.95f;

        // Reserve a stable detail area so moving to a long description never shifts the rows.
        int detailLines = 0;
        text.setTextSize(size * 0.8f);
        for (Row row : rows) {
            if (row.detail != null)
                detailLines = Math.max(detailLines, TextFit.wrap(text, row.detail, w - 2 * pad).size());
        }
        float detailTop = legendTop - (detailLines > 0 ? (detailLines + 0.8f) * size : 0);
        rowsTop = size * 2.4f;
        rowHeight = size * (reading ? 1.3f : 1.55f);
        int visible = Math.max(1, (int) ((detailTop - rowsTop - size * 0.3f) / rowHeight));
        visibleRows = Math.min(visible, rows.size());
        if (selected < first) first = selected;
        if (selected >= first + visible) first = selected - visible + 1;
        first = Math.max(0, Math.min(first, rows.size() - visibleRows));
        float right = w - pad - (rows.size() > visible ? size * 0.5f : 0);

        for (int i = first; i < rows.size() && i < first + visible; i++) {
            Row r = rows.get(i);
            float top = rowsTop + (i - first) * rowHeight;
            if (i == selected && !reading) {
                fill.setColor(Color.rgb(245, 177, 92));
                c.drawRect(pad * 0.5f, top, right + pad * 0.4f, top + rowHeight - 2, fill);
            }
            text.setTextSize(size * (reading ? 0.9f : 1));
            text.setColor(i == selected && !reading ? Color.rgb(24, 19, 12) : Color.WHITE);
            float labelX = pad + (reading ? 0 : size * 0.6f);
            float labelY = top + rowHeight / 2 - (text.ascent() + text.descent()) / 2;
            if (r.marked) {
                fill.setColor(text.getColor());
                c.drawCircle(pad + size * 0.15f, top + rowHeight / 2, size * 0.12f, fill);
            }
            float valueW = 0;
            float contentRight = right;
            if (r.submenu) {
                float cy = top + rowHeight / 2;
                chevron.setColor(text.getColor());
                chevron.setStrokeWidth(size * 0.08f);
                chevronPath.reset();
                chevronPath.moveTo(right - size * 0.4f, cy - size * 0.23f);
                chevronPath.lineTo(right - size * 0.15f, cy);
                chevronPath.lineTo(right - size * 0.4f, cy + size * 0.23f);
                c.drawPath(chevronPath, chevron);
                contentRight -= size * 0.9f;
            }
            if (r.value != null) {
                boolean arrows = r.adjustable && i == selected;
                String v = (arrows ? "< " : "") + r.value + (arrows ? " >" : "");
                float maxValue = r.adjustable ? (contentRight - labelX) * 0.45f
                        : Math.max(0, contentRight - labelX - text.measureText(r.label) - pad);
                v = TextFit.ellipsize(text, v, maxValue);
                valueW = text.measureText(v);
                c.drawText(v, contentRight - valueW, labelY, text);
            }
            c.drawText(TextFit.ellipsize(text, r.label, contentRight - labelX - valueW - (valueW > 0 ? pad : 0)), labelX, labelY, text);
        }

        if (rows.size() > visible) {
            float track = visible * rowHeight;
            fill.setColor(Color.rgb(55, 55, 60));
            c.drawRect(w - pad * 0.5f, rowsTop, w - pad * 0.25f, rowsTop + track, fill);
            fill.setColor(Color.rgb(245, 177, 92));
            float thumbTop = rowsTop + track * first / rows.size();
            c.drawRect(w - pad * 0.5f, thumbTop, w - pad * 0.25f,
                    thumbTop + track * visible / rows.size(), fill);
        }

        if (detailLines > 0 && !rows.isEmpty() && rows.get(selected).detail != null) {
            text.setTextSize(size * 0.8f);
            text.setColor(Color.rgb(190, 193, 200));
            float detailY = detailTop + size;
            for (String line : TextFit.wrap(text, rows.get(selected).detail, w - 2 * pad)) {
                c.drawText(line, pad, detailY, text);
                detailY += size;
            }
        }

        fill.setColor(Color.rgb(55, 55, 60));
        c.drawRect(pad, rowsTop - size * 0.35f, w - pad, rowsTop - size * 0.3f, fill);
        c.drawRect(pad, legendTop - size * 0.3f, w - pad, legendTop - size * 0.25f, fill);
        text.setTextSize(size * 0.75f);
        text.setColor(Color.rgb(170, 170, 170));
        float y = legendTop + size * 0.75f;
        for (String l : legendLines) {
            c.drawText(l, pad, y, text);
            y += size * 0.95f;
        }
    }
}
