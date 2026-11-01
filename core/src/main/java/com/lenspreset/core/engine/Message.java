package com.lenspreset.core.engine;

import com.lenspreset.core.i18n.I18n;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Something to tell the user, using app translations or text carried by a preset. */
public final class Message {
    public enum Level { HINT, INFO, WARN }

    public final String key;
    public final Map<String, String> args;
    public final Level level;
    public final Map<String, String> localizedText;

    public Message(Level level, String key, Map<String, String> args) {
        this(level, key, args, Collections.<String, String>emptyMap());
    }

    private Message(Level level, String key, Map<String, String> args, Map<String, String> localizedText) {
        this.level = level;
        this.key = key;
        this.args = Collections.unmodifiableMap(args);
        this.localizedText = Collections.unmodifiableMap(new LinkedHashMap<String, String>(localizedText));
    }

    public static Message localized(Level level, String key, Map<String, String> text) {
        return new Message(level, key, Collections.<String, String>emptyMap(), text);
    }

    /** Resolve at display time so changing language also updates preset-owned text. */
    public String render(I18n i18n) {
        return localizedText.isEmpty() ? i18n.t(key, args) : i18n.pick(localizedText);
    }

    public static Message of(Level level, String key, String... pairs) {
        Map<String, String> args = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < pairs.length; i += 2) args.put(pairs[i], pairs[i + 1]);
        return new Message(level, key, args);
    }

    @Override
    public String toString() {
        return level + " " + key + args;
    }
}
