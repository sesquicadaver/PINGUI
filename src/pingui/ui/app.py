"""Qt application bootstrap."""

from __future__ import annotations

import sys

from PyQt6.QtWidgets import QApplication

from pingui.ui.main_window import MainWindow


def run_app(
    hosts: list[str],
    interval_seconds: float = 1.0,
    max_hops: int = 20,
    timeout: float = 0.5,
) -> int:
    """Create QApplication and run main window event loop."""
    app = QApplication(sys.argv)
    window = MainWindow(
        hosts,
        interval_seconds=interval_seconds,
        max_hops=max_hops,
        timeout=timeout,
    )
    window.show()
    return app.exec()
