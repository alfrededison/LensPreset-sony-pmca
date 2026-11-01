package com.lenspreset.core.profile;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * The profiles and language tables packed into one APK asset by build.sh. On an A5100 each
 * AssetManager list or open took about half a second, so reading some twenty files one by one kept
 * the app loading for ten seconds; one file takes one open.
 *
 * <p>Format: for each file, a header line {@code @@ <kind> <name> <byte length>}, then exactly that
 * many bytes of UTF-8, then a newline. Kinds: {@code i18n}, {@code cameras}, {@code lenses},
 * {@code presets}.
 */
public final class AssetBundle {
    private AssetBundle() {
    }

    public static final String ASSET = "lenspreset.bundle";

    public static final class Item {
        public final String kind;
        public final String name;
        public final String text;

        Item(String kind, String name, String text) {
            this.kind = kind;
            this.name = name;
            this.text = text;
        }
    }

    public static List<Item> parse(byte[] data) throws ProfileException {
        List<Item> out = new ArrayList<Item>();
        int pos = 0;
        while (pos < data.length) {
            int eol = pos;
            while (eol < data.length && data[eol] != '\n') eol++;
            if (eol == data.length) throw new ProfileException("bundle: unterminated header at byte " + pos);
            String header = ascii(data, pos, eol);
            String[] parts = header.split(" ");
            if (parts.length != 4 || !"@@".equals(parts[0]))
                throw new ProfileException("bundle: bad header \"" + header + "\"");
            int length;
            try {
                length = Integer.parseInt(parts[3]);
            } catch (NumberFormatException e) {
                throw new ProfileException("bundle: bad length in \"" + header + "\"");
            }
            int start = eol + 1;
            if (length < 0 || start + length > data.length)
                throw new ProfileException("bundle: " + parts[2] + " is cut short");
            try {
                out.add(new Item(parts[1], parts[2], new String(data, start, length, "UTF-8")));
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
            pos = start + length;
            if (pos < data.length && data[pos] == '\n') pos++;
        }
        return out;
    }

    private static String ascii(byte[] data, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) sb.append((char) (data[i] & 0xff));
        return sb.toString();
    }
}
