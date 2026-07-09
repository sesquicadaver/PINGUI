package io.pingui.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DaemonPidFileTest {
    @TempDir
    Path tempDir;

    @Test
    void writeReadRoundTrip() throws Exception {
        Path pidPath = tempDir.resolve("pingui.pid");
        long pid = ProcessHandle.current().pid();
        DaemonPidFile.write(pidPath, pid);
        OptionalLong read = DaemonPidFile.read(pidPath);
        assertTrue(read.isPresent());
        assertEquals(pid, read.getAsLong());
    }

    @Test
    void isRunningDetectsCurrentProcess() throws Exception {
        Path pidPath = tempDir.resolve("live.pid");
        DaemonPidFile.write(pidPath, ProcessHandle.current().pid());
        assertTrue(DaemonPidFile.isRunning(pidPath));
    }

    @Test
    void isRunningFalseForStalePid() throws Exception {
        Path pidPath = tempDir.resolve("stale.pid");
        Files.writeString(pidPath, "999999999");
        assertFalse(DaemonPidFile.isRunning(pidPath));
    }
}
