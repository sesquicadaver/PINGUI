"""CLI entry point for PINGUI."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from pingui.config import load_hosts_config
from pingui.icmp.raw_socket import RawIcmpPermissionError, check_raw_icmp_permission
from pingui.logging_setup import setup_logging

DEFAULT_CONFIG = Path("config/hosts.example.yaml")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="PINGUI — Linux route and ping monitor (in-memory session)",
    )
    parser.add_argument(
        "--config",
        type=Path,
        default=DEFAULT_CONFIG,
        help="YAML file with hosts list (default: config/hosts.example.yaml)",
    )
    parser.add_argument(
        "--interval",
        type=float,
        default=1.0,
        help="Seconds between full monitoring cycles (default: 1.0)",
    )
    parser.add_argument(
        "--max-hops",
        type=int,
        default=20,
        help="Maximum TTL hops per trace (default: 20)",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=0.5,
        help="ICMP probe timeout in seconds (default: 0.5)",
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Enable debug logging",
    )
    parser.add_argument(
        "--session-db",
        type=Path,
        default=None,
        help="Optional SQLite path to persist routes/ping between sessions",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    setup_logging(verbose=args.verbose)

    if args.interval <= 0:
        print("Config error: --interval must be positive", file=sys.stderr)
        return 1
    if args.max_hops < 1:
        print("Config error: --max-hops must be >= 1", file=sys.stderr)
        return 1
    if args.timeout <= 0:
        print("Config error: --timeout must be positive", file=sys.stderr)
        return 1

    try:
        hosts = load_hosts_config(args.config)
    except (OSError, ValueError) as exc:
        print(f"Config error: {exc}", file=sys.stderr)
        return 1

    try:
        check_raw_icmp_permission()
    except RawIcmpPermissionError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    from pingui.ui.app import run_app

    return run_app(
        hosts,
        config_path=args.config,
        interval_seconds=args.interval,
        max_hops=args.max_hops,
        timeout=args.timeout,
        quiet=not args.verbose,
        session_db_path=args.session_db,
    )


if __name__ == "__main__":
    sys.exit(main())
