#!/usr/bin/env python3
"""Verify UK/EN documentation file and language-switcher parity."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

UK_BANNER = re.compile(
    r"^> \*\*Мова:\*\* Українська · \[English\]\((?P<link>[^)]+)\)\s*$",
    re.MULTILINE,
)
EN_BANNER = re.compile(
    r"^> \*\*Language:\*\* English · \[Українська\]\((?P<link>[^)]+)\)\s*$",
    re.MULTILINE,
)

# Paired files: (uk_path, en_path, expected_uk_link, expected_en_link) relative to each file.
FILE_PAIRS: list[tuple[Path, Path, str, str]] = [
    (ROOT / "README.md", ROOT / "README.en.md", "README.en.md", "README.md"),
    (ROOT / "ROADMAP.md", ROOT / "ROADMAP.en.md", "ROADMAP.en.md", "ROADMAP.md"),
    (ROOT / "java/README.md", ROOT / "java/README.en.md", "README.en.md", "README.md"),
]

# Must contain a Ukrainian banner linking into docs/en/ (no full EN twin).
BANNER_ONLY_UK: list[tuple[Path, re.Pattern[str]]] = [
    (
        ROOT / "CHANGELOG.md",
        re.compile(r"^> \*\*Мова:\*\* Українська · \[English\]\((?P<link>docs/en/[^)]+)\)", re.MULTILINE),
    ),
]


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def banner_link(text: str, pattern: re.Pattern[str]) -> str | None:
    match = pattern.search(text)
    return match.group("link") if match else None


def list_markdown(dir_path: Path) -> set[str]:
    return {path.name for path in dir_path.glob("*.md")}


def check_docs_directories() -> list[str]:
    errors: list[str] = []
    uk_dir = ROOT / "docs"
    en_dir = ROOT / "docs" / "en"

    uk_files = list_markdown(uk_dir)
    en_files = list_markdown(en_dir)

    for name in sorted(uk_files - en_files):
        errors.append(f"docs/{name}: missing counterpart docs/en/{name}")
    for name in sorted(en_files - uk_files):
        errors.append(f"docs/en/{name}: no Ukrainian counterpart docs/{name}")

    for name in sorted(uk_files & en_files):
        errors.extend(
            check_banner_pair(
                uk_dir / name,
                en_dir / name,
                expected_uk_link=f"en/{name}",
                expected_en_link=f"../{name}",
            )
        )
    return errors


def check_banner_pair(
    uk_path: Path,
    en_path: Path,
    *,
    expected_uk_link: str,
    expected_en_link: str,
) -> list[str]:
    errors: list[str] = []
    uk_rel = uk_path.relative_to(ROOT).as_posix()
    en_rel = en_path.relative_to(ROOT).as_posix()

    if not uk_path.is_file():
        errors.append(f"{uk_rel}: file missing")
        return errors
    if not en_path.is_file():
        errors.append(f"{en_rel}: file missing")
        return errors

    uk_text = read_text(uk_path)
    en_text = read_text(en_path)

    uk_link = banner_link(uk_text, UK_BANNER)
    en_link = banner_link(en_text, EN_BANNER)

    if uk_link is None:
        errors.append(f"{uk_rel}: missing Ukrainian language banner")
    elif uk_link != expected_uk_link:
        errors.append(
            f"{uk_rel}: banner links to {uk_link!r}, expected {expected_uk_link!r}",
        )

    if en_link is None:
        errors.append(f"{en_rel}: missing English language banner")
    elif en_link != expected_en_link:
        errors.append(
            f"{en_rel}: banner links to {en_link!r}, expected {expected_en_link!r}",
        )

    return errors


def check_file_pairs() -> list[str]:
    errors: list[str] = []
    for uk_path, en_path, uk_link, en_link in FILE_PAIRS:
        errors.extend(
            check_banner_pair(
                uk_path,
                en_path,
                expected_uk_link=uk_link,
                expected_en_link=en_link,
            )
        )
    return errors


def check_banner_only() -> list[str]:
    errors: list[str] = []
    for uk_path, pattern in BANNER_ONLY_UK:
        rel = uk_path.relative_to(ROOT).as_posix()
        if not uk_path.is_file():
            errors.append(f"{rel}: file missing")
            continue
        if pattern.search(read_text(uk_path)) is None:
            errors.append(f"{rel}: missing Ukrainian banner with docs/en/ link")
    return errors


def main() -> int:
    errors: list[str] = []
    errors.extend(check_docs_directories())
    errors.extend(check_file_pairs())
    errors.extend(check_banner_only())

    if errors:
        print("Documentation parity check FAILED:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1

    print("OK: UK/EN documentation parity")
    return 0


if __name__ == "__main__":
    sys.exit(main())
