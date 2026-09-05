package io.pingui.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UiI18nTest {
    @AfterEach
    void resetLocale() {
        UiI18n.setLocale(UiLocale.UK);
    }

    @Test
    void ukCanonProvidesMenuFile() {
        UiI18n.setLocale(UiLocale.UK);
        assertEquals("Файл", UiI18n.get("menu.file"));
    }

    @Test
    void englishBundleSwitchesMenuFile() {
        UiI18n.setLocale(UiLocale.EN);
        assertEquals("File", UiI18n.get("menu.file"));
        assertEquals("Simple", UiI18n.get("mode.simple"));
    }

    @Test
    void missingKeyFallsBackToKeyString() {
        UiI18n.setLocale(UiLocale.EN);
        assertEquals("definitely.missing.key.xyz", UiI18n.get("definitely.missing.key.xyz"));
    }

    @Test
    void formatArgs() {
        UiI18n.setLocale(UiLocale.EN);
        assertTrue(UiI18n.get("host.added", "8.8.8.8").contains("8.8.8.8"));
    }

    @Test
    void fromCodeParsesSupportedLocales() {
        assertEquals(UiLocale.PL, UiLocale.fromCode("pl").orElseThrow());
        assertEquals(UiLocale.UK, UiLocale.fromCode("uk-UA").orElseThrow());
        assertTrue(UiLocale.fromCode("de").isEmpty());
        assertTrue(UiLocale.fromCode("fr").isEmpty());
    }

    @Test
    void polishBundleIsNotUkrainianForModeSimple() {
        UiI18n.setLocale(UiLocale.PL);
        String simple = UiI18n.get("mode.simple");
        assertFalse(simple.contains("Простий"));
        assertFalse(simple.isBlank());
    }

    @Test
    void allLocaleBundlesHaveEnKeyParityWithoutDuplicateHistoryInitialRoute() throws Exception {
        Set<String> enKeys = loadKeys("messages_en.properties");
        assertTrue(enKeys.contains("history.initial_route"));
        assertTrue(enKeys.contains("host.state.up"));
        for (UiLocale locale : UiLocale.values()) {
            String resource = "messages_" + locale.code() + ".properties";
            Set<String> keys = loadKeys(resource);
            assertEquals(enKeys, keys, () -> "key mismatch for " + resource);
        }
        Set<String> root = loadKeys("messages.properties");
        assertEquals(enKeys, root);
    }

    private static Set<String> loadKeys(String resourceName) throws Exception {
        String path = "/io/pingui/i18n/" + resourceName;
        try (InputStream in = UiI18nTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing classpath resource " + path);
            Properties props = new Properties();
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return new LinkedHashSet<>(props.stringPropertyNames());
        }
    }
}
