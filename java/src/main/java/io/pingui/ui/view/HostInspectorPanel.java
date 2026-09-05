package io.pingui.ui.view;

import io.pingui.i18n.UiI18n;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Compact selected-host inspector chrome under the route graph (P31-003). */
public final class HostInspectorPanel {
    private final Label title = new Label();
    private final Label addressLine = new Label();
    private final Label modeLine = new Label();
    private final Label lastPollLine = new Label();
    private final Label metricsLine = new Label();
    private final Label endpointLine = new Label();
    private final Label routeLine = new Label();
    private final Label routeChangeLine = new Label();
    private final Label problemLine = new Label();
    private final Button copyButton = new Button();
    private final Button ackButton = new Button();
    private final Button diagnosticsButton = new Button();
    private final FlowPane actions = new FlowPane(8, 4);
    private final VBox root = new VBox(4);

    HostInspectorPanel() {
        title.getStyleClass().add("pingui-inspector-title");
        for (Label line : new Label[] {
            addressLine, modeLine, lastPollLine, metricsLine, endpointLine, routeLine, routeChangeLine, problemLine
        }) {
            line.getStyleClass().add("pingui-inspector-line");
            line.setWrapText(true);
            line.setMaxWidth(Double.MAX_VALUE);
        }
        problemLine.getStyleClass().add("pingui-muted");
        actions.getChildren().addAll(copyButton, ackButton, diagnosticsButton);
        root.getStyleClass().add("pingui-inspector");
        root.setPadding(new Insets(4, 0, 4, 0));
        HBox header = new HBox(8, title, new Region());
        HBox.setHgrow(header.getChildren().get(1), Priority.ALWAYS);
        root.getChildren()
                .addAll(
                        header,
                        addressLine,
                        modeLine,
                        lastPollLine,
                        metricsLine,
                        endpointLine,
                        routeLine,
                        routeChangeLine,
                        problemLine,
                        actions);
        showEmpty();
        retranslate();
    }

    void installInto(VBox graphPanel) {
        graphPanel.getChildren().add(root);
    }

    public void retranslate() {
        title.setText(UiI18n.get("inspector.title"));
        copyButton.setText(UiI18n.get("inspector.copy"));
        ackButton.setText(UiI18n.get("inspector.ack"));
        diagnosticsButton.setText(UiI18n.get("inspector.diagnostics"));
        root.setAccessibleText(UiI18n.get("a11y.inspector"));
        copyButton.setAccessibleText(UiI18n.get("inspector.copy"));
        ackButton.setAccessibleText(UiI18n.get("inspector.ack"));
        diagnosticsButton.setAccessibleText(UiI18n.get("inspector.diagnostics"));
    }

    public void showEmpty() {
        addressLine.setText(UiI18n.get("inspector.empty"));
        modeLine.setText("");
        lastPollLine.setText("");
        metricsLine.setText("");
        endpointLine.setText("");
        routeLine.setText("");
        routeChangeLine.setText("");
        problemLine.setText("");
        ackButton.setDisable(true);
        diagnosticsButton.setDisable(true);
        copyButton.setDisable(true);
    }

    public void apply(
            String address,
            String resolvedIp,
            String mode,
            String lastPoll,
            String rtt,
            String jitter,
            String loss,
            String endpoint,
            String route,
            String lastRouteChange,
            String problem,
            boolean canAck,
            boolean canDiagnose) {
        addressLine.setText(UiI18n.get("inspector.address", address, resolvedIp));
        modeLine.setText(UiI18n.get("inspector.mode", mode));
        lastPollLine.setText(UiI18n.get("inspector.last_poll", lastPoll));
        metricsLine.setText(UiI18n.get("inspector.metrics", rtt, jitter, loss));
        endpointLine.setText(UiI18n.get("inspector.endpoint", endpoint));
        routeLine.setText(UiI18n.get("inspector.route", route));
        routeChangeLine.setText(UiI18n.get("inspector.route_change", lastRouteChange));
        problemLine.setText(UiI18n.get("inspector.problem", problem));
        copyButton.setDisable(false);
        ackButton.setDisable(!canAck);
        diagnosticsButton.setDisable(!canDiagnose);
    }

    public Button copyButton() {
        return copyButton;
    }

    public Button ackButton() {
        return ackButton;
    }

    public Button diagnosticsButton() {
        return diagnosticsButton;
    }

    public VBox root() {
        return root;
    }
}
