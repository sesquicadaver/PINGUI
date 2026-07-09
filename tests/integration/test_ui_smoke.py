"""UI smoke test with offscreen Qt platform."""

from __future__ import annotations

from pathlib import Path

import pytest

pytest.importorskip("PyQt6")


def test_main_window_creates(tmp_path: Path) -> None:
    from PyQt6.QtCore import Qt
    from PyQt6.QtGui import QCloseEvent
    from PyQt6.QtWidgets import QApplication, QLineEdit, QPushButton

    from pingui.ui.main_window import MainWindow

    app = QApplication.instance() or QApplication([])
    window = MainWindow(["127.0.0.1"], config_path=tmp_path / "hosts.yaml", interval_seconds=60.0)
    assert window.windowTitle()
    assert window.findChild(QLineEdit) is not None
    assert window.findChild(QPushButton) is not None
    item = window._host_list.item(0)
    assert item is not None
    assert item.flags() & Qt.ItemFlag.ItemIsUserCheckable
    assert item.checkState() == Qt.CheckState.Unchecked
    assert window._worker.enabled_hosts() == []
    window.closeEvent(QCloseEvent())
    app.processEvents()


def test_main_window_add_edit_remove_save(tmp_path: Path) -> None:
    from PyQt6.QtGui import QCloseEvent
    from PyQt6.QtWidgets import QApplication

    from pingui.ui.main_window import MainWindow

    app = QApplication.instance() or QApplication([])
    config = tmp_path / "hosts.yaml"
    config.write_text("hosts: []\n", encoding="utf-8")
    window = MainWindow([], config_path=config, interval_seconds=60.0)

    window._host_input.setText("8.8.8.8")
    window._on_add_host()
    assert window._host_list.count() == 1

    window._host_input.setText("1.1.1.1")
    window._on_edit_host()
    assert window._store.hosts() == ["1.1.1.1"]

    window._on_save_hosts()
    assert "1.1.1.1" in config.read_text(encoding="utf-8")

    window._on_remove_host()
    assert window._host_list.count() == 0

    window.closeEvent(QCloseEvent())
    app.processEvents()


def test_main_window_enable_and_log_handlers(tmp_path: Path) -> None:
    from PyQt6.QtCore import Qt
    from PyQt6.QtGui import QCloseEvent
    from PyQt6.QtWidgets import QApplication

    from pingui.models import HopNode, RouteSnapshot
    from pingui.ui.main_window import MainWindow

    app = QApplication.instance() or QApplication([])
    window = MainWindow(["8.8.8.8"], config_path=tmp_path / "hosts.yaml", interval_seconds=60.0)
    item = window._host_list.item(0)
    assert item is not None
    item.setCheckState(Qt.CheckState.Checked)
    assert window._store.get("8.8.8.8").enabled

    snap = RouteSnapshot(
        target="8.8.8.8",
        target_ip="8.8.8.8",
        nodes=[
            HopNode(hop=1, ip="10.0.0.1", ping_ms=5.0),
            HopNode(hop=2, ip="8.8.8.8", ping_ms=10.0),
        ],
    )
    window._on_data_received("8.8.8.8", snap)
    assert window._status_label.text().startswith("Останнє оновлення")
    window._on_route_changed("8.8.8.8", ["10.0.0.1"], ["192.168.1.1"])
    window._on_probe_error("8.8.8.8", "timeout")
    assert "ЗМІНА МАРШРУТУ" in window._log.toPlainText()
    window._on_data_received("8.8.8.8", "not-a-snapshot")

    window.closeEvent(QCloseEvent())
    app.processEvents()


def test_main_window_invalid_host_and_limit(tmp_path: Path) -> None:
    from PyQt6.QtGui import QCloseEvent
    from PyQt6.QtWidgets import QApplication

    from pingui.ui.main_window import MainWindow

    app = QApplication.instance() or QApplication([])
    window = MainWindow([], config_path=tmp_path / "hosts.yaml", interval_seconds=60.0)
    window._host_input.setText("bad host name")
    window._on_add_host()
    assert window._host_list.count() == 0
    assert "Не вдалося додати" in window._log.toPlainText()
    window.closeEvent(QCloseEvent())

    hosts = [f"10.0.0.{i}" for i in range(10)]
    limited = MainWindow(hosts, config_path=tmp_path / "full.yaml", interval_seconds=60.0)
    assert not limited._add_button.isEnabled()
    limited.closeEvent(QCloseEvent())
    app.processEvents()
