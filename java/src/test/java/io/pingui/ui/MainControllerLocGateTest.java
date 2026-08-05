package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** P26-005: MainController must stay a thin shell after coordinator extraction. */
class MainControllerLocGateTest {
    private static final int MAX_LOC = 550;

    @Test
    void mainControllerSourceWithinLocBudget() throws Exception {
        Path source = Path.of("src/main/java/io/pingui/ui/MainController.java");
        long lines = Files.lines(source, StandardCharsets.UTF_8).count();
        assertTrue(
                lines <= MAX_LOC, "MainController.java has " + lines + " lines; budget is " + MAX_LOC + " (P26-005)");
    }
}
