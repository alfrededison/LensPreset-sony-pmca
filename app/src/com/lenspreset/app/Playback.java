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
 * pictures only, no movies or delete. MainActivity closes the camera before {@link #open}
 * (the live view and playback do not run together, as on the camera itself) and opens it again
 * after {@link #close}.
 *
 * <p>Pictures open newest first, like the camera's playback: the 160x120 thumbnail and the Exif line
 * at once, then the screen-sized JPEG. Card reads run on one thread of their own, and a new picture
 * drops the reads still queued for older ones: holding Left / Right skips pictures instead of
 * queueing them. The index refresh has a thread of its own, so browsing never waits behind it.
 *
 * <p>The zoom lever zooms the screennail, decoded at its full 1616x1080, up to {@link #MAX_ZOOM}:
 * about one screennail pixel per screen pixel across there, enough to check focus. Not the JPEG itself: on an A5100,
 * opening a 24 MP file with BitmapRegionDecoder took 3 s and once 23 s, and kept 23 MB of native
 * memory, stalling the card for the next screennail. One step per push however many key events
 * the lever sends. Zoomed, the wheel moves to the next picture at the same zoom and place, to
 * compare focus across a burst.
 */
final class Playback {
    interface Host {
        void log(String line);
    }

    /**
     * The screennail is decoded to at least this many pixels on its long side: its full 1616 on an
     * A5100, about three times the 540 screen pixels a 3:2 picture covers, for {@link #MAX_ZOOM}.
     */
    private static final int DECODE_MIN_PX = 1600;
    private static final double MAX_ZOOM = 3;
    private static final int INDEX_REFRESH_MS = 5000;
    /** Marks one item's card reads, so a newer item drops them without dropping the index refresh. */
    private static final Object ITEM = new Object();
    /**
     * One lever push sends several key events (and a held lever keeps sending them): steps closer
     * together than this are one push, so a push is one step and a held lever steps about three
     * times a second.
     */
    private static final long ZOOM_STEP_MS = 300;

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
    /**
     * The showing picture's screennail is up. Zoom waits for it: the thumbnail before it is 160x120,
     * too small to enlarge, and may still be the previous picture's.
     */
    private boolean screennailShown;
    /** Browsing zoomed, the thumbnail is kept off screen: shown at 1x if the screennail cannot be read. */
    private Bitmap heldThumb;
    private int heldRotation;
    private long lastZoomStep;

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
        resetZoom(false);
        dropHeldThumb();
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
        if (zoomed()) {
            // The four-way moves around, the wheel goes to the next picture at the same zoom and
            // place, Centre shows it all.
            switch (action) {
                case Keys.LEFT: view.pan(-1, 0); break;
                case Keys.RIGHT: view.pan(1, 0); break;
                case Keys.UP: view.pan(0, -1); break;
                case Keys.DOWN: view.pan(0, 1); break;
                case Keys.NEXT: step(1, true); break;
                case Keys.PREV: step(-1, true); break;
                case Keys.ENTER: fit(); break;
                case Keys.TRASH: view.cycleInfo(); break;
                default: break;
            }
            return;
        }
        switch (action) {
            case Keys.LEFT:
            case Keys.PREV: step(-1, false); break;
            case Keys.RIGHT:
            case Keys.NEXT: step(1, false); break;
            case Keys.UP:
            case Keys.TRASH: view.cycleInfo(); break;
            default: break;
        }
    }

    boolean zoomed() {
        return view.zoom() > 1;
    }

    /** The lever was let go: the next push steps at once, however soon it comes. */
    void zoomReleased() {
        lastZoomStep = 0;
    }

    /** The zoom lever: {@code direction} +1 tele, -1 wide. */
    void zoom(int direction) {
        if (!open || !screennailShown) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastZoomStep < ZOOM_STEP_MS) return;
        lastZoomStep = now;
        double next = PlaybackGeometry.nextZoom(view.zoom(), direction, MAX_ZOOM);
        if (next != view.zoom()) zoomTo(next);
    }

    /** Centre: the whole picture, or its thumbnail when browsed zoomed onto it and its screennail is not in yet. */
    private void fit() {
        zoomTo(1);
        if (!screennailShown && heldThumb != null) {
            showPicture(heldThumb, heldRotation);
            heldThumb = null;
        }
    }

    private void zoomTo(double z) {
        host.log("playback zoom " + PlaybackText.zoom(z));
        view.setZoom(z);
        view.setLegend(z > 1 ? zoomLegend(z) : i18n.t("ui.playback.legend"));
    }

    private String zoomLegend(double z) {
        Map<String, String> args = new HashMap<String, String>();
        args.put("zoom", PlaybackText.zoom(z));
        return i18n.t("ui.playback.legend.zoom", args);
    }

    /**
     * Forgets the picture's zoom state; {@code keepView} keeps the zoom and place on screen for the
     * next picture (the wheel while zoomed).
     */
    private void resetZoom(boolean keepView) {
        screennailShown = false;
        if (keepView) return;
        view.setZoom(1);
        if (i18n != null) view.setLegend(i18n.t("ui.playback.legend"));
    }

    private int count() {
        return stills == null ? 0 : stills.size();
    }

    private void step(int d, boolean keepZoom) {
        int n = count();
        if (n == 0) return;
        // Zoomed on the only picture, the wheel would only reload it.
        if (keepZoom && n == 1) return;
        // Past the newest comes the oldest again, as on the camera.
        index = ((index + d) % n + n) % n;
        show(keepZoom);
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
                            show(false);
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
                        show(false);
                    }
                });
            }
        }, "index-refresh").start();
    }

    // ---- one picture ----

    private void show(boolean keepZoom) {
        generation++;
        work.removeCallbacksAndMessages(ITEM);
        resetZoom(keepZoom);
        dropHeldThumb();
        // Zoomed, the 4:3 thumbnail would show enlarged and out of place: black until the screennail.
        if (keepZoom) showPicture(null, 0);
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
                            if (!zoomed()) showPicture(thumb, PlaybackText.rotation(p.orientation));
                            else {
                                heldThumb = thumb;
                                heldRotation = PlaybackText.rotation(p.orientation);
                            }
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
                        int rotation = PlaybackText.rotation(p == null ? 1 : p.orientation);
                        if (full != null) {
                            showPicture(full, rotation);
                            dropHeldThumb();
                            screennailShown = true;
                            // Browsed with the wheel while zoomed: the same zoom and place, kept inside
                            // this picture's edges.
                            if (zoomed()) view.setZoom(view.zoom());
                        } else if (zoomed()) {
                            // Browsed zoomed onto a picture with no screennail: nothing to zoom, so
                            // back to the whole picture, its thumbnail if there is one.
                            zoomTo(1);
                            Bitmap t = heldThumb;
                            heldThumb = null;
                            showPicture(t, rotation);
                            view.setMessage(i18n.t("ui.playback.failed"), true);
                        } else if (thumb == null) {
                            view.setMessage(i18n.t("ui.playback.failed"), true);
                        }
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

    private void dropHeldThumb() {
        recycle(heldThumb);
        heldThumb = null;
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
            // Half the memory of ARGB_8888 (3.5 MB for 1616x1080); dithered, skies do not band.
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            o.inDither = true;
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
        parts.add("zoom=" + PlaybackText.zoom(view.zoom()));
        return parts.toString();
    }
}
