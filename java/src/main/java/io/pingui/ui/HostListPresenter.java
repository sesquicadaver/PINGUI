package io.pingui.ui;

import io.pingui.config.ConfigError;
import io.pingui.config.HostEntry;
import io.pingui.config.HostTags;
import io.pingui.config.HostsConfig;
import io.pingui.config.PingExpertEntry;
import io.pingui.monitor.HostProbeMode;
import io.pingui.monitor.HostProblemSummary;
import io.pingui.monitor.HostTargetStats;
import io.pingui.monitor.MonitorService;
import io.pingui.monitor.SessionStore;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Window;

/** Host list CRUD, tag filter chips, toggles, and row metrics in the main window. */
final class HostListPresenter {
    private static final double HOST_ROW_HEIGHT = 56.0;
    private static final double HOST_LIST_INSET = 4.0;
    static final String TAG_FILTER_ALL = "Усі";

    private final ObservableList<HostItem> hostItems;
    private final FilteredList<HostItem> filteredHosts;
    private final ListView<HostItem> hostList;
    private final TextField hostInput;
    private final Supplier<SessionStore> store;
    private final Supplier<MonitorService> monitor;
    private final SimpleBooleanProperty expertMode;
    private final UserFeedback userFeedback;
    private final Runnable syncControls;
    private final Runnable redrawRoute;
    private final Runnable clearHistoryReplay;
    private final java.util.function.BiConsumer<String, String> onHostRenamed;
    private final Runnable startEasterEgg;
    private final Runnable fitWindow;
    private final Consumer<Runnable> runWithoutHistoryFilterSync;
    private final FlowPane tagChipPane = new FlowPane(6, 6);
    private final ToggleGroup tagFilterGroup = new ToggleGroup();
    private final HBox tagFilterBar = new HBox(8);
    private String activeFilterTag;
    private boolean updatingList;
    private boolean refreshingChips;
    private BiFunction<String, List<String>, Optional<List<String>>> tagsEditor = HostTagsDialog::show;
    private Function<String, Boolean> confirmDeleteHost = this::confirmDeleteHostDialog;
    private Runnable markDirty = () -> {};

    HostListPresenter(
            ObservableList<HostItem> hostItems,
            ListView<HostItem> hostList,
            TextField hostInput,
            Supplier<SessionStore> store,
            Supplier<MonitorService> monitor,
            SimpleBooleanProperty expertMode,
            UserFeedback userFeedback,
            Runnable syncControls,
            Runnable redrawRoute,
            Runnable clearHistoryReplay,
            java.util.function.BiConsumer<String, String> onHostRenamed,
            Runnable startEasterEgg,
            Runnable fitWindow,
            Consumer<Runnable> runWithoutHistoryFilterSync) {
        this.hostItems = hostItems;
        this.filteredHosts = new FilteredList<>(hostItems, item -> true);
        this.hostList = hostList;
        this.hostInput = hostInput;
        this.store = store;
        this.monitor = monitor;
        this.expertMode = expertMode;
        this.userFeedback = userFeedback;
        this.syncControls = syncControls;
        this.redrawRoute = redrawRoute;
        this.clearHistoryReplay = clearHistoryReplay;
        this.onHostRenamed = onHostRenamed;
        this.startEasterEgg = startEasterEgg;
        this.fitWindow = fitWindow;
        this.runWithoutHistoryFilterSync = runWithoutHistoryFilterSync;
        Label tagLabel = new Label("Тег:");
        tagChipPane.setPadding(new Insets(2, 0, 2, 0));
        HBox.setHgrow(tagChipPane, Priority.ALWAYS);
        tagFilterBar.getChildren().addAll(tagLabel, tagChipPane);
        tagFilterGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (refreshingChips) {
                return;
            }
            if (newToggle == null) {
                // Keep one chip selected (re-select previous or «Усі»).
                Toggle restore = oldToggle != null ? oldToggle : findAllToggle();
                refreshingChips = true;
                tagFilterGroup.selectToggle(restore);
                refreshingChips = false;
                return;
            }
            Object data = newToggle.getUserData();
            activeFilterTag = data instanceof String tag && !tag.isBlank() ? tag : null;
            applyTagFilter();
        });
    }

    Node tagFilterBar() {
        return tagFilterBar;
    }

    void configure() {
        hostList.setItems(filteredHosts);
        hostList.setFixedCellSize(HOST_ROW_HEIGHT);
        hostList.setMaxHeight(listHeightForRows(HostsConfig.MAX_HOSTS));
        hostItems.addListener((ListChangeListener.Change<? extends HostItem> change) -> {
            syncListHeight();
            refreshTagChips();
        });
        syncListHeight();
        refreshTagChips();
        hostList.setCellFactory(list -> new HostListCell(
                this::onToggleEnabled,
                this::onTogglePingOnly,
                expertMode,
                this::onOpenExpertPing,
                this::onOpenMtuWizard,
                this::onOpenProblem));
    }

    void rebuild(List<HostEntry> entries) {
        hostItems.clear();
        for (HostEntry entry : HostViewRules.sessionEntries(entries)) {
            HostItem item = new HostItem(entry.address(), entry.enabled(), entry.pingOnly(), entry.tags());
            item.setExpertConfigured(entry.pingExpert().isConfigured());
            hostItems.add(item);
        }
    }

    void refreshTagChips() {
        String previous = activeFilterTag;
        List<String> tags =
                HostTags.collectUnique(hostItems.stream().map(HostItem::getTags).toList());
        refreshingChips = true;
        for (Toggle toggle : List.copyOf(tagFilterGroup.getToggles())) {
            toggle.setToggleGroup(null);
        }
        tagChipPane.getChildren().clear();
        ToggleButton all = chipButton(TAG_FILTER_ALL, null);
        tagChipPane.getChildren().add(all);
        ToggleButton selected = all;
        for (String tag : tags) {
            ToggleButton chip = chipButton(tag, tag);
            tagChipPane.getChildren().add(chip);
            if (tag.equals(previous)) {
                selected = chip;
            }
        }
        tagFilterGroup.selectToggle(selected);
        Object data = selected.getUserData();
        activeFilterTag = data instanceof String tag && !tag.isBlank() ? tag : null;
        refreshingChips = false;
        applyTagFilter();
    }

    /** Clears tag filter when history selects a host hidden by the current chip. */
    void ensureHostVisibleForTagFilter(String host) {
        if (host == null || host.isBlank()) {
            return;
        }
        HostItem item = HistoryHostSync.findItem(hostItems, host);
        if (item == null) {
            return;
        }
        if (!item.hasTag(activeFilterTag)) {
            selectAllChip();
        }
    }

    /** Package-visible for tests: inject dialog without JavaFX modality. */
    void setTagsEditor(BiFunction<String, List<String>, Optional<List<String>>> tagsEditor) {
        this.tagsEditor = tagsEditor != null ? tagsEditor : HostTagsDialog::show;
    }

    /** Package-visible for tests: inject delete confirmation without modal Alert. */
    void setConfirmDeleteHost(Function<String, Boolean> confirmDeleteHost) {
        this.confirmDeleteHost = confirmDeleteHost != null ? confirmDeleteHost : this::confirmDeleteHostDialog;
    }

    /** Package-visible for tests / wiring: mark YAML dirty after config mutations. */
    void setMarkDirty(Runnable markDirty) {
        this.markDirty = markDirty != null ? markDirty : () -> {};
    }

    String activeFilterTag() {
        return activeFilterTag;
    }

    int visibleHostCount() {
        return filteredHosts.size();
    }

    void selectFilterChip(String tagOrNull) {
        for (Toggle toggle : tagFilterGroup.getToggles()) {
            Object data = toggle.getUserData();
            if (tagOrNull == null && data == null) {
                tagFilterGroup.selectToggle(toggle);
                return;
            }
            if (tagOrNull != null && tagOrNull.equals(data)) {
                tagFilterGroup.selectToggle(toggle);
                return;
            }
        }
    }

    void editSelectedHostTags() {
        HostItem selected = hostList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            userFeedback.error("Оберіть ціль, щоб змінити теги");
            return;
        }
        Optional<List<String>> updated = tagsEditor.apply(selected.getHost(), selected.getTags());
        if (updated.isEmpty()) {
            return;
        }
        try {
            String host = selected.getHost();
            store.get().setTags(host, updated.get());
            selected.setTags(updated.get());
            refreshTagChips();
            HostItem item = findItem(host);
            if (item != null) {
                if (!item.hasTag(activeFilterTag)) {
                    selectAllChip();
                }
                hostList.getSelectionModel().select(item);
            }
            hostList.refresh();
            markDirty.run();
            userFeedback.info(
                    "Теги [" + host + "]: " + (updated.get().isEmpty() ? "(немає)" : String.join(", ", updated.get())));
        } catch (ConfigError ex) {
            userFeedback.error(ex.getMessage());
        }
    }

    void syncMetrics(HostItem item) {
        if (!item.isEnabled()) {
            item.clearMetrics();
            return;
        }
        HostTargetStats stats = store.get().targetStats(item.getHost());
        if (stats == null) {
            item.clearMetrics();
            return;
        }
        item.applyMetrics(stats);
    }

    /** Syncs unread endpoint_down badge from {@link MonitorService} (P22-004). */
    void syncProblem(HostItem item) {
        MonitorService service = monitor.get();
        if (service == null) {
            item.clearProblem();
            return;
        }
        item.applyProblem(service.hostProblemSummary(item.getHost()).orElse(null));
    }

    HostItem findItem(String host) {
        for (HostItem item : hostItems) {
            if (host.equals(item.getHost())) {
                return item;
            }
        }
        return null;
    }

    void addHost() {
        String raw = hostInput.getText().strip();
        if (raw.isBlank()) {
            return;
        }
        if (HostViewRules.matches(raw)) {
            startEasterEgg.run();
            return;
        }
        try {
            SessionStore session = store.get();
            String host = HostsConfig.validateSessionHost(raw, session.hosts());
            monitor.get().addHost(host, false, false);
            session.addHost(host, false, false, PingExpertEntry.empty());
            HostItem item = new HostItem(host, false);
            hostItems.add(item);
            if (!item.hasTag(activeFilterTag)) {
                selectAllChip();
            }
            selectHostWithoutHistoryFilterSync(item);
            hostInput.clear();
            markDirty.run();
            userFeedback.info("Додано ціль: " + host);
            syncControls.run();
            redrawRoute.run();
        } catch (ConfigError ex) {
            userFeedback.error("Не вдалося додати ціль: " + ex.getMessage());
        }
    }

    void editHost() {
        HostItem selected = hostList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        String oldHost = selected.getHost();
        String newText = hostInput.getText().strip();
        if (newText.isBlank() || newText.equals(oldHost)) {
            return;
        }
        if (HostViewRules.matches(newText)) {
            startEasterEgg.run();
            return;
        }
        try {
            SessionStore session = store.get();
            List<String> others =
                    session.hosts().stream().filter(h -> !h.equals(oldHost)).toList();
            String renamed = HostsConfig.validateSessionHost(newText, others);
            monitor.get().renameHost(oldHost, renamed);
            session.renameHost(oldHost, renamed);
            selected.hostProperty().set(renamed);
            hostInput.setText(renamed);
            onHostRenamed.accept(oldHost, renamed);
            markDirty.run();
            userFeedback.info("Змінено ціль: " + oldHost + " → " + renamed);
            clearHistoryReplay.run();
            redrawRoute.run();
        } catch (ConfigError ex) {
            userFeedback.error("Не вдалося змінити ціль: " + ex.getMessage());
        }
    }

    void removeHost() {
        HostItem selected = hostList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        String host = selected.getHost();
        if (!Boolean.TRUE.equals(confirmDeleteHost.apply(host))) {
            return;
        }
        try {
            monitor.get().removeHost(host);
            store.get().removeHost(host);
            hostItems.remove(selected);
            hostInput.clear();
            markDirty.run();
            userFeedback.info("Видалено ціль: " + host);
            syncControls.run();
            redrawRoute.run();
        } catch (ConfigError ex) {
            userFeedback.error(ex.getMessage());
        }
    }

    private boolean confirmDeleteHostDialog(String host) {
        Window owner = hostList.getScene() != null ? hostList.getScene().getWindow() : null;
        return ConfirmDialogs.confirm(
                owner,
                "Видалити ціль",
                "Видалити «" + host + "» зі списку?",
                "Ціль зникне з поточної сесії. Збережіть конфіг, щоб зміна потрапила у YAML.");
    }

    void syncInputLimits() {
        boolean canAdd = store.get().canAddHost();
        hostInput.setDisable(!canAdd);
        if (!canAdd) {
            hostInput.setPromptText("Досягнуто ліміт 10 цілей у списку");
        } else {
            hostInput.setPromptText("IPv4, IPv6 literal або hostname…");
        }
    }

    private void applyTagFilter() {
        filteredHosts.setPredicate(item -> item.hasTag(activeFilterTag));
    }

    private void selectAllChip() {
        Toggle all = findAllToggle();
        if (all != null) {
            tagFilterGroup.selectToggle(all);
        } else {
            activeFilterTag = null;
            applyTagFilter();
        }
    }

    private Toggle findAllToggle() {
        for (Toggle toggle : tagFilterGroup.getToggles()) {
            if (toggle.getUserData() == null) {
                return toggle;
            }
        }
        return null;
    }

    private ToggleButton chipButton(String label, String tag) {
        ToggleButton button = new ToggleButton(label);
        button.setUserData(tag);
        button.setToggleGroup(tagFilterGroup);
        button.setFocusTraversable(false);
        button.setStyle("-fx-font-size: 11px;");
        return button;
    }

    private void syncListHeight() {
        int rows = Math.max(1, hostItems.size());
        hostList.setPrefHeight(listHeightForRows(Math.min(rows, HostsConfig.MAX_HOSTS)));
        fitWindow.run();
    }

    private static double listHeightForRows(int rows) {
        return rows * HOST_ROW_HEIGHT + HOST_LIST_INSET;
    }

    /** Keeps route-history host filter when a newly added row is auto-selected. */
    private void selectHostWithoutHistoryFilterSync(HostItem item) {
        Runnable select = () -> hostList.getSelectionModel().select(item);
        if (runWithoutHistoryFilterSync != null) {
            runWithoutHistoryFilterSync.accept(select);
        } else {
            select.run();
        }
    }

    private void onToggleEnabled(HostItem item, boolean enabled) {
        try {
            monitor.get().setHostEnabled(item.getHost(), enabled);
            store.get().setEnabled(item.getHost(), enabled);
            updatingList = true;
            item.enabledProperty().set(enabled);
            updatingList = false;
            syncMetrics(item);
            clearHistoryReplay.run();
            redrawRoute.run();
            markDirty.run();
        } catch (ConfigError ex) {
            userFeedback.error(ex.getMessage());
            updatingList = true;
            item.enabledProperty().set(store.get().get(item.getHost()).isEnabled());
            updatingList = false;
            syncMetrics(item);
        }
    }

    private void onTogglePingOnly(HostItem item, boolean pingOnly) {
        try {
            SessionStore session = store.get();
            HostProbeMode mode = pingOnly ? HostProbeMode.PING_ONLY : HostProbeMode.TRACE;
            // Session first: resolver (store::getProbeMode) matches intended mode before monitor flips.
            session.setProbeMode(item.getHost(), mode);
            monitor.get().setHostProbeMode(item.getHost(), mode);
            if (pingOnly) {
                PingExpertEntry expert = session.getPingExpert(item.getHost());
                if (expert.applyToChain()) {
                    session.setPingExpert(item.getHost(), new PingExpertEntry(false, expert.args()));
                }
            }
            updatingList = true;
            item.pingOnlyProperty().set(pingOnly);
            updatingList = false;
            syncMetrics(item);
            hostList.refresh();
            clearHistoryReplay.run();
            redrawRoute.run();
            markDirty.run();
            userFeedback.info("Ping only [" + item.getHost() + "]: " + (pingOnly ? "увімкнено" : "вимкнено"));
        } catch (ConfigError ex) {
            userFeedback.error(ex.getMessage());
            updatingList = true;
            item.pingOnlyProperty().set(store.get().isPingOnly(item.getHost()));
            updatingList = false;
            syncMetrics(item);
        }
    }

    private void onOpenExpertPing(HostItem item, Void ignored) {
        SessionStore session = store.get();
        PingExpertEntry current = session.getPingExpert(item.getHost());
        Optional<PingExpertEntry> updated = PingExpertDialog.show(item.getHost(), current, item.isPingOnly());
        if (updated.isEmpty()) {
            return;
        }
        try {
            session.setPingExpert(item.getHost(), updated.get());
            item.setExpertConfigured(updated.get().isConfigured());
            markDirty.run();
            userFeedback.info("Expert ping [" + item.getHost() + "]: "
                    + (updated.get().isConfigured() ? updated.get().args() : "скинуто"));
        } catch (ConfigError ex) {
            userFeedback.error(ex.getMessage());
        }
    }

    private void onOpenMtuWizard(HostItem item, Void ignored) {
        SessionStore session = store.get();
        PingExpertEntry current = session.getPingExpert(item.getHost());
        boolean ipv6 = MtuDiscoveryDialog.ipv6FromExpertArgs(current.args());
        Window owner = hostList.getScene() != null ? hostList.getScene().getWindow() : null;
        MtuDiscoveryDialog.show(owner, item.getHost(), ipv6, current.args(), result -> {
            try {
                boolean applyToChain = !item.isPingOnly() && current.applyToChain();
                PingExpertEntry next = new PingExpertEntry(applyToChain, result.expertArgs());
                session.setPingExpert(item.getHost(), next);
                item.setExpertConfigured(next.isConfigured());
                hostList.refresh();
                String mtu = result.discovery().recommendedMtu().isPresent()
                        ? Integer.toString(result.discovery().recommendedMtu().getAsInt())
                        : "?";
                markDirty.run();
                userFeedback.info("MTU wizard [" + item.getHost() + "]: MTU≈" + mtu + " → " + next.args());
            } catch (ConfigError ex) {
                userFeedback.error(ex.getMessage());
            }
        });
    }

    private void onOpenProblem(HostItem item, Void ignored) {
        HostProblemSummary summary = item.problemSummary();
        if (summary == null || !summary.showBadge()) {
            return;
        }
        Window owner = hostList.getScene() != null ? hostList.getScene().getWindow() : null;
        ProblemDetailsDialog.show(owner, summary);
        MonitorService service = monitor.get();
        if (service != null) {
            service.ackHostProblem(item.getHost());
            item.applyProblem(service.hostProblemSummary(item.getHost()).orElse(null));
        } else {
            item.clearProblem();
        }
        hostList.refresh();
    }
}
