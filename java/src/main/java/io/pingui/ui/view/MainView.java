package io.pingui.ui.view;

import io.pingui.i18n.UiI18n;
import io.pingui.i18n.UiLocale;
import io.pingui.ui.AppAccelerators;
import io.pingui.ui.GraphCanvas;
import io.pingui.ui.HostItem;
import io.pingui.ui.RouteHistoryItem;
import io.pingui.ui.UiPalette;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Owns main-window JavaFX nodes and assembles chrome. Cross-coordinator listeners stay in {@code
 * MainController}.
 */
public final class MainView {
    private final ProfileToolbar profileToolbar = new ProfileToolbar();
    private final MonitorModeToolbar modeToolbar = new MonitorModeToolbar();
    private final HostListPanel hostListPanel = new HostListPanel();
    private final StatusPanel statusPanel = new StatusPanel();
    private final RouteGraphPanel routeGraphPanel = new RouteGraphPanel();
    private final HostInspectorPanel hostInspectorPanel = new HostInspectorPanel();
    private final HistoryPanel historyPanel = new HistoryPanel();
    private final VBox leftPanel = new VBox(8);
    private final VBox graphPanel = new VBox(8);
    private final SplitPane mainSplit = new SplitPane();
    private final BorderPane root = new BorderPane();
    private MainViewActions actions;

    public MainView() {
        root.getStyleClass().add("pingui-root");
        leftPanel.getStyleClass().add("pingui-panel");
        graphPanel.getStyleClass().add("pingui-panel");
        mainSplit.getStyleClass().add("pingui-split");
        leftPanel.setMinWidth(HostListPanel.PANEL_MIN_WIDTH);
        // Padding/spacing come from .pingui-panel CSS (UiPalette.SPACE_SM); keep min width in code.
        leftPanel.setPadding(new Insets(UiPalette.SPACE_SM));
        graphPanel.setPadding(new Insets(UiPalette.SPACE_SM));
    }

    /**
     * Wires chrome actions and installs children into left/graph panels. Does not attach
     * cross-coordinator listeners.
     *
     * @param actions orchestration callbacks
     * @param navigationChrome host list filter/sort chrome from {@code HostListPresenter}
     * @return root border pane (menu already set as top)
     */
    public BorderPane assemble(MainViewActions actions, Node navigationChrome) {
        this.actions = actions;
        profileToolbar.wire(actions);
        hostListPanel.wire(actions);
        historyPanel.wire(actions);

        profileToolbar.bar().getStyleClass().add("pingui-toolbar");
        modeToolbar.bar().getStyleClass().add("pingui-toolbar");
        hostListPanel.hostList().getStyleClass().add("pingui-host-list");
        statusPanel.monitoringLabel().getStyleClass().add("pingui-status");
        statusPanel.logArea().getStyleClass().add("pingui-log");
        if (navigationChrome != null) {
            navigationChrome.getStyleClass().add("pingui-toolbar");
        }

        java.util.ArrayList<Node> leftChildren = new java.util.ArrayList<>();
        leftChildren.add(profileToolbar.bar());
        leftChildren.add(modeToolbar.bar());
        for (Node node : hostListPanel.chromeWithNavigation(navigationChrome)) {
            leftChildren.add(node);
        }
        leftChildren.add(statusPanel.chrome());
        leftPanel.getChildren().setAll(leftChildren);

        routeGraphPanel.installInto(graphPanel);
        hostInspectorPanel.installInto(graphPanel);
        historyPanel.installInto(graphPanel);

        root.setTop(createMenuBar(actions));
        return root;
    }

    private MenuBar createMenuBar(MainViewActions actions) {
        MenuItem saveItem = new MenuItem(UiI18n.get("menu.save"));
        saveItem.setAccelerator(KeyCombination.valueOf(AppAccelerators.SAVE));
        saveItem.setOnAction(e -> actions.onSaveConfig());
        MenuItem addHostItem = new MenuItem(UiI18n.get("menu.add_host"));
        addHostItem.setAccelerator(KeyCombination.valueOf(AppAccelerators.ADD_HOST));
        addHostItem.setOnAction(e -> actions.onAddHost());
        Menu fileMenu = new Menu(UiI18n.get("menu.file"));
        fileMenu.getItems().addAll(saveItem, addHostItem);

        MenuItem aboutItem = new MenuItem(UiI18n.get("menu.about_item"));
        aboutItem.setOnAction(e -> actions.onAbout());
        Menu aboutMenu = new Menu(UiI18n.get("menu.about"));
        aboutMenu.getItems().add(aboutItem);

        MenuItem helpItem = new MenuItem(UiI18n.get("menu.help_item"));
        helpItem.setAccelerator(KeyCombination.valueOf(AppAccelerators.HELP));
        helpItem.setOnAction(e -> actions.onHelp());
        Menu helpMenu = new Menu(UiI18n.get("menu.help"));
        helpMenu.getItems().add(helpItem);

        MenuItem databaseItem = new MenuItem(UiI18n.get("menu.database"));
        databaseItem.setOnAction(e -> actions.onPersistenceSettings());
        MenuItem profileParamsItem = new MenuItem(UiI18n.get("menu.profile"));
        profileParamsItem.setOnAction(e -> actions.onProfileParamsSettings());
        MenuItem alertsItem = new MenuItem(UiI18n.get("menu.alerts"));
        alertsItem.setOnAction(e -> actions.onAlertsSettings());
        MenuItem telemetryItem = new MenuItem(UiI18n.get("menu.telemetry"));
        telemetryItem.setOnAction(e -> actions.onTelemetrySettings());
        MenuItem exportItem = new MenuItem(UiI18n.get("menu.export_now"));
        exportItem.setOnAction(e -> actions.onExportNow());
        Menu settingsMenu = new Menu(UiI18n.get("menu.settings"));
        settingsMenu.getItems().addAll(databaseItem, profileParamsItem, alertsItem, telemetryItem, exportItem);

        Menu languageMenu = new Menu(UiI18n.get("menu.language"));
        ToggleGroup langGroup = new ToggleGroup();
        for (UiLocale locale : UiLocale.values()) {
            RadioMenuItem item = new RadioMenuItem(locale.displayName());
            item.setToggleGroup(langGroup);
            item.setUserData(locale);
            item.setSelected(locale == UiI18n.locale());
            item.setOnAction(e -> actions.onLanguageSelected(locale));
            languageMenu.getItems().add(item);
        }

        MenuBar menuBar = new MenuBar(fileMenu, aboutMenu, settingsMenu, languageMenu, helpMenu);
        menuBar.setUseSystemMenuBar(true);
        return menuBar;
    }

    /** Refresh chrome labels after {@link UiI18n#setLocale} (P25). */
    public void retranslateChrome() {
        profileToolbar.retranslate();
        modeToolbar.retranslate();
        hostListPanel.retranslate();
        routeGraphPanel.retranslate();
        hostInspectorPanel.retranslate();
        historyPanel.retranslate();
        if (actions != null) {
            root.setTop(createMenuBar(actions));
        }
        hostList().refresh();
    }

    public BorderPane root() {
        return root;
    }

    public SplitPane mainSplit() {
        return mainSplit;
    }

    public VBox leftPanel() {
        return leftPanel;
    }

    public VBox graphPanel() {
        return graphPanel;
    }

    public ProfileToolbar profileToolbar() {
        return profileToolbar;
    }

    public MonitorModeToolbar monitorModeToolbar() {
        return modeToolbar;
    }

    public HostListPanel hostListPanel() {
        return hostListPanel;
    }

    public StatusPanel statusPanel() {
        return statusPanel;
    }

    public RouteGraphPanel routeGraphPanel() {
        return routeGraphPanel;
    }

    public HostInspectorPanel hostInspectorPanel() {
        return hostInspectorPanel;
    }

    public HistoryPanel historyPanel() {
        return historyPanel;
    }

    public ListView<HostItem> hostList() {
        return hostListPanel.hostList();
    }

    public TextField hostInput() {
        return hostListPanel.hostInput();
    }

    public Button saveButton() {
        return hostListPanel.saveButton();
    }

    public ComboBox<String> profileCombo() {
        return profileToolbar.profileCombo();
    }

    public Label statusLabel() {
        return statusPanel.statusLabel();
    }

    public TextArea logArea() {
        return statusPanel.logArea();
    }

    public GraphCanvas graphCanvas() {
        return routeGraphPanel.graphCanvas();
    }

    public ListView<RouteHistoryItem> historyList() {
        return historyPanel.historyList();
    }

    public RadioButton historyRange24h() {
        return historyPanel.historyRange24h();
    }

    public RadioButton historyRange7d() {
        return historyPanel.historyRange7d();
    }

    public ComboBox<String> historyHostFilter() {
        return historyPanel.historyHostFilter();
    }

    public HBox historyFilterBar() {
        return historyPanel.historyFilterBar();
    }

    public HBox historyRangeBar() {
        return historyPanel.historyRangeBar();
    }

    public Label historyLabel() {
        return historyPanel.historyLabel();
    }

    public RadioButton simpleModeButton() {
        return modeToolbar.simpleToggle();
    }

    public RadioButton extendedModeButton() {
        return modeToolbar.extendedToggle();
    }

    public ToggleGroup modeGroup() {
        return modeToolbar.modeGroup();
    }
}
