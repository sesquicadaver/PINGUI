"""Main application window."""

from __future__ import annotations

import time
from datetime import UTC, datetime
from pathlib import Path

from PyQt6.QtCore import Qt
from PyQt6.QtGui import QCloseEvent, QKeySequence, QShortcut
from PyQt6.QtWidgets import (
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QMainWindow,
    QPushButton,
    QTextEdit,
    QVBoxLayout,
    QWidget,
)

from pingui.config import ConfigError, save_hosts_config
from pingui.models import RouteSnapshot
from pingui.monitor.session_store import SessionStore
from pingui.monitor.worker import LightweightMonitorWorker
from pingui.persistence.session_db import SessionDatabase
from pingui.ui.graph_canvas import GraphCanvas

HOST_KEY_ROLE = Qt.ItemDataRole.UserRole


class MainWindow(QMainWindow):
    """Primary GUI: host list, route-change log, topological graph."""

    def __init__(
        self,
        hosts: list[str],
        config_path: Path | str,
        interval_seconds: float = 1.0,
        max_hops: int = 20,
        timeout: float = 0.5,
        session_db_path: Path | None = None,
    ) -> None:
        super().__init__()
        self.setWindowTitle("PINGUI — Сесійний монітор маршрутів Linux")
        self.resize(1100, 700)
        self._config_path = Path(config_path)
        self._session_db = (
            SessionDatabase(session_db_path) if session_db_path is not None else None
        )

        self._store = SessionStore(hosts, session_db=self._session_db)
        self._last_update: datetime | None = None
        self._updating_list = False

        central = QWidget()
        self.setCentralWidget(central)
        root = QHBoxLayout(central)

        left = QVBoxLayout()
        self._host_list = QListWidget()
        self._host_list.itemChanged.connect(self._on_host_item_changed)
        self._host_list.currentItemChanged.connect(self._on_host_selected)
        for host in hosts:
            enabled = self._store.get(host).enabled
            self._append_host_item(host, enabled=enabled)

        self._host_input = QLineEdit()
        self._host_input.setPlaceholderText("IP або hostname…")

        host_btn_row = QHBoxLayout()
        self._add_button = QPushButton("Додати")
        self._edit_button = QPushButton("Змінити")
        self._remove_button = QPushButton("Видалити")
        self._save_button = QPushButton("Зберегти")
        self._add_button.clicked.connect(self._on_add_host)
        self._edit_button.clicked.connect(self._on_edit_host)
        self._remove_button.clicked.connect(self._on_remove_host)
        self._save_button.clicked.connect(self._on_save_hosts)
        host_btn_row.addWidget(self._add_button)
        host_btn_row.addWidget(self._edit_button)
        host_btn_row.addWidget(self._remove_button)
        host_btn_row.addWidget(self._save_button)
        QShortcut(QKeySequence(Qt.Key.Key_Return), self._host_input, self._on_add_host)
        QShortcut(QKeySequence(Qt.Key.Key_Enter), self._host_input, self._on_add_host)

        self._status_label = QLabel("Очікування даних…")
        self._status_label.setAlignment(Qt.AlignmentFlag.AlignLeft)

        self._log = QTextEdit()
        self._log.setReadOnly(True)
        self._log.setPlaceholderText("Логи змін маршрутів відображатимуться тут…")

        left.addWidget(self._host_list, stretch=1)
        left.addWidget(self._host_input)
        left.addLayout(host_btn_row)
        left.addWidget(self._status_label)
        left.addWidget(self._log, stretch=2)

        self._canvas = GraphCanvas()
        root.addLayout(left, stretch=1)
        root.addWidget(self._canvas, stretch=2)

        self._worker = LightweightMonitorWorker(
            hosts,
            interval_seconds=interval_seconds,
            max_hops=max_hops,
            timeout=timeout,
        )
        self._worker.data_received.connect(self._on_data_received)
        self._worker.route_changed.connect(self._on_route_changed)
        self._worker.probe_error.connect(self._on_probe_error)
        self._worker.start()
        for host in hosts:
            if self._store.get(host).enabled:
                self._worker.set_host_enabled(host, True)

        if self._host_list.count() > 0:
            self._host_list.setCurrentRow(0)

        self._sync_host_controls()

    def _append_host_item(self, host: str, *, enabled: bool) -> None:
        item = QListWidgetItem(host)
        flags = (
            item.flags()
            | Qt.ItemFlag.ItemIsUserCheckable
            | Qt.ItemFlag.ItemIsEditable
        )
        item.setFlags(flags)
        item.setData(HOST_KEY_ROLE, host)
        item.setCheckState(
            Qt.CheckState.Checked if enabled else Qt.CheckState.Unchecked
        )
        self._updating_list = True
        self._host_list.addItem(item)
        self._updating_list = False

    def _sync_host_controls(self) -> None:
        can_add = self._store.can_add_host()
        has_selection = self._host_list.currentItem() is not None
        self._host_input.setEnabled(True)
        self._add_button.setEnabled(can_add)
        self._edit_button.setEnabled(has_selection)
        self._remove_button.setEnabled(has_selection)
        self._save_button.setEnabled(True)
        if not can_add:
            self._host_input.setPlaceholderText("Досягнуто ліміт 10 цілей у списку")

    def _selected_item(self) -> QListWidgetItem | None:
        return self._host_list.currentItem()

    def _active_host(self) -> str | None:
        item = self._selected_item()
        if item is None:
            return None
        key = item.data(HOST_KEY_ROLE)
        return str(key) if key is not None else item.text()

    def _on_host_selected(
        self,
        current: QListWidgetItem | None,
        _previous: QListWidgetItem | None,
    ) -> None:
        if current is not None:
            key = current.data(HOST_KEY_ROLE)
            self._host_input.setText(str(key) if key is not None else current.text())
        self._sync_host_controls()
        self._redraw_active()

    def _rename_host_item(self, item: QListWidgetItem, old_key: str, new_text: str) -> bool:
        try:
            renamed = self._worker.rename_host(old_key, new_text)
            self._store.rename_host(old_key, renamed)
        except ConfigError as exc:
            ts = time.strftime("%H:%M:%S")
            self._log.append(f"[{ts}] {exc}\n")
            return False
        self._updating_list = True
        item.setData(HOST_KEY_ROLE, renamed)
        item.setText(renamed)
        self._host_input.setText(renamed)
        self._updating_list = False
        return True

    def _on_host_item_changed(self, item: QListWidgetItem) -> None:
        if self._updating_list:
            return
        host_key = item.data(HOST_KEY_ROLE)
        if host_key is None:
            return
        host_key = str(host_key)
        new_text = item.text().strip()

        if new_text and new_text != host_key:
            if not self._rename_host_item(item, host_key, new_text):
                self._updating_list = True
                item.setText(host_key)
                self._updating_list = False
                return
            host_key = str(item.data(HOST_KEY_ROLE))

        enabled = item.checkState() == Qt.CheckState.Checked
        if self._store.get(host_key).enabled == enabled:
            return
        try:
            self._worker.set_host_enabled(host_key, enabled)
            self._store.set_enabled(host_key, enabled)
        except ConfigError as exc:
            ts = time.strftime("%H:%M:%S")
            self._log.append(f"[{ts}] {exc}\n")
            self._updating_list = True
            item.setCheckState(
                Qt.CheckState.Checked
                if self._store.get(host_key).enabled
                else Qt.CheckState.Unchecked
            )
            self._updating_list = False

    def _on_add_host(self) -> None:
        raw = self._host_input.text()
        if not raw.strip():
            return
        try:
            host = self._worker.add_host(raw, enabled=False)
            self._store.add_host(host, enabled=False)
        except ConfigError as exc:
            ts = time.strftime("%H:%M:%S")
            self._log.append(f"[{ts}] Не вдалося додати ціль: {exc}\n")
            return

        self._append_host_item(host, enabled=False)
        self._host_list.setCurrentRow(self._host_list.count() - 1)
        self._host_input.clear()
        self._sync_host_controls()
        ts = time.strftime("%H:%M:%S")
        self._log.append(f"[{ts}] Додано ціль: {host}\n")
        self._redraw_active()

    def _on_edit_host(self) -> None:
        item = self._selected_item()
        if item is None:
            return
        old_key = item.data(HOST_KEY_ROLE)
        if old_key is None:
            return
        old_key = str(old_key)
        new_text = self._host_input.text().strip()
        if not new_text or new_text == old_key:
            return
        if self._rename_host_item(item, old_key, new_text):
            ts = time.strftime("%H:%M:%S")
            self._log.append(f"[{ts}] Змінено ціль: {old_key} → {new_text}\n")
            self._redraw_active()

    def _on_remove_host(self) -> None:
        item = self._selected_item()
        if item is None:
            return
        host = str(item.data(HOST_KEY_ROLE))
        try:
            self._worker.remove_host(host)
            self._store.remove_host(host)
        except ConfigError as exc:
            ts = time.strftime("%H:%M:%S")
            self._log.append(f"[{ts}] {exc}\n")
            return

        row = self._host_list.row(item)
        self._updating_list = True
        self._host_list.takeItem(row)
        self._updating_list = False
        self._host_input.clear()
        self._sync_host_controls()
        ts = time.strftime("%H:%M:%S")
        self._log.append(f"[{ts}] Видалено ціль: {host}\n")
        self._redraw_active()

    def _on_save_hosts(self) -> None:
        try:
            save_hosts_config(self._config_path, self._store.hosts())
        except (ConfigError, OSError) as exc:
            ts = time.strftime("%H:%M:%S")
            self._log.append(f"[{ts}] Не вдалося зберегти список: {exc}\n")
            return
        ts = time.strftime("%H:%M:%S")
        self._log.append(f"[{ts}] Список цілей збережено: {self._config_path}\n")

    def _on_data_received(self, host: str, snapshot_obj: object) -> None:
        if not isinstance(snapshot_obj, RouteSnapshot):
            return
        self._store.update_route(host, snapshot_obj)
        self._store.append_ping_samples(host, snapshot_obj)
        self._last_update = datetime.now(UTC)
        if self._active_host() == host:
            self._redraw_active()
            self._update_status(host)

    def _on_route_changed(self, host: str, old_ips: list[str], new_ips: list[str]) -> None:
        ts = time.strftime("%H:%M:%S")
        old_str = " -> ".join(old_ips) if old_ips else "Початок моніторингу"
        new_str = " -> ".join(new_ips)
        self._log.append(
            f"[{ts}] ⚠ ЗМІНА МАРШРУТУ до {host}:\n"
            f"Було: {old_str}\n"
            f"Стало: {new_str}\n"
        )

    def _on_probe_error(self, host: str, message: str) -> None:
        ts = time.strftime("%H:%M:%S")
        self._log.append(f"[{ts}] Помилка [{host}]: {message}\n")

    def _update_status(self, host: str) -> None:
        if self._last_update is None:
            return
        local = self._last_update.astimezone().strftime("%H:%M:%S")
        self._status_label.setText(f"Останнє оновлення [{host}]: {local}")

    def _redraw_active(self) -> None:
        host = self._active_host()
        if host is None:
            return
        data = self._store.get(host)
        self._canvas.render_route(
            data.current_route,
            avg_ping_fn=lambda ip: self._store.avg_ping(host, ip),
            previous_route=self._store.inactive_route(host),
        )

    def closeEvent(self, event: QCloseEvent | None) -> None:
        self._worker.stop()
        self._worker.wait(5000)
        self._store.close()
        if event is not None:
            event.accept()
