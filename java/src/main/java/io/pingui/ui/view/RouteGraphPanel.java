package io.pingui.ui.view;

import io.pingui.i18n.UiI18n;
import io.pingui.ui.GraphCanvas;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Route graph canvas chrome for the Extended right column. */
public final class RouteGraphPanel {
    private final GraphCanvas graphCanvas = new GraphCanvas();
    private final Label title = new Label();

    RouteGraphPanel() {
        graphCanvas.setMinSize(400, 280);
        VBox.setVgrow(graphCanvas, Priority.ALWAYS);
        retranslate();
    }

    void installInto(VBox graphPanel) {
        graphPanel.getChildren().setAll(title, graphCanvas);
    }

    void retranslate() {
        title.setText(UiI18n.get("graph.title"));
    }

    GraphCanvas graphCanvas() {
        return graphCanvas;
    }
}
