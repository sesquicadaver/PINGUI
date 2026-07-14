"""CLI entry point for PINGUI."""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

from pingui.config import ConfigError, load_hosts_config
from pingui.export.session_report import export_session_csv, export_session_html
from pingui.geoip import configure as configure_geoip
from pingui.geoip.country import GeoIpHintsError
from pingui.icmp.raw_socket import RawIcmpPermissionError, check_raw_icmp_permission
from pingui.logging_setup import setup_logging
from pingui.monitor.alert_dispatcher import AlertDispatcher, build_alert_dispatcher
from pingui.monitor.daemon_runner import (
    DEFAULT_PID_FILE,
    DaemonError,
    PidFile,
    run_headless_monitor,
)
from pingui.monitor.session_store import SessionStore
from pingui.persistence.policy import (
    PersistencePolicy,
    apply_cli_event_overrides,
    load_persistence_config,
    resolve_session_db_path,
)
from pingui.telemetry_config import apply_cli_overrides, load_telemetry_config
from pingui.persistence.session_db import SessionDatabase
from pingui.persistence.timeseries.base import TimeSeriesBackend, TimeSeriesConfigError
from pingui.persistence.timeseries.factory import create_timeseries_backend

DEFAULT_CONFIG = Path("config/hosts.example.yaml")
SUBCOMMANDS = frozenset({"run", "export", "monitor", "daemon", "stop", "status"})


def _add_monitor_args(parser: argparse.ArgumentParser) -> None:
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
        "--telemetry-syslog",
        default=None,
        help="Override telemetry syslog sink as HOST:PORT or [IPv6]:PORT",
    )
    parser.add_argument(
        "--telemetry-jsonl",
        type=Path,
        default=None,
        help="Override telemetry JSONL directory (profile telemetry.jsonl_dir)",
    )
    parser.add_argument(
        "--telemetry-otlp",
        default=None,
        help="Override telemetry OTLP/HTTP endpoint URL (profile telemetry.otlp)",
    )
    parser.add_argument(
        "--no-persist-route-change",
        action="store_true",
        help="Do not write route_change rows to persistence_event",
    )
    parser.add_argument(
        "--no-persist-probe-error",
        action="store_true",
        help="Do not write probe_error rows to persistence_event",
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


def _add_alert_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--alert-webhook",
        default=None,
        help="POST route-change JSON to this webhook URL (secrets are not logged)",
    )
    parser.add_argument(
        "--desktop-alerts",
        action="store_true",
        help="Linux desktop notifications on route change (notify-send)",
    )
    parser.add_argument(
        "--alert-rate-limit",
        type=int,
        default=10,
        help="Max alerts per host per hour (default: 10)",
    )


def build_legacy_parser() -> argparse.ArgumentParser:
    """Flat CLI (backward compatible with pre-PY-023 invocations)."""
    parser = argparse.ArgumentParser(
        description="PINGUI — Linux route and ping monitor (in-memory session)",
    )
    _add_monitor_args(parser)
    _add_alert_args(parser)
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
    return parser


def build_command_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="PINGUI — Linux route and ping monitor",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    run_parser = sub.add_parser("run", help="Launch PyQt6 GUI (default mode)")
    _add_monitor_args(run_parser)
    _add_alert_args(run_parser)

    export_parser = sub.add_parser("export", help="Export session report and exit")
    _add_monitor_args(export_parser)
    export_parser.add_argument("--csv", type=Path, default=None, help="CSV output path")
    export_parser.add_argument("--html", type=Path, default=None, help="HTML output path")

    monitor_parser = sub.add_parser(
        "monitor",
        help="Headless foreground monitoring (no GUI)",
    )
    _add_monitor_args(monitor_parser)
    _add_alert_args(monitor_parser)

    daemon_parser = sub.add_parser(
        "daemon",
        help="Headless monitoring with PID file (NOC/systemd)",
    )
    _add_monitor_args(daemon_parser)
    _add_alert_args(daemon_parser)
    daemon_parser.add_argument(
        "--pid-file",
        type=Path,
        default=DEFAULT_PID_FILE,
        help=f"PID file path (default: {DEFAULT_PID_FILE})",
    )

    stop_parser = sub.add_parser("stop", help="Stop daemon via PID file")
    stop_parser.add_argument(
        "--pid-file",
        type=Path,
        default=DEFAULT_PID_FILE,
        help=f"PID file path (default: {DEFAULT_PID_FILE})",
    )

    status_parser = sub.add_parser("status", help="Check daemon status via PID file")
    status_parser.add_argument(
        "--pid-file",
        type=Path,
        default=DEFAULT_PID_FILE,
        help=f"PID file path (default: {DEFAULT_PID_FILE})",
    )

    return parser


def _validate_timing(args: argparse.Namespace) -> int | None:
    if args.interval <= 0:
        print("Config error: --interval must be positive", file=sys.stderr)
        return 1
    if args.max_hops < 1:
        print("Config error: --max-hops must be >= 1", file=sys.stderr)
        return 1
    if args.timeout <= 0:
        print("Config error: --timeout must be positive", file=sys.stderr)
        return 1
    rate_limit = getattr(args, "alert_rate_limit", 10)
    if rate_limit < 1:
        print("Config error: --alert-rate-limit must be >= 1", file=sys.stderr)
        return 1
    return None


def _build_alert_dispatcher(args: argparse.Namespace) -> AlertDispatcher | None:
    webhook = getattr(args, "alert_webhook", None)
    desktop = getattr(args, "desktop_alerts", False)
    rate_limit = getattr(args, "alert_rate_limit", 10)
    return build_alert_dispatcher(
        webhook_url=webhook,
        desktop_alerts=desktop,
        max_alerts_per_hour=rate_limit,
    )


def _load_hosts(args: argparse.Namespace) -> list[str] | int:
    try:
        return load_hosts_config(args.config)
    except (OSError, ValueError) as exc:
        print(f"Config error: {exc}", file=sys.stderr)
        return 1


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


def _require_icmp() -> int | None:
    try:
        check_raw_icmp_permission()
    except RawIcmpPermissionError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    return None


def _configure_geoip(args: argparse.Namespace) -> int | None:
    try:
        configure_geoip(enabled=not args.no_geoip, hints_path=args.geoip_hints)
    except GeoIpHintsError as exc:
        print(f"Config error: {exc}", file=sys.stderr)
        return 1
    return None


def _resolve_persistence(args: argparse.Namespace) -> tuple[Path | None, PersistencePolicy]:
    """Return (session_db_path, PersistencePolicy) with CLI > YAML > defaults."""
    yaml_cfg = load_persistence_config(args.config)
    policy = apply_cli_event_overrides(
        yaml_cfg.events,
        no_persist_route_change=bool(getattr(args, "no_persist_route_change", False)),
        no_persist_probe_error=bool(getattr(args, "no_persist_probe_error", False)),
    )
    session_db = resolve_session_db_path(args.session_db, args.config)
    return session_db, policy


def _resolve_telemetry(args: argparse.Namespace):
    """Return TelemetryConfig with CLI overrides over YAML (P16-041)."""
    return apply_cli_overrides(
        load_telemetry_config(args.config),
        syslog=getattr(args, "telemetry_syslog", None),
        jsonl_dir=getattr(args, "telemetry_jsonl", None),
        otlp=getattr(args, "telemetry_otlp", None),
    )


def _run_gui(args: argparse.Namespace, hosts: list[str]) -> int:
    geo_err = _configure_geoip(args)
    if geo_err is not None:
        return geo_err
    try:
        session_db_path, policy = _resolve_persistence(args)
        _resolve_telemetry(args)
    except ConfigError as exc:
        print(f"Config error: {exc}", file=sys.stderr)
        return 1
    timeseries_backend = _build_timeseries_backend(args)
    from pingui.ui.app import run_app

    return run_app(
        hosts,
        config_path=args.config,
        interval_seconds=args.interval,
        max_hops=args.max_hops,
        timeout=args.timeout,
        quiet=not args.verbose,
        session_db_path=session_db_path,
        geo_map_enabled=not args.no_geo_map,
        timeseries_backend=timeseries_backend,
        alert_dispatcher=_build_alert_dispatcher(args),
        persistence_policy=policy,
    )


def _run_headless(args: argparse.Namespace, hosts: list[str], *, pid_file: Path | None) -> int:
    icmp_err = _require_icmp()
    if icmp_err is not None:
        return icmp_err
    try:
        session_db_path, policy = _resolve_persistence(args)
        _resolve_telemetry(args)
    except ConfigError as exc:
        print(f"Config error: {exc}", file=sys.stderr)
        return 1
    timeseries_backend = _build_timeseries_backend(args)
    return run_headless_monitor(
        hosts,
        interval_seconds=args.interval,
        max_hops=args.max_hops,
        timeout=args.timeout,
        session_db_path=session_db_path,
        timeseries_backend=timeseries_backend,
        pid_file=pid_file,
        alert_dispatcher=_build_alert_dispatcher(args),
        persistence_policy=policy,
    )


def _dispatch_command(args: argparse.Namespace) -> int:
    command = getattr(args, "command", "run")

    if command == "stop":
        try:
            return PidFile.stop(args.pid_file)
        except (DaemonError, ProcessLookupError, PermissionError) as exc:
            print(str(exc), file=sys.stderr)
            return 1

    if command == "status":
        return PidFile.status(args.pid_file)

    setup_logging(verbose=args.verbose)

    timing_err = _validate_timing(args)
    if timing_err is not None:
        return timing_err

    hosts_result = _load_hosts(args)
    if isinstance(hosts_result, int):
        return hosts_result
    hosts = hosts_result

    if command == "export":
        csv_path = getattr(args, "csv", None) or getattr(args, "export_csv", None)
        html_path = getattr(args, "html", None) or getattr(args, "export_html", None)
        if csv_path is None and html_path is None:
            print("Config error: specify --csv and/or --html", file=sys.stderr)
            return 1
        try:
            session_db_path, _policy = _resolve_persistence(args)
        except ConfigError as exc:
            print(f"Config error: {exc}", file=sys.stderr)
            return 1
        return _export_reports(
            hosts,
            session_db_path=session_db_path,
            csv_path=csv_path,
            html_path=html_path,
        )

    if command == "monitor":
        return _run_headless(args, hosts, pid_file=None)

    if command == "daemon":
        return _run_headless(args, hosts, pid_file=args.pid_file)

    icmp_err = _require_icmp()
    if icmp_err is not None:
        return icmp_err

    return _run_gui(args, hosts)


def parse_args(argv: list[str] | None) -> argparse.Namespace:
    argv = list(argv if argv is not None else sys.argv[1:])
    if argv and argv[0] in SUBCOMMANDS:
        return build_command_parser().parse_args(argv)
    legacy = build_legacy_parser().parse_args(argv)
    if legacy.export_csv is not None or legacy.export_html is not None:
        legacy.command = "export"
        legacy.csv = legacy.export_csv
        legacy.html = legacy.export_html
    else:
        legacy.command = "run"
    return legacy


def build_parser() -> argparse.ArgumentParser:
    """Backward-compatible alias used in tests/docs."""
    return build_legacy_parser()


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    return _dispatch_command(args)


if __name__ == "__main__":
    sys.exit(main())
