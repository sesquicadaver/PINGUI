package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.Test;

class UiPaletteTest {
    @Test
    void stylesheetResourceIsOnClasspath() {
        assertNotNull(UiPalette.class.getResource("pingui.css"), "pingui.css must be next to UiPalette");
    }

    @Test
    void applyToAddsStylesheetOnce() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            Scene scene = new Scene(new BorderPane());
            UiPalette.applyTo(scene);
            assertEquals(1, scene.getStylesheets().size());
            UiPalette.applyTo(scene);
            assertEquals(1, scene.getStylesheets().size(), "stylesheet must not duplicate");
            assertTrue(scene.getStylesheets().get(0).contains("pingui.css"));
        });
    }

    @Test
    void cssCommentContractMatchesJavaHex() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/io/pingui/ui/pingui.css"), StandardCharsets.UTF_8);
        assertTrue(css.contains(UiPalette.BG_HEX), "CSS must document BG_HEX");
        assertTrue(css.contains(UiPalette.PANEL_HEX));
        assertTrue(css.contains(UiPalette.TEXT_HEX));
        assertTrue(css.contains(UiPalette.MUTED_HEX));
        assertTrue(css.contains(UiPalette.DANGER_HEX));
        assertTrue(css.contains("-pingui-bg: " + UiPalette.BG_HEX));
        assertTrue(css.contains(".theme-dark"), "dark stub structure must remain for later");
        assertTrue(
                css.contains(".pingui-metrics") && css.contains("-fx-text-fill: -pingui-text"),
                "metrics/poll counters must force readable text on RTT row backgrounds");
        assertTrue(css.contains(".pingui-poll-counters"));
    }

    @Test
    void mainControllerSceneHasNoHardcodedFafafaFill() throws Exception {
        String src =
                Files.readString(Path.of("src/main/java/io/pingui/ui/MainController.java"), StandardCharsets.UTF_8);
        assertFalse(src.contains("Color.web(\"#fafafa\")"), "Scene fill must come from CSS / UiPalette.applyTo");
        assertTrue(src.contains("UiPalette.applyTo"), "createScene must attach stylesheet");
    }

    @Test
    void graphCanvasPaintUsesUiPalette() throws Exception {
        String src = Files.readString(Path.of("src/main/java/io/pingui/ui/GraphCanvas.java"), StandardCharsets.UTF_8);
        assertTrue(src.contains("UiPalette.BG"));
        assertTrue(src.contains("UiPalette.TEXT"));
        assertFalse(src.contains("Color.web(\"#fafafa\")"));
    }
}
