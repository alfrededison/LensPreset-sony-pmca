package com.lenspreset.app;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import com.lenspreset.adapter.PlaybackGeometry;
import com.lenspreset.adapter.PlaybackText;
import com.lenspreset.adapter.SonyMedia;
import com.lenspreset.core.i18n.I18n;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The playback screen's state: which pictures there are, which one is showing, and the card reads
 * behind it. It is there to check what was just shot, not to replace the camera's own playback:
 * pictures only, no movies, zoom or delete. MainActivity closes the camera before {@link #open}
 * (the live view and playback do not run together, as on the camera itself) and opens it again
 * after {@link #close}.
 *
 * <p>Pictures open newest first, like the camera's playback: the 160x120 thumbnail and the Exif line
 * at once, then the screen-sized JPEG. Card reads run on one thread of their own, and a new picture
 * drops the reads still queued for older ones: holding Left / Right skips pictures instead of
 * queueing them. The index refresh has a thread of its own, so browsing never waits behind it.
 */
final class Playback {
    interface Host {
        void log(String line);
    }

    /** The screennail is decoded to at least this many pixels on its long side (the screen is 640). */
    private static final int DECODE_MIN_PX = 800;
    private static final int INDEX_REFRESH_MS = 5000;
    /** Marks one item's card reads, so a newer item drops them without dropping the index refresh. */
    private static final Object ITEM = new Object();

    private final PlaybackView view;
    private final Handler ui = new Handler();
    private final ContentResolver resolver;
    private final Host host;
    private I18n i18n;

    private HandlerThread thread;
    private Handler work;
    /** Bumped by every navigation and by close: results of older requests are dropped. */
    private volatile int generation;
    private boolean open;
    /** Bumped by every open: a list or refresh still running from an earlier visit is ignored. */
    private int session;
    /** Index refreshes still waiting, this visit's or an earlier one's: a count, so none clears another's. */
    private final AtomicInteger refreshes = new AtomicInteger();
    private SonyMedia.Stills stills;
    private int index;
    /** This visit's index refresh has finished: the list includes every picture taken before it. */
    private boolean refreshed;

    Playback(PlaybackView view, ContentResolver resolver, Host host) {
        this.view = view;
        this.resolver = resolver;
        this.host = host;
    }

    /**
     * {@code refreshFirst}: a picture may have been taken since the index was last read, so bring it
     * up to date before showing anything; the first picture shown is then the one just taken, not
     * the one before it. Otherwise the list shows at once and the refresh follows.
     */
    void open(I18n i18n, double pixelAspect, boolean refreshFirst) {
        // A second open without a close would leave the first card thread running.
        close();
        this.i18n = i18n;
        open = true;
        session++;
        generation++;
        stills = null;
        refreshed = false;
        thread = new HandlerThread("playback");
        thread.start();
        work = new Handler(thread.getLooper());
        view.setPixelAspect(pixelAspect);
        showPicture(null, 0);
        view.setTexts("", "", "", "");
        view.setLegend(i18n.t("ui.playback.legend"));
        view.setMessage(i18n.t("ui.playback.loading"), false);
        host.log("playback open pixel_aspect=" + pixelAspect + " refresh_first=" + refreshFirst);
        // With no list yet, the refresh's result shows the newest picture.
        if (refreshFirst) refreshIndex();
        else loadStills();
    }

    /** Whether this visit brought the index up to date; false when it closed before the refresh ended. */
    boolean refreshed() {
        return refreshed;
    }

    void close() {
        if (!open) return;
        open = false;
        generation++;
        if (refreshes.get() > 0) SonyMedia.cancelRefresh(resolver);
        // Results already queued for the UI still run: each sees the new generation (or the closed
        // screen) and recycles the bitmap it carries instead of leaving it to the finalizer.
        if (thread != null) {
            work.removeCallbacksAndMessages(null);
            thread.quit();
            thread = null;
            work = null;
        }
        showPicture(null, 0);
        host.log("playback close");
    }

    // ---- keys ----

    void handle(int action) {
        if (!open) return;
        switch (action) {
            case Keys.LEFT:
            case Keys.PREV: step(-1); break;
            case Keys.RIGHT:
            case Keys.NEXT: step(1); break;
            case Keys.UP:
            case Keys.TRASH: view.cycleInfo(); break;
            default: break;
        }
    }

    private int count() {
        return stills == null ? 0 : stills.size();
    }

    private void step(int d) {
        int n = count();
        if (n == 0) return;
        // Past the newest comes the oldest again, as on the camera.
        index = ((index + d) % n + n) % n;
        show();
    }

    // ---- list ----

    private void loadStills() {
        view.setMessage(i18n.t("ui.playback.loading"), false);
        final int gen = generation;
        final int visit = session;
        work.post(new Runnable() {
            @Override
            public void run() {
                long start = System.currentTimeMillis();
                final SonyMedia.Stills s = SonyMedia.stills(resolver);
                final long ms = System.currentTimeMillis() - start;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!open || visit != session) return;
                        host.log("playback stills " + s.size() + " in " + ms + " ms"
                                + (s.problem != null ? " (" + s.problem + ")" : ""));
                        stills = s;
                        if (gen == generation) {
                            index = Math.max(0, s.size() - 1);
                            show();
                        }
                        refreshIndex();
                    }
                });
            }
        });
    }

    /**
     * Sony's playback brings its index up to date on entry; this does the same, before the first
     * picture when one may have just been taken, otherwise after it so opening is not held up, then
     * moves to the newest picture if it changed and the newest one was showing. On a thread of its
     * own: the wait can take seconds, and browsing must not queue behind it.
     */
    private void refreshIndex() {
        final int visit = session;
        refreshes.incrementAndGet();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String line;
                try {
                    line = SonyMedia.refreshIndex(resolver, INDEX_REFRESH_MS);
                } finally {
                    refreshes.decrementAndGet();
                }
                final SonyMedia.Stills s = SonyMedia.stills(resolver);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!open || visit != session) return;
                        refreshed = true;
                        host.log("playback " + line + ", stills now " + s.size());
                        SonyMedia.Stills old = stills;
                        if (old != null && s.size() == old.size() && s.problem == null) return;
                        if (s.problem != null && old != null && old.size() > 0) return;
                        boolean atNewest = old == null || old.size() == 0 || index == old.size() - 1;
                        stills = s;
                        if (atNewest) index = Math.max(0, s.size() - 1);
                        else index = Math.min(index, Math.max(0, s.size() - 1));
                        show();
                    }
                });
            }
        }, "index-refresh").start();
    }

    // ---- one picture ----

    private void show() {
        generation++;
        work.removeCallbacksAndMessages(ITEM);
        if (count() == 0) {
            showPicture(null, 0);
            view.setTexts("", "", "", "");
            String problem = stills != null ? stills.problem : null;
            if (problem != null) {
                Map<String, String> args = new HashMap<String, String>();
                args.put("reason", problem);
                view.setMessage(i18n.t("ui.playback.index_failed", args), true);
            } else {
                view.setMessage(i18n.t("ui.playback.no_stills"), false);
            }
            return;
        }
        view.setMessage("", false);
        showStill(index);
    }

    private void showStill(final int i) {
        final int gen = generation;
        final SonyMedia.Stills s = stills;
        view.setTexts(PlaybackText.position(i, s.size()), "", "", "");
        postItem(new Runnable() {
            @Override
            public void run() {
                final SonyMedia.Picture p = SonyMedia.picture(s.data[i]);
                final Bitmap thumb = p == null ? null : decode(p.thumbnail, 0);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != generation || !open) {
                            recycle(thumb);
                            return;
                        }
                        if (p == null) {
                            view.setMessage(i18n.t("ui.playback.failed"), true);
                        } else {
                            view.setTexts(PlaybackText.position(i, s.size()), PlaybackText.fileName(p.folder, p.file),
                                    PlaybackText.exposure(p.fNumber, p.exposureSeconds, p.iso, p.ev, p.focalMm),
                                    PlaybackText.date(p.date) + (p.width > 0 ? " · " + p.width + "x" + p.height : ""));
                            if (thumb != null) showPicture(thumb, PlaybackText.rotation(p.orientation));
                        }
                    }
                });
                if (gen != generation) return;
                final Bitmap full = decode(SonyMedia.screennail(resolver, s.uri, s.ids[i], s.data[i]), DECODE_MIN_PX);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != generation || !open) {
                            recycle(full);
                            return;
                        }
                        if (full != null) showPicture(full, PlaybackText.rotation(p == null ? 1 : p.orientation));
                        else if (thumb == null) view.setMessage(i18n.t("ui.playback.failed"), true);
                    }
                });
            }
        });
    }

    private void postItem(Runnable r) {
        work.postAtTime(r, ITEM, SystemClock.uptimeMillis());
    }

    // ---- bitmaps ----

    private void showPicture(Bitmap b, int rotation) {
        Bitmap old = view.picture();
        view.setPicture(b, rotation);
        if (old != null && old != b) old.recycle();
    }

    private static void recycle(Bitmap b) {
        if (b != null) b.recycle();
    }

    /** Decodes a JPEG, halving while the long side stays at least {@code minPx} (0: full size). */
    private static Bitmap decode(byte[] jpeg, int minPx) {
        if (jpeg == null || jpeg.length == 0) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            o.inJustDecodeBounds = false;
            o.inSampleSize = minPx <= 0 ? 1 : PlaybackGeometry.sampleSize(o.outWidth, o.outHeight, minPx);
            return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, o);
        } catch (Throwable t) {
            // Out of memory on the camera's 24 MB heap, or a broken file: show the next one.
            return null;
        }
    }

    /** For the adb test run: what is showing. */
    String describe() {
        List<String> parts = new ArrayList<String>();
        parts.add("index=" + index);
        parts.add("count=" + count());
        return parts.toString();
    }
}
