package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HopNode;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.event.EventType;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

class GraphCanvasTest {
    @Test
    void invalidateStrategyDocumentsStep1AndCoalesce() {
        assertTrue(GraphCanvas.INVALIDATE_STRATEGY.contains("step1-clearRect-no-buffer-churn"));
        assertTrue(GraphCanvas.INVALIDATE_STRATEGY.contains("coalesced-pulse"));
    }

    @Test
    void repeatedPaintAtSameSizeDoesNotResizeCanvasBuffer() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            GraphCanvas graph = new GraphCanvas();
            StackPane root = new StackPane(graph);
            Scene scene = new Scene(root, 400, 300);
            graph.resize(400, 300);
            graph.layout();
            graph.renderRoute(sampleRoute(), ip -> 1.0, List.of());
            graph.paintForTest();

            assertEquals(400.0, graph.canvasBufferWidth(), 0.001);
            assertEquals(300.0, graph.canvasBufferHeight(), 0.001);
            graph.resetCanvasResizeCount();

            for (int i = 0; i < 10; i++) {
                graph.paintForTest();
            }

            assertEquals(0, graph.canvasResizeCount(), "same-size paint must not call setWidth/setHeight");
            assertEquals(400.0, graph.canvasBufferWidth(), 0.001);
            assertEquals(300.0, graph.canvasBufferHeight(), 0.001);
            assertEquals(400.0, scene.getWidth(), 0.001);
        });
    }

    @Test
    void regionSizeChangeResizesBufferOnceToTarget() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            GraphCanvas graph = new GraphCanvas();
            new Scene(new StackPane(graph), 200, 100);
            graph.resize(200, 100);
            graph.layout();
            graph.paintForTest();
            graph.resetCanvasResizeCount();

            graph.resize(320, 240);
            graph.layout();

            assertEquals(1, graph.canvasResizeCount());
            assertEquals(320.0, graph.canvasBufferWidth(), 0.001);
            assertEquals(240.0, graph.canvasBufferHeight(), 0.001);
        });
    }

    @Test
    void requestRedrawBurstCoalescesToOnePaint() throws Exception {
        java.util.concurrent.atomic.AtomicReference<GraphCanvas> graphRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        FxTestSupport.runOnFxThread(() -> {
            GraphCanvas graph = new GraphCanvas();
            new Scene(new StackPane(graph), 400, 300);
            graph.resize(400, 300);
            graph.layout();
            graph.paintForTest();
            graph.resetPaintCount();
            for (int i = 0; i < 20; i++) {
                graph.requestRedrawForTest();
            }
            assertEquals(0, graph.paintCount(), "paint must wait for coalesced pulse");
            graphRef.set(graph);
        });
        // Await FX pulse on the test thread (must not block the FX thread).
        CountDownLatch pulse = new CountDownLatch(1);
        Platform.runLater(pulse::countDown);
        assertTrue(pulse.await(5, TimeUnit.SECONDS), "coalesced paint pulse timed out");

        java.util.concurrent.atomic.AtomicInteger paints = new java.util.concurrent.atomic.AtomicInteger();
        FxTestSupport.runOnFxThread(() -> paints.set(graphRef.get().paintCount()));
        assertEquals(1, paints.get(), "20 requestRedraw calls must coalesce to one paint");
    }

    @Test
    void sourceHasNoWidthPlusOneHack() throws Exception {
        // Guard against reintroducing the old Prism toggle: setWidth(width + 1.0).
        String src =
                java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/io/pingui/ui/GraphCanvas.java"));
        assertFalse(src.contains("setWidth(width + 1"), "buffer toggle setWidth(width + 1…) must stay removed");
        assertFalse(src.contains("setWidth(width + 1.0)"));
    }

    // --- G2 hostile scenarios: drag burst, layout+drag same pulse, off-thread renderRoute ---

    @Test
    void dragBurstCoalescesToOnePaintWithFinalTransformApplied() throws Exception {
        java.util.concurrent.atomic.AtomicReference<GraphCanvas> graphRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        FxTestSupport.runOnFxThread(() -> {
            GraphCanvas graph = new GraphCanvas();
            new Scene(new StackPane(graph), 400, 300);
            graph.resize(400, 300);
            graph.layout();
            graph.paintForTest();
            graph.resetPaintCount();

            graph.getOnMousePressed().handle(pressEvent(graph, 10, 10));
            // Hostile drag burst: 40 drag events fired inside a single FX pulse window.
            for (int i = 1; i <= 40; i++) {
                graph.getOnMouseDragged().handle(dragEvent(graph, 10 + i, 10 + i));
            }
            assertEquals(0, graph.paintCount(), "burst must not paint before the coalesced pulse");
            graphRef.set(graph);
        });

        awaitOneFxPulse();

        FxTestSupport.runOnFxThread(() -> {
            GraphCanvas graph = graphRef.get();
            assertEquals(1, graph.paintCount(), "drag burst of 40 events must coalesce to exactly one paint");
            // Last drag went to (50,50): pan must reflect the final event, not a dropped intermediate one.
            assertEquals(40.0, graph.viewTransform().panX(), 0.001);
            assertEquals(40.0, graph.viewTransform().panY(), 0.001);
        });
    }

    @Test
    void layoutAndDragInSamePulseCoalesceToOnePaint() throws Exception {
        java.util.concurrent.atomic.AtomicReference<GraphCanvas> graphRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        FxTestSupport.runOnFxThread(() -> {
            GraphCanvas graph = new GraphCanvas();
            new Scene(new StackPane(graph), 200, 100);
            graph.resize(200, 100);
            graph.layout();
            graph.paintForTest();
            graph.resetPaintCount();
            graph.resetCanvasResizeCount();

            // Same FX pulse: a real layout-driven resize AND a user drag both request a redraw
            // before the pulse has a chance to run.
            graph.resize(320, 240);
            graph.layout();
            graph.getOnMousePressed().handle(pressEvent(graph, 5, 5));
            graph.getOnMouseDragged().handle(dragEvent(graph, 25, 15));

            assertEquals(0, graph.paintCount(), "layout+drag queued in the same pulse must not paint yet");
            graphRef.set(graph);
        });

        awaitOneFxPulse();

        FxTestSupport.runOnFxThread(() -> {
            GraphCanvas graph = graphRef.get();
            assertEquals(1, graph.paintCount(), "layout resize + drag in one pulse must coalesce to one paint");
            assertEquals(1, graph.canvasResizeCount(), "buffer resizes once even with a queued drag redraw");
            assertEquals(320.0, graph.canvasBufferWidth(), 0.001);
            assertEquals(240.0, graph.canvasBufferHeight(), 0.001);
            assertEquals(20.0, graph.viewTransform().panX(), 0.001);
            assertEquals(10.0, graph.viewTransform().panY(), 0.001);
        });
    }

    @Test
    void offThreadRenderRouteEventuallyPaintsWithoutException() throws Exception {
        java.util.concurrent.atomic.AtomicReference<GraphCanvas> graphRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        FxTestSupport.runOnFxThread(() -> {
            GraphCanvas graph = new GraphCanvas();
            new Scene(new StackPane(graph), 400, 300);
            graph.resize(400, 300);
            graph.layout();
            graph.paintForTest();
            graphRef.set(graph);
        });
        GraphCanvas graph = graphRef.get();
        // Constructing the Scene above schedules its own coalesced paint (sceneProperty listener);
        // paintForTest() bypasses that pending schedule without clearing it (documented as an
        // immediate/direct paint). Drain it before taking the baseline so the assertion below
        // measures only the off-thread renderRoute's own paint, not this unrelated leftover pulse.
        awaitOneFxPulse();
        FxTestSupport.runOnFxThread(graph::resetPaintCount);

        // Hostile: renderRoute invoked directly off the FX Application Thread, unlike every
        // production caller (RouteGraphPresenter is always reached via Platform.runLater).
        List<Throwable> offThreadErrors = new CopyOnWriteArrayList<>();
        Thread offThread = new Thread(
                () -> {
                    try {
                        graph.renderRoute(sampleRoute(), ip -> 1.0, List.of());
                    } catch (Throwable t) {
                        offThreadErrors.add(t);
                    }
                },
                "hostile-off-fx-renderRoute");
        offThread.start();
        offThread.join(5000);
        assertFalse(offThread.isAlive(), "off-thread renderRoute call itself must not hang");
        assertTrue(offThreadErrors.isEmpty(), "off-thread renderRoute must not throw: " + offThreadErrors);

        awaitOneFxPulse();

        FxTestSupport.runOnFxThread(() -> assertEquals(
                1, graph.paintCount(), "off-thread renderRoute must still reach exactly one coalesced paint"));
    }

    /**
     * Stress scenario: several background threads hammer {@link GraphCanvas#renderRoute} off the
     * FX thread while the FX thread itself is agitated with coalesced-redraw requests. {@code
     * paintDirty}/{@code paintScheduled} are plain (non-volatile) fields, so this exercises the
     * memory-visibility hazard of that design under real thread contention, not just single-thread
     * bursts.
     */
    @Test
    void offThreadRenderRouteBurstRacingFxRedrawsStaysThreadSafe() throws Exception {
        java.util.concurrent.atomic.AtomicReference<GraphCanvas> graphRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        FxTestSupport.runOnFxThread(() -> {
            GraphCanvas graph = new GraphCanvas();
            new Scene(new StackPane(graph), 400, 300);
            graph.resize(400, 300);
            graph.layout();
            graph.paintForTest();
            graphRef.set(graph);
        });
        GraphCanvas graph = graphRef.get();

        int backgroundThreads = 6;
        int iterationsPerThread = 100;
        int agitatorIterations = 50;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(backgroundThreads);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int t = 0; t < backgroundThreads; t++) {
            int threadIndex = t;
            Thread worker = new Thread(
                    () -> {
                        try {
                            start.await();
                            for (int i = 0; i < iterationsPerThread; i++) {
                                double pingValue = threadIndex * 100 + i;
                                graph.renderRoute(sampleRoute(), ip -> pingValue, List.of());
                            }
                        } catch (Throwable ex) {
                            errors.add(ex);
                        } finally {
                            done.countDown();
                        }
                    },
                    "hostile-off-fx-render-" + t);
            worker.setDaemon(true);
            worker.start();
        }
        // Concurrently drive real FX-thread-originated coalesced redraw requests (drag/layout
        // equivalent) while the background storm above is off-thread.
        Thread fxAgitator = new Thread(
                () -> {
                    try {
                        start.await();
                        for (int i = 0; i < agitatorIterations; i++) {
                            FxTestSupport.runOnFxThread(graph::requestRedrawForTest);
                        }
                    } catch (Throwable ex) {
                        errors.add(ex);
                    }
                },
                "hostile-fx-agitator");
        fxAgitator.setDaemon(true);
        fxAgitator.start();

        start.countDown();
        assertTrue(done.await(15, TimeUnit.SECONDS), "background renderRoute storm must finish within bound");
        fxAgitator.join(15000);
        assertFalse(fxAgitator.isAlive(), "FX agitator must not hang");
        assertTrue(errors.isEmpty(), "concurrent off-thread renderRoute + FX redraw must not throw: " + errors);

        // Drain a few pulses so any straggler coalesced paint gets a chance to run.
        for (int i = 0; i < 5; i++) {
            awaitOneFxPulse();
        }

        int totalRequests = backgroundThreads * iterationsPerThread + agitatorIterations;
        FxTestSupport.runOnFxThread(() -> {
            assertTrue(graph.paintCount() >= 1, "at least one paint must land after the contention storm");
            assertTrue(
                    graph.paintCount() < totalRequests,
                    "coalescing must still collapse " + totalRequests + " redraw requests to far fewer paints, got "
                            + graph.paintCount());
        });
    }

    private static MouseEvent pressEvent(GraphCanvas target, double x, double y) {
        return mouseEvent(MouseEvent.MOUSE_PRESSED, target, x, y);
    }

    private static MouseEvent dragEvent(GraphCanvas target, double x, double y) {
        return mouseEvent(MouseEvent.MOUSE_DRAGGED, target, x, y);
    }

    private static MouseEvent mouseEvent(EventType<MouseEvent> type, GraphCanvas target, double x, double y) {
        return new MouseEvent(
                type,
                x,
                y,
                x,
                y,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                new PickResult(target, x, y));
    }

    private static void awaitOneFxPulse() throws InterruptedException {
        // Awaited from the test thread (never from inside an FX-thread callback): the coalesced
        // pulse this waits for can only run once the FX Application Thread is free, so awaiting it
        // from that same thread would deadlock the whole suite.
        CountDownLatch pulse = new CountDownLatch(1);
        Platform.runLater(pulse::countDown);
        assertTrue(pulse.await(5, TimeUnit.SECONDS), "coalesced FX pulse timed out");
    }

    private static List<HopNode> sampleRoute() {
        return List.of(new HopNode(1, "8.8.8.8", 12.0, false));
    }
}
