package com.lenspreset.core.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {
    @Test
    void parsesNestedValues() throws Exception {
        Object v = Json.parse("{\"a\": [1, 2.5, -3e2], \"b\": {\"c\": true, \"d\": null}, \"e\": \"x\\u00e9\\n\"}");
        Map<?, ?> m = (Map<?, ?>) v;
        List<?> a = (List<?>) m.get("a");
        assertEquals(1.0, a.get(0));
        assertEquals(2.5, a.get(1));
        assertEquals(-300.0, a.get(2));
        Map<?, ?> b = (Map<?, ?>) m.get("b");
        assertEquals(Boolean.TRUE, b.get("c"));
        assertTrue(b.containsKey("d"));
        assertNull(b.get("d"));
        assertEquals("xé\n", m.get("e"));
    }

    @Test
    void keepsKeyOrder() throws Exception {
        Map<?, ?> m = (Map<?, ?>) Json.parse("{\"z\": 1, \"a\": 2, \"m\": 3}");
        assertEquals("[z, a, m]", m.keySet().toString());
    }

    @Test
    void toleratesByteOrderMark() throws Exception {
        assertEquals(1.0, Json.parse("﻿ 1 "));
    }

    @Test
    void reportsLineAndColumn() {
        JsonException e = assertThrows(JsonException.class, () -> Json.parse("{\n  \"a\": 1,\n  \"b\" 2\n}"));
        assertEquals(3, e.line);
        assertEquals(7, e.column);
    }

    @Test
    void rejectsCommentsTrailingCommasAndDuplicates() {
        assertThrows(JsonException.class, () -> Json.parse("{\"a\": 1 // no\n}"));
        assertThrows(JsonException.class, () -> Json.parse("[1, 2,]"));
        assertThrows(JsonException.class, () -> Json.parse("{\"a\": 1, \"a\": 2}"));
        assertThrows(JsonException.class, () -> Json.parse("01"));
        assertThrows(JsonException.class, () -> Json.parse("{} x"));
        assertThrows(JsonException.class, () -> Json.parse("\"unterminated"));
    }

    @Test
    void rejectsDeepNesting() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) sb.append('[');
        for (int i = 0; i < 100; i++) sb.append(']');
        assertThrows(JsonException.class, () -> Json.parse(sb.toString()));
    }
}
