package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.ConfigError;
import io.pingui.config.PingExpertEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class PingExpertValidatorTest {
    @Test
    void rejectsExcludedCountFlag() {
        ConfigError error =
                assertThrows(
                        ConfigError.class,
                        () -> PingExpertValidator.validateAndNormalize(List.of("-c", "5")));
        assertTrue(error.getMessage().contains("-c"));
    }

    @Test
    void rejectsMutuallyExclusiveIpVersions() {
        ConfigError error =
                assertThrows(
                        ConfigError.class,
                        () -> PingExpertValidator.validateAndNormalize(List.of("-4", "-6")));
        assertTrue(error.getMessage().contains("mutually exclusive"));
    }

    @Test
    void normalizesValidArgs() {
        List<String> args = PingExpertValidator.validateAndNormalize(List.of("-4", "-s", "64"));
        assertEquals(List.of("-4", "-s", "64"), args);
    }
}
