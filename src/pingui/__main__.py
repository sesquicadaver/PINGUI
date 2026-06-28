"""CLI entry point for PINGUI."""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

from pingui.config import load_hosts_config
from pingui.export.session_report import export_session_csv, export_session_html
from pingui.geoip import configure as configure_geoip
from pingui.icmp.raw_socket import RawIcmpPermissionError, check_raw_icmp_permission
from pingui.logging_setup import setup_logging
from pingui.monitor.session_store import SessionStore
from pingui.persistence.session_db import SessionDatabase
from pingui.persistence.timeseries.base import TimeSeriesBackend, TimeSeriesConfigError
from pingui.persistence.timeseries.factory import create_timeseries_backend

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
    parser.add_argument(
        "--export-csv",
        type=Path,
        default=None,
        help="Export session report to CSV and exit (optional with --session-db)",
    )
    parser.add_argument(
        "--export-html",
        type=Path,
        default=None,
        help="Export session report to HTML and exit (optional with --session-db)",
    )
    parser.add_argument(
        "--geoip-hints",
        type=Path,
        default=None,
        help="YAML with CIDR→country hints for graph labels (default: config/geoip_hints.yaml)",
    )
    parser.add_argument(
        "--no-geoip",
        action="store_true",
        help="Disable country hints in hop node labels",
    )
    parser.add_argument(
        "--no-geo-map",
        action="store_true",
        help="Disable folium geo-map tab in GUI",
    )
    parser.add_argument(
        "--ts-backend",
        choices=("influx", "timescale"),
        default=None,
        help="Optional time-series backend for RTT/route metrics",
    )
    parser.add_argument(
        "--influx-url",
        default=None,
        help="InfluxDB URL (or INFLUXDB_URL env)",
    )
    parser.add_argument(
        "--influx-token",
        default=None,
        help="InfluxDB token (or INFLUXDB_TOKEN env)",
    )
    parser.add_argument(
        "--influx-org",
        default=None,
        help="InfluxDB org (or INFLUXDB_ORG env)",
    )
    parser.add_argument(
        "--influx-bucket",
        default=None,
        help="InfluxDB bucket (or INFLUXDB_BUCKET env)",
    )
    parser.add_argument(
        "--timescale-dsn",
        default=None,
        help="PostgreSQL/Timescale DSN (or PINGUI_TIMESCALE_DSN env)",
    )
    return parser


def _export_reports(
    hosts: list[str],
    *,
    session_db_path: Path | None,
    csv_path: Path | None,
    html_path: Path | None,
) -> int:
    session_db = SessionDatabase(session_db_path) if session_db_path is not None else None
    store = SessionStore(hosts, session_db=session_db)
    try:
        if csv_path is not None:
            export_session_csv(store, csv_path)
        if html_path is not None:
            export_session_html(store, html_path)
    finally:
        store.close()
    return 0


def _build_timeseries_backend(args: argparse.Namespace) -> TimeSeriesBackend | None:
    try:
        return create_timeseries_backend(
            args.ts_backend,
            influx_url=args.influx_url or os.environ.get("INFLUXDB_URL"),
            influx_token=args.influx_token or os.environ.get("INFLUXDB_TOKEN"),
            influx_org=args.influx_org or os.environ.get("INFLUXDB_ORG"),
            influx_bucket=args.influx_bucket or os.environ.get("INFLUXDB_BUCKET"),
            timescale_dsn=args.timescale_dsn or os.environ.get("PINGUI_TIMESCALE_DSN"),
        )
    except TimeSeriesConfigError as exc:
        print(f"Config error: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc


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

    if args.export_csv is not None or args.export_html is not None:
        return _export_reports(
            hosts,
            session_db_path=args.session_db,
            csv_path=args.export_csv,
            html_path=args.export_html,
        )

    try:
        check_raw_icmp_permission()
    except RawIcmpPermissionError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    configure_geoip(enabled=not args.no_geoip, hints_path=args.geoip_hints)
    timeseries_backend = _build_timeseries_backend(args)

    from pingui.ui.app import run_app

    return run_app(
        hosts,
        config_path=args.config,
        interval_seconds=args.interval,
        max_hops=args.max_hops,
        timeout=args.timeout,
        quiet=not args.verbose,
        session_db_path=args.session_db,
        geo_map_enabled=not args.no_geo_map,
        timeseries_backend=timeseries_backend,
    )


if __name__ == "__main__":
    sys.exit(main())
