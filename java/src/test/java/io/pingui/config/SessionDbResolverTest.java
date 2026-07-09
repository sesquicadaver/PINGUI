package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SessionDbResolverTest {

    @Test
    void resolvePrefersCliOverYamlAndGui() {
        Path cli = Path.of("cli.db");
        Path yaml = Path.of("yaml.db");
        Path gui = Path.of("gui.db");
        assertEquals(
                Optional.of(cli), SessionDbResolver.resolve(Optional.of(cli), Optional.of(yaml), Optional.of(gui)));
    }

    @Test
    void resolveUsesYamlWhenCliAbsent() {
        Path yaml = Path.of("yaml.db");
        assertEquals(
                Optional.of(yaml),
                SessionDbResolver.resolve(Optional.empty(), Optional.of(yaml), Optional.of(Path.of("gui.db"))));
    }

    @Test
    void resolveUsesGuiWhenCliAndYamlAbsent() {
        Path gui = Path.of("gui.db");
        assertEquals(Optional.of(gui), SessionDbResolver.resolve(Optional.empty(), Optional.empty(), Optional.of(gui)));
    }

    @Test
    void canPickGuiPathOnlyWithoutCliOrYaml() {
        assertFalse(SessionDbResolver.canPickGuiPath(Optional.of(Path.of("cli.db")), Optional.empty()));
        assertFalse(SessionDbResolver.canPickGuiPath(Optional.empty(), Optional.of(Path.of("yaml.db"))));
        assertTrue(SessionDbResolver.canPickGuiPath(Optional.empty(), Optional.empty()));
    }
}
