package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.pingui.model.Models.HopNode;
import java.util.List;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

class GraphCanvasTest {
    @Test
    void invalidateStrategyIsStep1ClearRect() {
        assertEquals("step1-clearRect-no-buffer-churn", GraphCanvas.INVALIDATE_STRATEGY);
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
    void sourceHasNoWidthPlusOneHack() throws Exception {
        // Guard against reintroducing the old Prism toggle: setWidth(width + 1.0).
        String src =
                java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/io/pingui/ui/GraphCanvas.java"));
        assertFalse(src.contains("setWidth(width + 1"), "buffer toggle setWidth(width + 1…) must stay removed");
        assertFalse(src.contains("setWidth(width + 1.0)"));
    }

    private static List<HopNode> sampleRoute() {
        return List.of(new HopNode(1, "8.8.8.8", 12.0, false));
    }
}
