package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.ConfigError;
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

    @Test
    void rejectsPacketSizeOutOfRange() {
        ConfigError error =
                assertThrows(
                        ConfigError.class,
                        () -> PingExpertValidator.validateAndNormalize(List.of("-s", "70000")));
        assertTrue(error.getMessage().contains("-s"));
        assertTrue(error.getMessage().contains("65507"));
    }

    @Test
    void rejectsInvalidPmtudiscMode() {
        ConfigError error =
                assertThrows(
                        ConfigError.class,
                        () -> PingExpertValidator.validateAndNormalize(List.of("-M", "maybe")));
        assertTrue(error.getMessage().contains("-M"));
    }

    @Test
    void normalizesPmtudiscChoice() {
        List<String> args = PingExpertValidator.validateAndNormalize(List.of("-M", "WANT"));
        assertEquals(List.of("-M", "want"), args);
    }

    @Test
    void rejectsTtlOutOfRange() {
        ConfigError error =
                assertThrows(
                        ConfigError.class,
                        () -> PingExpertValidator.validateAndNormalize(List.of("-t", "0")));
        assertTrue(error.getMessage().contains("-t"));
    }

    @Test
    void acceptsTimestampTsonly() {
        List<String> args = PingExpertValidator.validateAndNormalize(List.of("-T", "tsonly"));
        assertEquals(List.of("-T", "tsonly"), args);
    }

    @Test
    void acceptsTimestampTsprespec() {
        List<String> args =
                PingExpertValidator.validateAndNormalize(List.of("-T", "tsprespec 10.0.0.1 10.0.0.2"));
        assertEquals(List.of("-T", "tsprespec 10.0.0.1 10.0.0.2"), args);
    }

    @Test
    void rejectsInvalidTimestamp() {
        ConfigError error =
                assertThrows(
                        ConfigError.class,
                        () -> PingExpertValidator.validateAndNormalize(List.of("-T", "invalid")));
        assertTrue(error.getMessage().contains("-T"));
    }

    @Test
    void acceptsQosHexInRange() {
        List<String> args = PingExpertValidator.validateAndNormalize(List.of("-Q", "0xff"));
        assertEquals(List.of("-Q", "0xff"), args);
    }

    @Test
    void rejectsQosOutOfRange() {
        ConfigError error =
                assertThrows(
                        ConfigError.class,
                        () -> PingExpertValidator.validateAndNormalize(List.of("-Q", "256")));
        assertTrue(error.getMessage().contains("-Q"));
    }

    @Test
    void rejectsNegativeMark() {
        ConfigError error =
                assertThrows(
                        ConfigError.class,
                        () -> PingExpertValidator.validateAndNormalize(List.of("-m", "-1")));
        assertTrue(error.getMessage().contains("-m"));
    }

    @Test
    void rejectsInvalidHexPattern() {
        ConfigError error =
                assertThrows(
                        ConfigError.class,
                        () -> PingExpertValidator.validateAndNormalize(List.of("-p", "gg")));
        assertTrue(error.getMessage().contains("-p"));
    }

    @Test
    void rejectsFlowLabelOutOfRange() {
        ConfigError error =
                assertThrows(
                        ConfigError.class,
                        () -> PingExpertValidator.validateAndNormalize(List.of("-6", "-F", "100000")));
        assertTrue(error.getMessage().contains("-F"));
    }

    @Test
    void describeValueSpecForIntRange() {
        PingOptionCatalog.PingOption option = PingOptionCatalog.find("-s");
        assertTrue(PingExpertValidator.describeValueSpec(option).contains("65507"));
    }
}
