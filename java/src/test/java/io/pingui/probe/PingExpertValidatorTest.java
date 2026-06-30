package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.pingui.config.ConfigError;
import java.util.List;
import org.junit.jupiter.api.Test;

class PingExpertValidatorTest {

    @Test
    void validate_rejectsExcludedCountFlag() {
        assertThrows(ConfigError.class, () -> PingExpertValidator.validateAndNormalize(List.of("-c", "1")));
    }

    @Test
    void validate_normalizesSizeFlag() {
        List<String> args = PingExpertValidator.validateAndNormalize(List.of("-s", "128"));
        assertEquals(List.of("-s", "128"), args);
    }
}
