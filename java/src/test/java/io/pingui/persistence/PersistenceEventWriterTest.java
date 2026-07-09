package io.pingui.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PersistenceEventWriterTest {
    @Test
    void probeErrorPayloadEscapesQuotes() {
        String json = PersistenceEventWriter.probeErrorPayload("h", "say \"hi\"");
        assertTrue(json.contains("\\\"hi\\\""));
        assertTrue(json.contains("\"host\":\"h\""));
    }
}
