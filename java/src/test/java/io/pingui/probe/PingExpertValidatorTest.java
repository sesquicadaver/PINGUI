package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.ConfigError;
import io.pingui.probe.PingOptionCatalog.PingOption;
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

    @Test
    void validateEmptyOrNullReturnsEmptyList() {
        assertEquals(List.of(), PingExpertValidator.validateAndNormalize(null));
        assertEquals(List.of(), PingExpertValidator.validateAndNormalize(List.of()));
    }

    @Test
    void validateSkipsBlankTokens() {
        List<String> args = PingExpertValidator.validateAndNormalize(List.of("  ", "-4", "", "-n"));
        assertEquals(List.of("-4", "-n"), args);
    }

    @Test
    void validateRejectsNonFlagToken() {
        assertThrows(ConfigError.class, () -> PingExpertValidator.validateAndNormalize(List.of("128")));
    }

    @Test
    void validateRejectsUnknownOption() {
        assertThrows(ConfigError.class, () -> PingExpertValidator.validateAndNormalize(List.of("-Z")));
    }

    @Test
    void validateRejectsDuplicateOption() {
        assertThrows(ConfigError.class, () -> PingExpertValidator.validateAndNormalize(List.of("-4", "-4")));
    }

    @Test
    void validateRejectsValueOptionWithoutValue() {
        assertThrows(ConfigError.class, () -> PingExpertValidator.validateAndNormalize(List.of("-s")));
    }

    @Test
    void validateRejectsIpv4Ipv6Together() {
        assertThrows(ConfigError.class, () -> PingExpertValidator.validateAndNormalize(List.of("-4", "-6")));
    }

    @Test
    void validateFlowLabelRequiresIpv6() {
        assertThrows(ConfigError.class, () -> PingExpertValidator.validateAndNormalize(List.of("-F", "abc")));
    }

    @Test
    void validateNumericAndReverseConflict() {
        assertThrows(ConfigError.class, () -> PingExpertValidator.validateAndNormalize(List.of("-n", "-H")));
    }

    @Test
    void validateBypassRouteRequiresInterface() {
        assertThrows(ConfigError.class, () -> PingExpertValidator.validateAndNormalize(List.of("-r")));
    }

    @Test
    void validateNormalizesHexSizeAndPmtu() {
        List<String> args = PingExpertValidator.validateAndNormalize(List.of("-s", "0x80", "-M", "want"));
        assertEquals(List.of("-s", "0x80", "-M", "want"), args);
    }

    @Test
    void validateRejectsOutOfRangeSize() {
        assertThrows(ConfigError.class, () -> PingExpertValidator.validateAndNormalize(List.of("-s", "70000")));
    }

    @Test
    void validateRejectsInvalidNumericValue() {
        assertThrows(ConfigError.class, () -> PingExpertValidator.validateAndNormalize(List.of("-t", "fast")));
    }

    @Test
    void validateNormalizesTimestampModes() {
        List<String> tsonly = PingExpertValidator.validateAndNormalize(List.of("-T", "tsonly"));
        assertEquals(List.of("-T", "tsonly"), tsonly);
        List<String> prespec = PingExpertValidator.validateAndNormalize(List.of("-T", "tsprespec 10.0.0.1 10.0.0.2"));
        assertEquals(List.of("-T", "tsprespec 10.0.0.1 10.0.0.2"), prespec);
    }

    @Test
    void validateRejectsInvalidTimestamp() {
        assertThrows(ConfigError.class, () -> PingExpertValidator.validateAndNormalize(List.of("-T", "badmode")));
    }

    @Test
    void validateNormalizesHexPattern() {
        List<String> args = PingExpertValidator.validateAndNormalize(List.of("-p", "AABB"));
        assertEquals(List.of("-p", "aabb"), args);
    }

    @Test
    void validateRejectsInvalidHexPattern() {
        assertThrows(ConfigError.class, () -> PingExpertValidator.validateAndNormalize(List.of("-p", "gg")));
    }

    @Test
    void validateNormalizesFlowLabelWithIpv6() {
        List<String> args = PingExpertValidator.validateAndNormalize(List.of("-6", "-F", "AbCdE"));
        assertEquals(List.of("-6", "-F", "abcde"), args);
    }

    @Test
    void validateRejectsFlowLabelOutOfRange() {
        assertThrows(ConfigError.class, () -> PingExpertValidator.validateAndNormalize(List.of("-6", "-F", "100000")));
    }

    @Test
    void validateAndNormalizeValueRejectsFlagOption() {
        PingOption flag = PingOptionCatalog.find("-4");
        assertThrows(ConfigError.class, () -> PingExpertValidator.validateAndNormalizeValue(flag, "1"));
    }

    @Test
    void validateAndNormalizeValueRejectsBlankValue() {
        PingOption size = PingOptionCatalog.find("-s");
        assertThrows(ConfigError.class, () -> PingExpertValidator.validateAndNormalizeValue(size, " "));
    }

    @Test
    void describeValueSpecForFlagReturnsEmpty() {
        PingOption flag = PingOptionCatalog.find("-4");
        assertEquals("", PingExpertValidator.describeValueSpec(flag));
    }

    @Test
    void describeValueSpecForIntRangeAndChoices() {
        PingOption size = PingOptionCatalog.find("-s");
        assertTrue(PingExpertValidator.describeValueSpec(size).contains("65507"));
        PingOption pmtu = PingOptionCatalog.find("-M");
        assertTrue(PingExpertValidator.describeValueSpec(pmtu).contains("want"));
    }
}
