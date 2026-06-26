"""Main application window."""

from __future__ import annotations

import time
from datetime import UTC, datetime

from PyQt6.QtCore import Qt
from PyQt6.QtGui import QCloseEvent
from PyQt6.QtWidgets import (
    QHBoxLayout,
    QLabel,
    QListWidget,
    QListWidgetItem,
    QMainWindow,
    QTextEdit,
    QVBoxLayout,
    QWidget,
)

from pingui.models import RouteSnapshot
from pingui.monitor.session_store import SessionStore
from pingui.monitor.worker import LightweightMonitorWorker
from pingui.ui.graph_canvas import GraphCanvas


class MainWindow(QMainWindow):
    """Primary GUI: host list, route-change log, topological graph."""

    def __init__(
        self,
        hosts: list[str],
        interval_seconds: float = 1.0,
        max_hops: int = 20,
        timeout: float = 0.5,
    ) -> None:
        super().__init__()
        self.setWindowTitle("PINGUI — Сесійний монітор маршрутів Linux")
        self.resize(1100, 700)

        self._store = SessionStore(hosts)
        self._last_update: datetime | None = None

        central = QWidget()
        self.setCentralWidget(central)
        root = QHBoxLayout(central)

        left = QVBoxLayout()
        self._host_list = QListWidget()
        for host in hosts:
            self._host_list.addItem(QListWidgetItem(host))
        self._host_list.setCurrentRow(0)
        self._host_list.itemClicked.connect(self._on_host_selected)

        self._status_label = QLabel("Очікування даних…")
        self._status_label.setAlignment(Qt.AlignmentFlag.AlignLeft)

        self._log = QTextEdit()
        self._log.setReadOnly(True)
        self._log.setPlaceholderText("Логи змін маршрутів відображатимуться тут…")

        left.addWidget(self._host_list, stretch=1)
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

    def _active_host(self) -> str | None:
        item = self._host_list.currentItem()
        return item.text() if item else None

    def _on_host_selected(self) -> None:
        self._redraw_active()

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
        )

    def closeEvent(self, event: QCloseEvent | None) -> None:
        self._worker.stop()
        self._worker.wait(5000)
        if event is not None:
            event.accept()
