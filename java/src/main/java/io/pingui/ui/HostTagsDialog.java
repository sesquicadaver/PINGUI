package io.pingui.ui;

import io.pingui.config.ConfigError;
import io.pingui.config.HostTags;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** Dialog to edit per-host tags (comma-separated); persists via session Save to YAML. */
public final class HostTagsDialog {
    private HostTagsDialog() {}

    public static Optional<List<String>> show(String host, List<String> current) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Теги — " + host);
        dialog.setHeaderText("Теги через кому (a-z, 0-9, '.', '_', '-'; макс. " + HostTags.MAX_TAGS_PER_HOST + ")");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField field = new TextField(current == null || current.isEmpty() ? "" : String.join(", ", current));
        field.setPromptText("dc, vpn, customer-x");
        Label error = new Label();
        error.setStyle("-fx-text-fill: #c0392b;");
        error.setWrapText(true);

        VBox box = new VBox(8, new Label("Теги:"), field, error);
        box.setPadding(new Insets(8, 0, 0, 0));
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().setPrefWidth(420);

        dialog.setResultConverter(button -> button);
        var okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                parseInput(field.getText());
                error.setText("");
            } catch (ConfigError ex) {
                error.setText(ex.getMessage());
                event.consume();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return Optional.empty();
        }
        return Optional.of(parseInput(field.getText()));
    }

    /** Splits on commas; blank tokens dropped; normalized via {@link HostTags}. */
    static List<String> parseInput(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String part : Arrays.asList(raw.split(","))) {
            String token = part.strip();
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return HostTags.normalize(tokens);
    }
}
