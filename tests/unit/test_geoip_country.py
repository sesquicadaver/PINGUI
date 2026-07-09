"""GeoIP country hint unit tests."""

from __future__ import annotations

from pathlib import Path

import pytest

from pingui.geoip.country import (
    CountryLookup,
    GeoIpHintsError,
    configure,
    country_code_for_ip,
)


@pytest.fixture(autouse=True)
def _reset_geoip() -> None:
    configure(enabled=True)
    yield
    configure(enabled=True)


def test_private_ip_returns_lan() -> None:
    lookup = CountryLookup.load(Path("/no/such/file.yaml"))
    assert lookup.lookup("10.0.0.1") == "LAN"
    assert lookup.lookup("192.168.1.1") == "LAN"
    assert lookup.lookup("127.0.0.1") == "LAN"


def test_bundled_hints_public_dns() -> None:
    lookup = CountryLookup.load(Path("config/geoip_hints.yaml"))
    assert lookup.lookup("8.8.8.8") == "US"
    assert lookup.lookup("1.1.1.1") == "AU"


def test_unknown_public_ip_returns_none() -> None:
    lookup = CountryLookup.load(Path("config/geoip_hints.yaml"))
    assert lookup.lookup("41.41.41.41") is None


def test_custom_hints_yaml(tmp_path: Path) -> None:
    hints = tmp_path / "hints.yaml"
    hints.write_text(
        "prefixes:\n  41.41.41.0/24: XX\n",
        encoding="utf-8",
    )
    lookup = CountryLookup.load(hints)
    assert lookup.lookup("41.41.41.5") == "XX"


def test_invalid_hints_raises(tmp_path: Path) -> None:
    bad = tmp_path / "bad.yaml"
    bad.write_text("prefixes:\n  not-a-cidr: US\n", encoding="utf-8")
    with pytest.raises(GeoIpHintsError):
        CountryLookup.load(bad)


def test_configure_disable() -> None:
    configure(enabled=True)
    assert country_code_for_ip("8.8.8.8") == "US"
    configure(enabled=False)
    assert country_code_for_ip("8.8.8.8") is None


def test_configure_custom_path(tmp_path: Path) -> None:
    hints = tmp_path / "hints.yaml"
    hints.write_text("prefixes:\n  8.8.8.0/24: DE\n", encoding="utf-8")
    configure(enabled=True, hints_path=hints)
    assert country_code_for_ip("8.8.8.8") == "DE"


def test_ipv6_private_returns_lan() -> None:
    lookup = CountryLookup.load(Path("config/geoip_hints.yaml"))
    assert lookup.lookup("::1") == "LAN"
    assert lookup.lookup("fe80::1") == "LAN"


def test_ipv6_public_prefix_match() -> None:
    lookup = CountryLookup.load(Path("config/geoip_hints.yaml"))
    assert lookup.lookup("2001:4860:4860::8888") == "US"
    assert lookup.lookup("2001:db8::1") == "US"


def test_ipv6_custom_hints_yaml(tmp_path: Path) -> None:
    hints = tmp_path / "hints.yaml"
    hints.write_text(
        "prefixes:\n  8.8.8.0/24: US\n"
        "prefixes_v6:\n  2001:db8:1::/64: PL\n",
        encoding="utf-8",
    )
    lookup = CountryLookup.load(hints)
    assert lookup.lookup("2001:db8:1::42") == "PL"


def test_v6_only_hints_yaml(tmp_path: Path) -> None:
    hints = tmp_path / "hints.yaml"
    hints.write_text("prefixes_v6:\n  2001:db8:1::/64: PL\n", encoding="utf-8")
    lookup = CountryLookup.load(hints)
    assert lookup.lookup("2001:db8:1::42") == "PL"
    assert lookup.lookup("8.8.8.8") is None


def test_invalid_ipv6_hints_raises(tmp_path: Path) -> None:
    bad = tmp_path / "bad.yaml"
    bad.write_text("prefixes:\n  8.8.8.0/24: US\nprefixes_v6: []\n", encoding="utf-8")
    with pytest.raises(GeoIpHintsError):
        CountryLookup.load(bad)
