package com.lenspreset.adapter;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import com.sony.scalar.graphics.AvindexGraphics;
import com.sony.scalar.media.AvindexContentInfo;
import com.sony.scalar.provider.AvindexStore;

import java.util.Timer;
import java.util.TimerTask;

/**
 * The pictures on the card, for the playback screen. They come from the camera's own index
 * (AvindexStore.Images), read as ma1co/PMCADemo does through OpenMemories' MediaManager and
 * ImageInfo: a cursor over {@code _id} / {@code _data}, then per picture the Exif attributes and
 * thumbnail ({@code getImageInfo}) and the screen-sized JPEG ({@code getScreennail}). Every call
 * may block on the card: call them off the UI thread. Every Sony call is guarded, so a body or
 * firmware without one reports an empty list or null and a reason, never a crash.
 */
public final class SonyMedia {
    private SonyMedia() {
    }

    static final String COL_ID = "_id";
    static final String COL_DATA = "_data";
    static final String COL_CREATED_UTC = "content_created_utc_date_time";

    /** Pictures oldest first; {@link #problem} says why the list is empty when it could not be read. */
    public static final class Stills {
        public final Uri uri;
        public final long[] ids;
        public final String[] data;
        public final String problem;

        Stills(Uri uri, long[] ids, String[] data, String problem) {
            this.uri = uri;
            this.ids = ids;
            this.data = data;
            this.problem = problem;
        }

        public int size() {
            return ids.length;
        }
    }

    /** One picture's Exif values; numbers the picture does not record are 0 (EV: NaN). */
    public static final class Picture {
        public String folder = "";
        public String file = "";
        public String date = "";
        public double fNumber;
        public double exposureSeconds;
        public int iso;
        public double ev = Double.NaN;
        public double focalMm;
        public int orientation = 1;
        public int width;
        public int height;
        /** The 160x120 JPEG thumbnail, or null. */
        public byte[] thumbnail;
    }

    public static Stills stills(ContentResolver resolver) {
        Uri uri;
        try {
            uri = AvindexStore.Images.Media.EXTERNAL_CONTENT_URI;
        } catch (Throwable t) {
            return new Stills(null, new long[0], new String[0], "no picture index: " + ApplyReport.describe(t));
        }
        Cursor c = null;
        try {
            String[] columns = {COL_ID, COL_DATA};
            try {
                c = resolver.query(uri, columns, null, null, COL_CREATED_UTC + " ASC, " + COL_ID + " ASC");
            } catch (RuntimeException e) {
                // A firmware without the date column: the index's own order, which is recording order.
                c = resolver.query(uri, columns, null, null, null);
            }
            if (c == null) return new Stills(uri, new long[0], new String[0], "picture index returned nothing");
            int id = c.getColumnIndexOrThrow(COL_ID);
            int data = c.getColumnIndexOrThrow(COL_DATA);
            long[] ids = new long[c.getCount()];
            String[] datas = new String[ids.length];
            int n = 0;
            while (c.moveToNext() && n < ids.length) {
                ids[n] = c.getLong(id);
                datas[n] = c.getString(data);
                n++;
            }
            if (n < ids.length) {
                long[] i2 = new long[n];
                String[] d2 = new String[n];
                System.arraycopy(ids, 0, i2, 0, n);
                System.arraycopy(datas, 0, d2, 0, n);
                ids = i2;
                datas = d2;
            }
            return new Stills(uri, ids, datas, null);
        } catch (Throwable t) {
            return new Stills(uri, new long[0], new String[0], "picture index: " + ApplyReport.describe(t));
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (Throwable ignored) {
                    // Nothing more to do.
                }
            }
        }
    }

    /**
     * Brings the picture index up to date with the card, as Sony's playback does on entry; gives
     * up after {@code timeoutMs}. Returns a line for the log.
     */
    public static String refreshIndex(final ContentResolver resolver, long timeoutMs) {
        long start = System.currentTimeMillis();
        final String media;
        try {
            media = mediaId();
        } catch (Throwable t) {
            return "index refresh skipped: " + ApplyReport.describe(t);
        }
        Timer timer = new Timer("index-refresh-timeout", true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    AvindexStore.Images.cancelWaitAndUpdateDatabase(resolver, media);
                } catch (Throwable ignored) {
                    // The wait ends by itself.
                }
            }
        }, timeoutMs);
        try {
            boolean ok = AvindexStore.Images.waitAndUpdateDatabase(resolver, media);
            return "index refresh " + media + " " + ok + " in " + (System.currentTimeMillis() - start) + " ms";
        } catch (Throwable t) {
            return "index refresh failed: " + ApplyReport.describe(t);
        } finally {
            timer.cancel();
        }
    }

    /** Ends a {@link #refreshIndex} wait now, from any thread. */
    public static void cancelRefresh(ContentResolver resolver) {
        try {
            AvindexStore.Images.cancelWaitAndUpdateDatabase(resolver, mediaId());
        } catch (Throwable ignored) {
            // The wait ends by itself at its timeout.
        }
    }

    private static String mediaId() {
        String[] ids = AvindexStore.getExternalMediaIds();
        return ids != null && ids.length > 0 ? ids[0] : AvindexStore.Images.Media.EXTERNAL_DEFAULT_MEDIA_ID;
    }

    /** Exif values and thumbnail of one picture, by its {@code _data} id; null when unavailable. */
    public static Picture picture(String data) {
        AvindexContentInfo info = null;
        try {
            info = AvindexStore.Images.Media.getImageInfo(data);
            if (info == null) return null;
            Picture p = new Picture();
            p.folder = nonNull(info.getAttribute(AvindexContentInfo.TAG_DCF_TBL_DIR_NAME));
            p.file = nonNull(info.getAttribute(AvindexContentInfo.TAG_DCF_TBL_FILE_NAME));
            p.date = nonNull(info.getAttribute(AvindexContentInfo.TAG_DATETIME));
            p.fNumber = info.getAttributeDouble(AvindexContentInfo.TAG_APERTURE, 0);
            p.exposureSeconds = info.getAttributeDouble(AvindexContentInfo.TAG_EXPOSURE_TIME, 0);
            p.iso = info.getAttributeInt(AvindexContentInfo.TAG_ISO, 0);
            p.ev = info.getAttributeDouble(AvindexContentInfo.TAG_EXPOSURE_BIAS_VALUE, Double.NaN);
            p.focalMm = info.getAttributeDouble(AvindexContentInfo.TAG_FOCAL_LENGTH, 0);
            p.orientation = info.getAttributeInt(AvindexContentInfo.TAG_ORIENTATION, 1);
            p.width = info.getAttributeInt(AvindexContentInfo.TAG_IMAGE_WIDTH, 0);
            p.height = info.getAttributeInt(AvindexContentInfo.TAG_IMAGE_LENGTH, 0);
            try {
                p.thumbnail = info.getThumbnail();
            } catch (Throwable ignored) {
                // The screennail still comes.
            }
            return p;
        } catch (Throwable t) {
            return null;
        } finally {
            if (info != null) {
                try {
                    info.recycle();
                } catch (Throwable ignored) {
                    // Freed with the object.
                }
            }
        }
    }

    /**
     * The screen-sized JPEG (about 1616x1080) of one picture, or null: AvindexGraphics by
     * {@code _data} first, as OpenMemories' ImageInfo and sony-lut-camera read it, then
     * AvindexStore's own getScreennail by row id.
     */
    public static byte[] screennail(ContentResolver resolver, Uri uri, long id, String data) {
        try {
            byte[] b = data == null ? null : AvindexGraphics.getScreenNail(data);
            if (b != null && b.length > 0) return b;
        } catch (Throwable ignored) {
            // Try the other route.
        }
        try {
            byte[] b = AvindexStore.Images.Media.getScreennail(resolver, uri, id);
            if (b != null && b.length > 0) return b;
        } catch (Throwable ignored) {
            // Nothing else to try.
        }
        return null;
    }

    private static String nonNull(String s) {
        return s == null ? "" : s.trim();
    }
}
