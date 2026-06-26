"""UI smoke test with offscreen Qt platform."""

from __future__ import annotations

import os

import pytest

pytest.importorskip("PyQt6")


@pytest.fixture(scope="module", autouse=True)
def offscreen_qt() -> None:
    os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")


def test_main_window_creates() -> None:
    from PyQt6.QtGui import QCloseEvent
    from PyQt6.QtWidgets import QApplication

    from pingui.ui.main_window import MainWindow

    app = QApplication.instance() or QApplication([])
    window = MainWindow(["127.0.0.1"], interval_seconds=60.0)
    assert window.windowTitle()
    window.closeEvent(QCloseEvent())
    app.processEvents()
