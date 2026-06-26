"""Qt application bootstrap."""

from __future__ import annotations

import sys
from pathlib import Path

from PyQt6.QtCore import QMessageLogContext, QtMsgType, qInstallMessageHandler
from PyQt6.QtWidgets import QApplication

from pingui.ui.main_window import MainWindow


def _quiet_qt_messages(
    msg_type: QtMsgType,
    _context: QMessageLogContext,
    message: str | None,
) -> None:
    """Drop Qt debug/info/warning chatter; keep only critical failures."""
    if msg_type in (
        QtMsgType.QtDebugMsg,
        QtMsgType.QtInfoMsg,
        QtMsgType.QtWarningMsg,
    ):
        return
    if message:
        sys.stderr.write(f"{message}\n")


def run_app(
    hosts: list[str],
    config_path: Path | str,
    interval_seconds: float = 1.0,
    max_hops: int = 20,
    timeout: float = 0.5,
    *,
    quiet: bool = True,
) -> int:
    """Create QApplication and run main window event loop."""
    if quiet:
        qInstallMessageHandler(_quiet_qt_messages)
    app = QApplication(sys.argv)
    window = MainWindow(
        hosts,
        config_path=config_path,
        interval_seconds=interval_seconds,
        max_hops=max_hops,
        timeout=timeout,
    )
    window.show()
    return app.exec()
