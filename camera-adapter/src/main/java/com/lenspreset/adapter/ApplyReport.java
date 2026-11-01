package com.lenspreset.adapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What happened to each setting of a recommendation on the camera. Shown as a summary on screen
 * and appended to LOG.TXT, so a compatibility report can say exactly which calls a body refused.
 */
public final class ApplyReport {
    public enum Status {
        /** The camera took it (and, for stepped values, reached the target). */
        OK,
        /** The camera took a step toward it but stopped short (a limit, or the step budget ran out). */
        PARTIAL,
        /** The camera does not list the value as supported. */
        UNSUPPORTED,
        /** The call threw: missing method on this firmware, or a refused value. */
        FAILED,
    }

    public static final class Item {
        /** i18n key suffix under {@code apply.item.}, e.g. {@code aperture}. */
        public final String key;
        public final Status status;
        /** What was asked for and, on failure, why; English, for the log. */
        public final String detail;

        Item(String key, Status status, String detail) {
            this.key = key;
            this.status = status;
            this.detail = detail;
        }
    }

    private final List<Item> items = new ArrayList<Item>();

    public void add(String key, Status status, String detail) {
        items.add(new Item(key, status, detail));
    }

    public List<Item> items() {
        return Collections.unmodifiableList(items);
    }

    public int okCount() {
        int n = 0;
        for (Item i : items) if (i.status == Status.OK) n++;
        return n;
    }

    public List<Item> problems() {
        List<Item> out = new ArrayList<Item>();
        for (Item i : items) if (i.status != Status.OK) out.add(i);
        return out;
    }

    /** What went wrong: refused calls, and steps that stopped short. Not what the body simply lacks. */
    public List<Item> failures() {
        List<Item> out = new ArrayList<Item>();
        for (Item i : items) if (i.status == Status.FAILED || i.status == Status.PARTIAL) out.add(i);
        return out;
    }

    /** What the body does not offer: a fact about the camera, not a failure. */
    public List<Item> unsupported() {
        List<Item> out = new ArrayList<Item>();
        for (Item i : items) if (i.status == Status.UNSUPPORTED) out.add(i);
        return out;
    }

    /** One line per item, for LOG.TXT. */
    public List<String> logLines() {
        List<String> out = new ArrayList<String>();
        for (Item i : items) out.add(i.status + " " + i.key + (i.detail.length() > 0 ? " " + i.detail : ""));
        return out;
    }

    /** Message text of a Throwable, safe for one log line. */
    public static String describe(Throwable t) {
        String m = t.getMessage();
        String s = t.getClass().getSimpleName() + (m == null ? "" : ": " + m);
        return s.replace('\n', ' ');
    }
}
