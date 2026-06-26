"""Config unit tests."""

from __future__ import annotations

from pathlib import Path

import pytest

from pingui.config import ConfigError, load_hosts_config, save_hosts_config, validate_session_host

FIXTURES = Path(__file__).resolve().parents[1] / "fixtures"


def test_load_valid_config(tmp_path: Path) -> None:
    cfg = tmp_path / "hosts.yaml"
    cfg.write_text(
        "hosts:\n  - 8.8.8.8\n  - google.com\n",
        encoding="utf-8",
    )
    hosts = load_hosts_config(cfg)
    assert hosts == ["8.8.8.8", "google.com"]


def test_reject_empty_hosts(tmp_path: Path) -> None:
    cfg = tmp_path / "hosts.yaml"
    cfg.write_text("hosts: []\n", encoding="utf-8")
    assert load_hosts_config(cfg) == []


def test_save_hosts_config_roundtrip(tmp_path: Path) -> None:
    cfg = tmp_path / "hosts.yaml"
    hosts = ["8.8.8.8", "1.1.1.1"]
    save_hosts_config(cfg, hosts)
    assert load_hosts_config(cfg) == hosts


def test_reject_too_many_hosts(tmp_path: Path) -> None:
    cfg = tmp_path / "hosts.yaml"
    lines = "\n".join(f'  - "h{i}.example"' for i in range(11))
    cfg.write_text(f"hosts:\n{lines}\n", encoding="utf-8")
    with pytest.raises(ConfigError, match="between 0 and 10"):
        load_hosts_config(cfg)


def test_reject_duplicate_hosts(tmp_path: Path) -> None:
    cfg = tmp_path / "hosts.yaml"
    cfg.write_text("hosts:\n  - Google.com\n  - google.com\n", encoding="utf-8")
    with pytest.raises(ConfigError, match="Duplicate"):
        load_hosts_config(cfg)


def test_reject_invalid_host(tmp_path: Path) -> None:
    cfg = tmp_path / "hosts.yaml"
    cfg.write_text('hosts:\n  - "bad host name"\n', encoding="utf-8")
    with pytest.raises(ConfigError, match="Invalid host"):
        load_hosts_config(cfg)


def test_example_config_loads() -> None:
    root = Path(__file__).resolve().parents[2]
    hosts = load_hosts_config(root / "config" / "hosts.example.yaml")
    assert len(hosts) == 9
    assert "example.com" not in hosts


def test_validate_session_host() -> None:
    assert validate_session_host("8.8.4.4", ["8.8.8.8"]) == "8.8.4.4"
    with pytest.raises(ConfigError, match="Duplicate"):
        validate_session_host("8.8.8.8", ["8.8.8.8"])
    with pytest.raises(ConfigError, match="Maximum"):
        validate_session_host("1.1.1.1", [f"h{i}.example" for i in range(10)])


def test_save_hosts_config_rejects_duplicate() -> None:
    with pytest.raises(ConfigError, match="Duplicate"):
        save_hosts_config(Path("unused.yaml"), ["8.8.8.8", "8.8.8.8"])


def test_save_hosts_config_rejects_too_many() -> None:
    hosts = [f"10.0.0.{i}" for i in range(11)]
    with pytest.raises(ConfigError, match="between 0 and 10"):
        save_hosts_config(Path("unused.yaml"), hosts)
