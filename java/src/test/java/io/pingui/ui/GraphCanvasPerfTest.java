package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HopNode;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.event.EventType;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

/**
 * Soft perf smoke for P24-010 / G10: a large drag burst must stay within one coalesced paint
 * pulse. Wall-clock / JFR budgets stay in CHECKLIST (native desktop); CI asserts the coalesce
 * invariant only.
 */
class GraphCanvasPerfTest {
    private static final int DRAG_DELTAS = 100;

    @Test
    void hundredDragDeltasCoalesceToOnePaintPulse() throws Exception {
        AtomicReference<GraphCanvas> graphRef = new AtomicReference<>();
        FxTestSupport.runOnFxThread(() -> {
            GraphCanvas graph = new GraphCanvas();
            new Scene(new StackPane(graph), 400, 300);
            graph.resize(400, 300);
            graph.layout();
            graph.renderRoute(sampleRoute(), ip -> 1.0, List.of());
            graph.paintForTest();
            graphRef.set(graph);
        });

        // Scene attach / layoutChildren may leave a coalesced Platform.runLater pending;
        // paintForTest() does not clear that schedule. Drain before the drag baseline so a
        // leftover pulse cannot masquerade as drag-driven coalesce.
        awaitOneFxPulse();
        FxTestSupport.runOnFxThread(graphRef.get()::resetPaintCount);

        FxTestSupport.runOnFxThread(() -> {
            GraphCanvas graph = graphRef.get();
            graph.getOnMousePressed().handle(pressEvent(graph, 10, 10));
            for (int i = 1; i <= DRAG_DELTAS; i++) {
                graph.getOnMouseDragged().handle(dragEvent(graph, 10 + i, 10 + i));
            }
            assertEquals(0, graph.paintCount(), "drag burst must not paint before coalesced pulse");
        });

        awaitOneFxPulse();

        FxTestSupport.runOnFxThread(() -> {
            GraphCanvas graph = graphRef.get();
            int paints = graph.paintCount();
            // Soft budget: one FX pulse → at most one paint (strict invariant of requestRedraw).
            assertTrue(paints <= 1, "paint budget exceeded: paints=" + paints);
            assertEquals(1, paints, "100 drag deltas in one pulse must coalesce to exactly one paint");
            assertEquals(DRAG_DELTAS, graph.viewTransform().panX(), 0.001);
            assertEquals(DRAG_DELTAS, graph.viewTransform().panY(), 0.001);
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
        CountDownLatch pulse = new CountDownLatch(1);
        Platform.runLater(pulse::countDown);
        assertTrue(pulse.await(5, TimeUnit.SECONDS), "coalesced FX pulse timed out");
    }

    private static List<HopNode> sampleRoute() {
        return List.of(new HopNode(1, "10.0.0.1", 2.0, false), new HopNode(2, "8.8.8.8", 12.0, false));
    }
}
