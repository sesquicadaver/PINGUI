package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.AppOptions;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.Scene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StartupBootstrapTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void resetHooks() {
        StartupBootstrap.resetTestHooks();
    }

    @Test
    void loadRunsHeavyPhasesOffCallerWhenInvokedFromWorker() throws Exception {
        Path config = writeMinimalConfig();
        AppOptions options = optionsWithConfig(config);
        List<String> phases = new ArrayList<>();
        StartupBootstrap.phaseListener = phases::add;

        Thread worker = new Thread(
                () -> {
                    try {
                        StartupBootstrap.Result result = StartupBootstrap.load(options);
                        assertNotNull(result.document());
                        assertNotNull(result.store());
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                },
                "pingui-startup-test");
        worker.start();
        worker.join(30_000);
        assertFalse(worker.isAlive(), "bootstrap worker must finish");

        assertEquals(List.of("profile", "geoip", "sqlite", "done"), phases);
        String heavy = StartupBootstrap.lastHeavyThread.get();
        assertNotNull(heavy);
        assertTrue(heavy.contains("pingui-startup-test"), "heavy I/O thread was " + heavy);
        assertFalse(heavy.toLowerCase().contains("javafx"), "heavy I/O must not run on JavaFX thread");
    }

    @Test
    void shellSceneIsBuiltBeforeBootstrapFinishes() throws Exception {
        Path config = writeMinimalConfig();
        AppOptions options = optionsWithConfig(config);
        CountDownLatch enteredHeavy = new CountDownLatch(1);
        CountDownLatch releaseHeavy = new CountDownLatch(1);
        AtomicBoolean sceneReadyBeforeDone = new AtomicBoolean(false);

        StartupBootstrap.phaseListener = phase -> {
            if ("profile".equals(phase)) {
                enteredHeavy.countDown();
                try {
                    releaseHeavy.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        Thread worker = new Thread(
                () -> {
                    try {
                        StartupBootstrap.load(options);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                },
                "pingui-startup-blocked");
        worker.start();
        assertTrue(enteredHeavy.await(10, TimeUnit.SECONDS), "bootstrap must enter profile phase");

        FxTestSupport.runOnFxThread(() -> {
            MainController shell = new MainController(options);
            Scene scene = shell.createScene();
            assertNotNull(scene);
            assertTrue(worker.isAlive(), "bootstrap still blocked while shell Scene exists");
            sceneReadyBeforeDone.set(true);
            shell.shutdown();
        });

        releaseHeavy.countDown();
        worker.join(30_000);
        assertTrue(sceneReadyBeforeDone.get());
        assertFalse(worker.isAlive());
    }

    @Test
    void mainControllerShellCtorSourceAvoidsHeavyIo() throws Exception {
        String src =
                Files.readString(Path.of("src/main/java/io/pingui/ui/MainController.java"), StandardCharsets.UTF_8);
        // Shell ctor body must not call these; deprecated sync ctor may still contain them.
        int shellCtor = src.indexOf("public MainController(AppOptions options) {");
        int deprecatedCtor = src.indexOf("@Deprecated");
        assertTrue(shellCtor > 0);
        assertTrue(deprecatedCtor > shellCtor);
        String shellBody = src.substring(shellCtor, deprecatedCtor);
        assertFalse(shellBody.contains("ProfilesConfig.load"));
        assertFalse(shellBody.contains("GeoCountry.configure"));
        assertFalse(shellBody.contains("SessionDatabase"));
        assertFalse(shellBody.contains("StartupBootstrap.load"));
    }

    @Test
    void invalidYamlFailsBootstrapAndOnBootstrapFailedKeepsServicesNotReady() throws Exception {
        Path config = tempDir.resolve("bad.yaml");
        Files.writeString(config, "not: [valid", StandardCharsets.UTF_8);
        AppOptions options = optionsWithConfig(config);

        Exception failed = null;
        try {
            StartupBootstrap.load(options);
        } catch (Exception ex) {
            failed = ex;
        }
        assertNotNull(failed, "corrupt YAML must fail StartupBootstrap.load");

        Exception bootstrapError = failed;
        FxTestSupport.runOnFxThread(() -> {
            MainController shell = new MainController(options);
            shell.createScene();
            shell.onBootstrapFailed(bootstrapError);
            assertFalse(shell.servicesReady());
            assertTrue(shell.statusTextForTest().contains("Помилка завантаження"));
            shell.shutdown();
        });
    }

    @Test
    void attachAfterShutdownDoesNotMarkServicesReady() throws Exception {
        Path config = writeMinimalConfig();
        AppOptions options = optionsWithConfig(config);
        StartupBootstrap.Result result = StartupBootstrap.load(options);

        FxTestSupport.runOnFxThread(() -> {
            MainController shell = new MainController(options);
            shell.createScene();
            shell.shutdown();
            shell.attachBootstrap(result);
            assertFalse(shell.servicesReady(), "attach after shutdown must be a no-op");
        });
    }

    @Test
    void pinguiApplicationStartUsesBootstrapAsync() throws Exception {
        String src =
                Files.readString(Path.of("src/main/java/io/pingui/PinguiApplication.java"), StandardCharsets.UTF_8);
        assertTrue(src.contains("StartupBootstrap.load"));
        assertTrue(src.contains("CompletableFuture"));
        assertTrue(src.contains("stage.show()"));
        int showAt = src.indexOf("stage.show()");
        int bootstrapAt = src.indexOf("StartupBootstrap.load");
        assertTrue(showAt > 0 && bootstrapAt > showAt, "show() must precede StartupBootstrap.load scheduling");
        assertFalse(
                src.contains("ProfilesConfig.load(options.configPath())"),
                "GUI start must not load profiles on the FX thread");
    }

    private Path writeMinimalConfig() throws Exception {
        Path config = tempDir.resolve("hosts.yaml");
        Files.writeString(
                config,
                """
                active_profile: default
                profiles:
                  default:
                    interval: 1.0
                    max_hops: 20
                    timeout: 0.5
                    probe: auto
                    hosts: []
                """,
                StandardCharsets.UTF_8);
        return config;
    }

    private static AppOptions optionsWithConfig(Path config) {
        AppOptions defaults = AppOptions.defaults();
        return new AppOptions(
                config,
                defaults.profileOverrides(),
                defaults.alertOverrides(),
                defaults.persistenceOverrides(),
                defaults.telemetryOverrides(),
                defaults.timeSeriesOverrides(),
                false,
                false,
                defaults.geoipHintsPath(),
                false,
                defaults.asnHintsPath(),
                defaults.asnTimeoutMs(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                defaults.runMode(),
                defaults.pidFilePath(),
                Optional.empty(),
                Optional.empty(),
                defaults.telemetryRetentionDays(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}
