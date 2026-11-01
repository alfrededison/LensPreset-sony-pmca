package com.lenspreset.app;

import android.os.Environment;
import android.util.Log;

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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The app's folder on the memory card, {@code LENSPRST}: profiles a user adds without a new build,
 * PROBE.TXT and LOG.TXT. Names stay 8.3 (see {@link CardFiles}). Files the app writes are English.
 *
 * <p>The app writes to the card only when the user asks (camera probe, save log): a user's card is
 * theirs, and a log there grows with every start. The log is kept in app storage and copied to the
 * card from the menu.
 */
final class CardStore {
    private static final long LOG_MAX_BYTES = 256 * 1024;

    /** App-private storage (internal flash), where the log lives. */
    private final File appDir;
    /** Also append each log line to the card (DEBUG.TXT switch {@code card-log}). */
    private boolean cardLog;

    CardStore(File appDir) {
        this.appDir = appDir;
    }

    void setCardLog(boolean on) {
        cardLog = on;
    }

    boolean available() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    File root() {
        return new File(Environment.getExternalStorageDirectory(), CardFiles.FOLDER);
    }

    /** Profile files in CAMERAS, LENSES and PRESETS, in name order. */
    List<ProfileSet.Entry> profiles() {
        List<ProfileSet.Entry> out = new ArrayList<ProfileSet.Entry>();
        // Each lookup goes through Sony's card layer, which is slow: one check when there is no folder.
        if (!available() || !root().isDirectory()) return out;
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

    /**
     * Switches in LENSPRST/DEBUG.TXT, one per line ({@code #} starts a comment): ways to leave out a
     * start-up step on a body where the app misbehaves, without a new build. See docs/DEVELOPMENT.md.
     */
    Set<String> debugSwitches() {
        Set<String> out = new HashSet<String>();
        if (!available()) return out;
        File f = new File(root(), "DEBUG.TXT");
        if (!f.isFile() || f.length() > CardFiles.MAX_BYTES) return out;
        try {
            for (String line : Text.read(f).split("\n")) {
                int hash = line.indexOf('#');
                String s = (hash >= 0 ? line.substring(0, hash) : line).trim().toLowerCase(Locale.US);
                if (s.length() > 0) out.add(s);
            }
        } catch (IOException ignored) {
            // No switches.
        }
        return out;
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

    /**
     * Appends timestamped lines to the log in app storage, keeping one older file when it grows
     * large; with {@code card-log}, to LOG.TXT on the card too. Every line also goes to logcat (tag
     * LensPreset), readable over adb on a body with OpenMemories-Tweak. Never throws.
     */
    synchronized void log(List<String> lines) {
        for (String l : lines) Log.i("LensPreset", l);
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(stamp).append(' ').append(l).append("\r\n");
        append(appDir, "log.txt", "log-old.txt", sb.toString());
        if (cardLog && available()) append(root(), CardFiles.LOG, "LOGOLD.TXT", sb.toString());
    }

    private static void append(File dir, String name, String oldName, String text) {
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            File f = new File(dir, name);
            if (f.length() > LOG_MAX_BYTES) {
                File old = new File(dir, oldName);
                if (old.exists() && !old.delete()) return;
                if (!f.renameTo(old)) return;
            }
            OutputStream os = new FileOutputStream(f, true);
            try {
                os.write(text.getBytes("UTF-8"));
            } finally {
                os.close();
            }
        } catch (IOException ignored) {
            // The log is a convenience; a full or read-only store must not stop the app.
        }
    }

    /** Copies the log (older part first) to LOG.TXT on the card and returns its path there. */
    String saveLog() throws IOException {
        File dir = root();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        StringBuilder sb = new StringBuilder();
        for (String name : new String[]{"log-old.txt", "log.txt"}) {
            File f = new File(appDir, name);
            if (f.isFile()) sb.append(Text.read(f));
        }
        OutputStream os = new FileOutputStream(new File(dir, CardFiles.LOG));
        try {
            os.write(sb.toString().getBytes("UTF-8"));
        } finally {
            os.close();
        }
        return CardFiles.FOLDER + "/" + CardFiles.LOG;
    }
}
