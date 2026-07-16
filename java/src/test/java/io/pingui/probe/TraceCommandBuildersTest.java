package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Direct unit tests for OS trace command builders (P19-003). */
class TraceCommandBuildersTest {

    @Test
    void linuxBsdBuildsTracerouteWithNumericFlags() {
        LinuxTracerouteCommand builder = new LinuxTracerouteCommand(TracerouteFlavor.BSD);
        List<String> command = builder.buildCommand("8.8.8.8", 20, 0.5);

        assertEquals("traceroute", command.get(0));
        assertTrue(command.contains("-n"));
        assertTrue(command.contains("-m"));
        assertTrue(command.contains("20"));
        assertTrue(command.contains("-w"));
        assertTrue(command.contains("1"));
        assertTrue(command.contains("-q"));
        assertEquals("8.8.8.8", command.get(command.size() - 1));
        assertFalse(command.contains("-6"));
    }

    @Test
    void linuxGnuInetutilsOmitsNumericOnlyFlag() {
        LinuxTracerouteCommand builder = new LinuxTracerouteCommand(TracerouteFlavor.GNU_INETUTILS);
        List<String> command = builder.buildCommand("8.8.8.8", 15, 2.0);

        assertEquals("traceroute", command.get(0));
        assertFalse(command.contains("-n"));
        assertTrue(command.contains("-m"));
        assertTrue(command.contains("15"));
        assertTrue(command.contains("-w"));
        assertTrue(command.contains("2"));
    }

    @Test
    void linuxAddsIpv6FlagForLiteral() {
        LinuxTracerouteCommand builder = new LinuxTracerouteCommand(TracerouteFlavor.BSD);
        List<String> command = builder.buildCommand("2001:db8::1", 20, 0.5);

        assertTrue(command.contains("-6"));
        assertEquals("2001:db8::1", command.get(command.size() - 1));
    }

    @Test
    void macBuildsBsdStyleCommand() {
        MacTracerouteCommand builder = new MacTracerouteCommand();
        List<String> command = builder.buildCommand("example.com", 30, 1.0);

        assertEquals("traceroute", command.get(0));
        assertTrue(command.contains("-n"));
        assertTrue(command.contains("-m"));
        assertTrue(command.contains("30"));
        assertTrue(command.contains("-w"));
        assertTrue(command.contains("1"));
        assertEquals("example.com", command.get(command.size() - 1));
        assertFalse(command.contains("-6"));
    }

    @Test
    void macAddsIpv6FlagForLiteral() {
        MacTracerouteCommand builder = new MacTracerouteCommand();
        List<String> command = builder.buildCommand("2001:db8::1", 20, 0.5);

        assertTrue(command.contains("-6"));
    }

    @Test
    void windowsBuildsTracertWithMinimumWait() {
        WindowsTracertCommand builder = new WindowsTracertCommand();
        List<String> command = builder.buildCommand("8.8.8.8", 20, 0.5);

        assertTrue(
                command.get(0).toLowerCase().contains("tracert"),
                "executable should resolve to tracert: " + command.get(0));
        assertTrue(command.contains("-d"));
        assertTrue(command.contains("-h"));
        assertTrue(command.contains("20"));
        assertTrue(command.contains("-w"));
        assertTrue(command.contains("4000"));
        assertEquals("8.8.8.8", command.get(command.size() - 1));
    }

    @Test
    void windowsAddsIpv6FlagForLiteral() {
        WindowsTracertCommand builder = new WindowsTracertCommand();
        List<String> command = builder.buildCommand("2001:db8::1", 20, 5.0);

        assertTrue(command.contains("-6"));
        assertTrue(command.contains("5000"));
    }
}
