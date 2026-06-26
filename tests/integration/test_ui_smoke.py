"""UI smoke test with offscreen Qt platform."""

from __future__ import annotations

import os
from pathlib import Path

import pytest

pytest.importorskip("PyQt6")


@pytest.fixture(scope="module", autouse=True)
def offscreen_qt() -> None:
    os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")


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
