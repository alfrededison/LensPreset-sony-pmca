package com.lenspreset.core.profile;

import com.lenspreset.core.model.CameraProfile;
import com.lenspreset.core.model.LensProfile;
import com.lenspreset.core.model.Preset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every profile the app knows: the ones bundled in the APK, then the memory card's. A card file
 * with the id of a bundled profile replaces it, so a contributor can try a corrected profile
 * without a new build. A file that fails to parse is skipped and listed in {@link #problems()}.
 */
public final class ProfileSet {
    public enum Kind { CAMERA, LENS, PRESET }

    /**
     * One file to read. {@code origin} is how the file is named in problems, e.g.
     * {@code card:PRESETS/MINE.JSN}; {@code text} is null for a file that could not be read.
     */
    public static final class Entry {
        public final Kind kind;
        public final String origin;
        public final String text;
        public final boolean fromCard;

        public Entry(Kind kind, String origin, String text, boolean fromCard) {
            this.kind = kind;
            this.origin = origin;
            this.text = text;
            this.fromCard = fromCard;
        }
    }

    private final Map<String, CameraProfile> cameras = new LinkedHashMap<String, CameraProfile>();
    private final Map<String, LensProfile> lenses = new LinkedHashMap<String, LensProfile>();
    private final Map<String, Preset> presets = new LinkedHashMap<String, Preset>();
    private final List<String> problems = new ArrayList<String>();
    private final List<String> overridden = new ArrayList<String>();

    /** Bundled entries must come before card entries; within each, a repeated id is a problem. */
    public static ProfileSet load(List<Entry> entries) {
        ProfileSet set = new ProfileSet();
        Map<String, Boolean> fromCard = new LinkedHashMap<String, Boolean>();
        for (Entry e : entries) {
            if (e.text == null) {
                set.problems.add(e.origin + ": could not be read (or is larger than "
                        + CardFiles.MAX_BYTES / 1024 + " KB)");
                continue;
            }
            try {
                String id;
                switch (e.kind) {
                    case CAMERA: {
                        CameraProfile p = ProfileParser.camera(e.text);
                        id = p.id;
                        if (set.accept(fromCard, "camera:" + id, e)) set.cameras.put(id, p);
                        break;
                    }
                    case LENS: {
                        LensProfile p = ProfileParser.lens(e.text);
                        id = p.id;
                        if (set.accept(fromCard, "lens:" + id, e)) set.lenses.put(id, p);
                        break;
                    }
                    default: {
                        Preset p = ProfileParser.preset(e.text);
                        id = p.id;
                        if (set.accept(fromCard, "preset:" + id, e)) set.presets.put(id, p);
                        break;
                    }
                }
            } catch (ProfileException ex) {
                set.problems.add(e.origin + ": " + ex.getMessage());
            }
        }
        for (CameraProfile c : set.cameras.values()) {
            if (c.fixedLens != null && !set.lenses.containsKey(c.fixedLens))
                set.problems.add("camera " + c.id + ": fixed_lens \"" + c.fixedLens + "\" has no lens profile");
        }
        return set;
    }

    private boolean accept(Map<String, Boolean> seen, String key, Entry e) {
        Boolean prev = seen.get(key);
        if (prev == null) {
            seen.put(key, e.fromCard);
            return true;
        }
        if (!prev && e.fromCard) {
            seen.put(key, Boolean.TRUE);
            overridden.add(key);
            return true;
        }
        problems.add(e.origin + ": id " + key.substring(key.indexOf(':') + 1) + " is already used by another file");
        return false;
    }

    public CameraProfile camera(String id) {
        return cameras.get(id);
    }

    public LensProfile lens(String id) {
        return lenses.get(id);
    }

    public Preset preset(String id) {
        return presets.get(id);
    }

    public List<CameraProfile> cameras() {
        return new ArrayList<CameraProfile>(cameras.values());
    }

    /** Lenses sorted by name. Fixed (built-in) lenses are left out: they come with their camera. */
    public List<LensProfile> interchangeableLenses() {
        List<LensProfile> out = new ArrayList<LensProfile>();
        for (LensProfile l : lenses.values()) if (!"fixed".equals(l.mount)) out.add(l);
        Collections.sort(out, new Comparator<LensProfile>() {
            @Override
            public int compare(LensProfile a, LensProfile b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        return out;
    }

    /** Presets by {@code order}, then id. */
    public List<Preset> presets() {
        List<Preset> out = new ArrayList<Preset>(presets.values());
        Collections.sort(out, new Comparator<Preset>() {
            @Override
            public int compare(Preset a, Preset b) {
                if (a.order != b.order) return a.order < b.order ? -1 : 1;
                return a.id.compareTo(b.id);
            }
        });
        return out;
    }

    public List<String> problems() {
        return Collections.unmodifiableList(problems);
    }

    /** {@code kind:id} of bundled profiles a card file replaced. */
    public List<String> overridden() {
        return Collections.unmodifiableList(overridden);
    }
}
