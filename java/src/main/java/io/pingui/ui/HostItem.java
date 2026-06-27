package io.pingui.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/** Observable host row for the JavaFX list. */
public final class HostItem {
    private final StringProperty host = new SimpleStringProperty();
    private final BooleanProperty enabled = new SimpleBooleanProperty(false);

    public HostItem(String host, boolean enabled) {
        this.host.set(host);
        this.enabled.set(enabled);
    }

    public StringProperty hostProperty() {
        return host;
    }

    public BooleanProperty enabledProperty() {
        return enabled;
    }

    public String getHost() {
        return host.get();
    }

    public boolean isEnabled() {
        return enabled.get();
    }
}
