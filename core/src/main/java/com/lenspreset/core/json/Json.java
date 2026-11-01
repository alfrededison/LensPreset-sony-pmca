package com.lenspreset.core.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A strict RFC 8259 JSON parser with no dependencies.
 *
 * <p>Objects become {@link LinkedHashMap} (key order kept), arrays {@link ArrayList}, numbers
 * {@link Double}, plus {@link String}, {@link Boolean} and {@code null}. Profiles come from the
 * memory card as well as the APK, so every error carries a line and column.
 */
public final class Json {
    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    public static Object parse(String text) throws JsonException {
        if (text == null) throw new JsonException("no input", 1, 1);
        Json p = new Json(text);
        // A UTF-8 byte order mark is tolerated: Windows editors add one.
        if (text.length() > 0 && text.charAt(0) == '﻿') p.pos = 1;
        p.skipWhitespace();
        Object value = p.readValue(0);
        p.skipWhitespace();
        if (p.pos < text.length()) throw p.error("unexpected text after the value");
        return value;
    }

    private static final int MAX_DEPTH = 64;

    private Object readValue(int depth) throws JsonException {
        if (depth > MAX_DEPTH) throw error("nested too deeply");
        if (pos >= text.length()) throw error("unexpected end of input");
        char c = text.charAt(pos);
        switch (c) {
            case '{': return readObject(depth);
            case '[': return readArray(depth);
            case '"': return readString();
            case 't': expectWord("true"); return Boolean.TRUE;
            case 'f': expectWord("false"); return Boolean.FALSE;
            case 'n': expectWord("null"); return null;
            default:
                if (c == '-' || (c >= '0' && c <= '9')) return readNumber();
                throw error("unexpected character '" + c + "'");
        }
    }

    private Map<String, Object> readObject(int depth) throws JsonException {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        pos++;
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') throw error("expected a quoted key");
            int keyPos = pos;
            String key = readString();
            if (map.containsKey(key)) {
                pos = keyPos;
                throw error("duplicate key \"" + key + "\"");
            }
            skipWhitespace();
            if (peek() != ':') throw error("expected ':'");
            pos++;
            skipWhitespace();
            map.put(key, readValue(depth + 1));
            skipWhitespace();
            char c = peek();
            pos++;
            if (c == '}') return map;
            if (c != ',') {
                pos--;
                throw error("expected ',' or '}'");
            }
        }
    }

    private List<Object> readArray(int depth) throws JsonException {
        List<Object> list = new ArrayList<Object>();
        pos++;
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue(depth + 1));
            skipWhitespace();
            char c = peek();
            pos++;
            if (c == ']') return list;
            if (c != ',') {
                pos--;
                throw error("expected ',' or ']'");
            }
        }
    }

    private String readString() throws JsonException {
        pos++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= text.length()) throw error("unterminated string");
            char c = text.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c < 0x20) {
                pos--;
                throw error("control character in string");
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= text.length()) throw error("unterminated string");
            char e = text.charAt(pos++);
            switch (e) {
                case '"': sb.append('"'); break;
                case '\\': sb.append('\\'); break;
                case '/': sb.append('/'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'u':
                    if (pos + 4 > text.length()) throw error("bad \\u escape");
                    try {
                        sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error("bad \\u escape");
                    }
                    pos += 4;
                    break;
                default:
                    pos--;
                    throw error("bad escape '\\" + e + "'");
            }
        }
    }

    private Double readNumber() throws JsonException {
        int start = pos;
        if (peek() == '-') pos++;
        if (peek() == '0') {
            pos++;
        } else if (isDigit(peek())) {
            while (isDigit(peek())) pos++;
        } else {
            throw error("bad number");
        }
        if (peek() == '.') {
            pos++;
            if (!isDigit(peek())) throw error("bad number");
            while (isDigit(peek())) pos++;
        }
        if (peek() == 'e' || peek() == 'E') {
            pos++;
            if (peek() == '+' || peek() == '-') pos++;
            if (!isDigit(peek())) throw error("bad number");
            while (isDigit(peek())) pos++;
        }
        return Double.valueOf(text.substring(start, pos));
    }

    private void expectWord(String word) throws JsonException {
        if (!text.startsWith(word, pos)) throw error("unexpected word");
        pos += word.length();
    }

    private char peek() {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') return;
            pos++;
        }
    }

    private JsonException error(String message) {
        int line = 1;
        int col = 1;
        for (int i = 0; i < pos && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        return new JsonException(message, line, col);
    }
}
