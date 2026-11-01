package com.lenspreset.core.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Something to tell the user, as an i18n key and its arguments; the app translates it. */
public final class Message {
    public enum Level { HINT, INFO, WARN }

    public final String key;
    public final Map<String, String> args;
    public final Level level;

    public Message(Level level, String key, Map<String, String> args) {
        this.level = level;
        this.key = key;
        this.args = Collections.unmodifiableMap(args);
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
