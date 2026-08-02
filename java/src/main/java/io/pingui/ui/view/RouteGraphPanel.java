package io.pingui.ui.view;

import io.pingui.ui.GraphCanvas;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Route graph canvas + injected route-diff chrome. */
public final class RouteGraphPanel {
    private final GraphCanvas graphCanvas = new GraphCanvas();
    private final Label title = new Label("Граф маршруту");

    RouteGraphPanel() {
        graphCanvas.setMinSize(400, 280);
        VBox.setVgrow(graphCanvas, Priority.ALWAYS);
    }

    void installInto(VBox graphPanel, Node routeDiffPanel) {
        graphPanel.getChildren().setAll(title, graphCanvas, routeDiffPanel);
    }

    GraphCanvas graphCanvas() {
        return graphCanvas;
    }
}
