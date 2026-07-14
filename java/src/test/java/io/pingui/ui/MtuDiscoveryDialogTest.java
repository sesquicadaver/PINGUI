package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.probe.MtuDiscoveryConfig;
import io.pingui.probe.MtuDiscoveryResult;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class MtuDiscoveryDialogTest {

    @Test
    void toExpertArgsBuildsDontFragmentPayload() {
        assertEquals(List.of("-4", "-M", "do", "-s", "1472"), MtuDiscoveryDialog.toExpertArgs(false, 1472));
        assertEquals(List.of("-6", "-M", "do", "-s", "1452"), MtuDiscoveryDialog.toExpertArgs(true, 1452));
    }

    @Test
    void applyPathUsesPayloadNotRecommendedMtu() {
        int payload = 1472;
        int recommendedMtu = payload + MtuDiscoveryConfig.IPV4_ICMP_OVERHEAD;
        List<String> args = MtuDiscoveryDialog.toExpertArgs(false, payload);
        assertEquals("1472", args.get(args.indexOf("-s") + 1));
        assertFalse(args.contains(Integer.toString(recommendedMtu)));
        assertTrue(recommendedMtu != payload);
    }

    @Test
    void mergeKeepsOtherFlagsAndReplacesMs() {
        List<String> current = List.of("-4", "-Q", "0x10", "-M", "probe", "-s", "64", "-t", "64");
        List<String> merged = MtuDiscoveryDialog.mergeMtuDiscoveryArgs(current, false, 1400);
        assertEquals(List.of("-4", "-Q", "0x10", "-t", "64", "-M", "do", "-s", "1400"), merged);
    }

    @Test
    void toExpertArgsRejectsNegativePayload() {
        assertThrows(IllegalArgumentException.class, () -> MtuDiscoveryDialog.toExpertArgs(false, -1));
    }

    @Test
    void ipv6FromExpertArgsRespectsAf() {
        assertTrue(MtuDiscoveryDialog.ipv6FromExpertArgs(List.of("-6", "-s", "64")));
        assertFalse(MtuDiscoveryDialog.ipv6FromExpertArgs(List.of("-4", "-6")));
        assertFalse(MtuDiscoveryDialog.ipv6FromExpertArgs(List.of()));
        assertFalse(MtuDiscoveryDialog.ipv6FromExpertArgs(null));
    }

    @Test
    void formatStepAndSummaryCoverCliffAndEmpty() {
        MtuDiscoveryResult.MtuProbeStep step = new MtuDiscoveryResult.MtuProbeStep(96, 10, 1, 10.0, true);
        assertTrue(MtuDiscoveryDialog.formatStep(step).contains("-s=96"));
        assertTrue(MtuDiscoveryDialog.formatStep(step).contains("STOP"));

        MtuDiscoveryConfig config = MtuDiscoveryConfig.ipv4Defaults();
        MtuDiscoveryResult ok =
                new MtuDiscoveryResult(OptionalInt.of(80), OptionalInt.of(108), List.of(step), true, false);
        String summary = MtuDiscoveryDialog.formatSummary(ok, config);
        assertTrue(summary.contains("108"));
        assertTrue(summary.contains("-s=80"));

        MtuDiscoveryResult empty =
                new MtuDiscoveryResult(OptionalInt.empty(), OptionalInt.empty(), List.of(step), true, false);
        assertTrue(MtuDiscoveryDialog.formatSummary(empty, config).contains("Немає успішного"));

        assertEquals("x (зупинка…)", MtuDiscoveryDialog.formatStopping("x"));
        assertEquals("x (зупинка…)", MtuDiscoveryDialog.formatStopping("x (зупинка…)"));
    }
}
