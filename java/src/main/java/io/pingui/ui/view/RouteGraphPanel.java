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
    private final Label geoStrip = new Label();

    RouteGraphPanel() {
        graphCanvas.setMinSize(400, 280);
        VBox.setVgrow(graphCanvas, Priority.ALWAYS);
        geoStrip.getStyleClass().add("pingui-geo-strip");
        geoStrip.setWrapText(true);
        geoStrip.setManaged(false);
        geoStrip.setVisible(false);
        retranslate();
    }

    void installInto(VBox graphPanel) {
        graphPanel.getChildren().setAll(title, geoStrip, graphCanvas);
    }

    void retranslate() {
        title.setText(UiI18n.get("graph.title"));
    }

    /** Updates the geographic route strip ({@code LAN → UA/AS…}); hides when blank. */
    public void setGeoStrip(String text) {
        String value = text == null ? "" : text.strip();
        geoStrip.setText(value);
        boolean show = !value.isEmpty();
        geoStrip.setManaged(show);
        geoStrip.setVisible(show);
    }

    GraphCanvas graphCanvas() {
        return graphCanvas;
    }
}
