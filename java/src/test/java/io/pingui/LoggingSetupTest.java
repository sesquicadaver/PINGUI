package io.pingui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LoggingSetupTest {
    private static final String DEFAULT_LEVEL = "org.slf4j.simpleLogger.defaultLogLevel";
    private static final String PINGUI_LEVEL = "org.slf4j.simpleLogger.log.io.pingui";

    @AfterEach
    void restoreDefaults() {
        System.clearProperty(DEFAULT_LEVEL);
        System.clearProperty(PINGUI_LEVEL);
    }

    @Test
    void configureVerbose() {
        LoggingSetup.configure(true);
        assertEquals("debug", System.getProperty(DEFAULT_LEVEL));
        assertEquals("debug", System.getProperty(PINGUI_LEVEL));
    }

    @Test
    void configureQuiet() {
        LoggingSetup.configure(false);
        assertEquals("error", System.getProperty(DEFAULT_LEVEL));
        assertEquals("info", System.getProperty(PINGUI_LEVEL));
    }
}
