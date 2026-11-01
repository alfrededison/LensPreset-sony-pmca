package com.lenspreset.app;

import android.os.Environment;

import com.lenspreset.core.profile.CardFiles;
import com.lenspreset.core.profile.ProfileSet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The app's folder on the memory card, {@code LENSPRST}: profiles a user adds without a new build,
 * PROBE.TXT and LOG.TXT. Names stay 8.3 (see {@link CardFiles}). Files the app writes are English.
 */
final class CardStore {
    private static final long LOG_MAX_BYTES = 256 * 1024;

    boolean available() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    File root() {
        return new File(Environment.getExternalStorageDirectory(), CardFiles.FOLDER);
    }

    /** Profile files in CAMERAS, LENSES and PRESETS, in name order. */
    List<ProfileSet.Entry> profiles() {
        List<ProfileSet.Entry> out = new ArrayList<ProfileSet.Entry>();
        if (!available()) return out;
        read(out, CardFiles.CAMERAS, ProfileSet.Kind.CAMERA);
        read(out, CardFiles.LENSES, ProfileSet.Kind.LENS);
        read(out, CardFiles.PRESETS, ProfileSet.Kind.PRESET);
        return out;
    }

    private void read(List<ProfileSet.Entry> out, String folder, ProfileSet.Kind kind) {
        File[] files = new File(root(), folder).listFiles();
        if (files == null) return;
        Arrays.sort(files);
        for (File f : files) {
            String origin = "card:" + folder + "/" + f.getName();
            if (!f.isFile() || !CardFiles.isProfileFile(f.getName())) continue;
            if (f.length() > CardFiles.MAX_BYTES) {
                out.add(new ProfileSet.Entry(kind, origin, null, true));
                continue;
            }
            try {
                out.add(new ProfileSet.Entry(kind, origin, Text.read(f), true));
            } catch (IOException e) {
                out.add(new ProfileSet.Entry(kind, origin, null, true));
            }
        }
    }

    /** Writes PROBE.TXT and returns its path on the card. */
    String writeProbe(String text) throws IOException {
        File dir = root();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File f = new File(dir, CardFiles.PROBE_REPORT);
        OutputStream os = new FileOutputStream(f);
        try {
            os.write(text.getBytes("UTF-8"));
        } finally {
            os.close();
        }
        return CardFiles.FOLDER + "/" + CardFiles.PROBE_REPORT;
    }

    /** Appends timestamped lines to LOG.TXT, keeping one older file when it grows large. Never throws. */
    void log(List<String> lines) {
        if (!available()) return;
        try {
            File dir = root();
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            File f = new File(dir, CardFiles.LOG);
            if (f.length() > LOG_MAX_BYTES) {
                File old = new File(dir, "LOGOLD.TXT");
                if (old.exists() && !old.delete()) return;
                if (!f.renameTo(old)) return;
            }
            String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            StringBuilder sb = new StringBuilder();
            for (String l : lines) sb.append(stamp).append(' ').append(l).append("\r\n");
            OutputStream os = new FileOutputStream(f, true);
            try {
                os.write(sb.toString().getBytes("UTF-8"));
            } finally {
                os.close();
            }
        } catch (IOException ignored) {
            // The log is a convenience; a full or read-only card must not stop the app.
        }
    }
}
