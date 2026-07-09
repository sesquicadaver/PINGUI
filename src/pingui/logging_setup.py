"""Centralized logging configuration."""

from __future__ import annotations

import logging
import sys


def setup_logging(*, verbose: bool = False) -> None:
    """Configure root logger. GUI stays quiet unless --verbose."""
    root = logging.getLogger()
    if root.handlers:
        return
    level = logging.DEBUG if verbose else logging.ERROR
    handler = logging.StreamHandler(sys.stderr)
    handler.setFormatter(
        logging.Formatter("%(asctime)s [%(levelname)s] %(name)s: %(message)s")
    )
    root.addHandler(handler)
    root.setLevel(level)
    logging.getLogger("scapy").setLevel(logging.ERROR)
    logging.getLogger("matplotlib").setLevel(logging.ERROR)
