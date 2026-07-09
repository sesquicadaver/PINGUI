"""CLI export mode tests."""

from __future__ import annotations

from pathlib import Path

from pingui.__main__ import main


def test_export_csv_without_gui(tmp_path: Path) -> None:
    cfg = tmp_path / "hosts.yaml"
    cfg.write_text("hosts:\n  - 8.8.8.8\n", encoding="utf-8")
    csv_path = tmp_path / "out.csv"
    assert main(["--config", str(cfg), "--export-csv", str(csv_path)]) == 0
    assert csv_path.is_file()


def test_export_html_without_gui(tmp_path: Path) -> None:
    cfg = tmp_path / "hosts.yaml"
    cfg.write_text("hosts:\n  - 8.8.8.8\n", encoding="utf-8")
    html_path = tmp_path / "out.html"
    assert main(["--config", str(cfg), "--export-html", str(html_path)]) == 0
    assert html_path.is_file()
    assert "8.8.8.8" in html_path.read_text(encoding="utf-8")
