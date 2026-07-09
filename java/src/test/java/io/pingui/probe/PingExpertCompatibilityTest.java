package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.pingui.config.ConfigError;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PingExpertCompatibilityTest {

    @Test
    void exclusivePartnerForAddressFamily() {
        assertEquals("-6", PingExpertCompatibility.exclusivePartner("-4"));
        assertEquals("-4", PingExpertCompatibility.exclusivePartner("-6"));
        assertEquals("-H", PingExpertCompatibility.exclusivePartner("-n"));
        assertEquals("-n", PingExpertCompatibility.exclusivePartner("-H"));
        assertNull(PingExpertCompatibility.exclusivePartner("-s"));
    }

    @Test
    void validateRejectsIpv4Ipv6Together() {
        assertThrows(ConfigError.class, () -> PingExpertCompatibility.validate(Set.of("-4", "-6")));
    }

    @Test
    void validateRejectsNumericAndReverseTogether() {
        assertThrows(ConfigError.class, () -> PingExpertCompatibility.validate(Set.of("-n", "-H")));
    }

    @Test
    void validateFlowLabelRequiresIpv6() {
        assertThrows(ConfigError.class, () -> PingExpertCompatibility.validate(Set.of("-F")));
    }

    @Test
    void validateBypassRouteRequiresInterface() {
        assertThrows(ConfigError.class, () -> PingExpertCompatibility.validate(Set.of("-r")));
    }

    @Test
    void validateAcceptsIpv6WithFlowLabel() {
        PingExpertCompatibility.validate(Set.of("-6", "-F"));
    }
}
