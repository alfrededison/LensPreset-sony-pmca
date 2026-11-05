package com.sony.scalar.provider;

import android.content.ContentResolver;
import android.net.Uri;

import com.sony.scalar.media.AvindexContentInfo;

/**
 * Compile-time stub (see CameraEx). The camera's own index of the pictures on the card, which its
 * playback reads; used by the in-app playback screen. Signatures as in OpenMemories-Framework's
 * stubs, the calls as ma1co/PMCADemo (via OpenMemories' MediaManager / ImageInfo) makes them.
 */
public final class AvindexStore {
    private AvindexStore() {}

    public static String[] getExternalMediaIds() { throw new RuntimeException("Stub!"); }

    public static final class Images {
        private Images() {}

        /** Brings the index up to date with the card; blocks, so it runs off the UI thread. */
        public static boolean waitAndUpdateDatabase(ContentResolver r, String mediaId) { throw new RuntimeException("Stub!"); }
        public static boolean cancelWaitAndUpdateDatabase(ContentResolver r, String mediaId) { throw new RuntimeException("Stub!"); }

        public static final class Media {
            private Media() {}

            public static final Uri EXTERNAL_CONTENT_URI = null;
            public static final String EXTERNAL_DEFAULT_MEDIA_ID = "external";

            public static AvindexContentInfo getImageInfo(String data) { throw new RuntimeException("Stub!"); }
            public static byte[] getScreennail(ContentResolver r, Uri uri, long id) { throw new RuntimeException("Stub!"); }
        }
    }
}
