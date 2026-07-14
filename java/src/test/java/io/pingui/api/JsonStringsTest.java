package io.pingui.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Escaping contract for API JSON string values (P15-050). */
class JsonStringsTest {
    @Test
    void quoteEscapesSpecialCharacters() {
        assertEquals("null", JsonStrings.quote(null));
        assertEquals("\"plain\"", JsonStrings.quote("plain"));
        assertEquals("\"a\\\"b\"", JsonStrings.quote("a\"b"));
        assertEquals("\"a\\\\b\"", JsonStrings.quote("a\\b"));
        assertEquals("\"a\\nb\"", JsonStrings.quote("a\nb"));
        assertEquals("\"a\\u0001b\"", JsonStrings.quote("a\u0001b"));
    }
}
