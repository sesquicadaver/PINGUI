#!/usr/bin/env python3
"""Verify UK/EN documentation parity and user-facing stub-locale matrix (P25)."""

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
# Stub locales (es/it/…): Language banner + UK + EN links.
STUB_BANNER = re.compile(
    r"^> \*\*Language:\*\* .+ · \[Українська\]\((?P<link_uk>[^)]+)\) · \[English\]\((?P<link_en>[^)]+)\)\s*$",
    re.MULTILINE,
)

STUB_LOCALES = ("es", "it", "pl", "cs", "lv", "lt", "et")

# Donation addresses must appear in every product README (UK/EN + locale stubs).
DONATION_MARKERS = (
    "0xfa9821efd142228d53e1418fe335bb1cd8ff3c39",
    "TNnhueeGqujf6AAUhcgissoEkL7tdzmqQv",
)

# Only end-user docs are required under docs/<lang>/ (not ADR/CHECKLIST/dev guides).
USER_FACING_DOCS = frozenset({"USER_GUIDE.md", "HOWTO.md"})

# Fully translated user docs may omit the "Translation pending" stub notice.
USER_DOCS_ALLOW_FULL = frozenset({"USER_GUIDE.md", "HOWTO.md"})

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
    """UK ↔ EN: full twin matrix for all docs/*.md (developer docs included)."""
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


def check_stub_locales() -> list[str]:
    """Stub locales carry only user-facing docs (USER_GUIDE, HOWTO)."""
    errors: list[str] = []
    for code in STUB_LOCALES:
        locale_dir = ROOT / "docs" / code
        if not locale_dir.is_dir():
            errors.append(f"docs/{code}/: missing user-locale directory")
            continue
        locale_files = list_markdown(locale_dir)
        for name in sorted(USER_FACING_DOCS - locale_files):
            errors.append(f"docs/{code}/{name}: missing user-facing doc")
        for name in sorted(locale_files - USER_FACING_DOCS):
            errors.append(
                f"docs/{code}/{name}: unexpected file "
                f"(stub locales are user-facing only: {', '.join(sorted(USER_FACING_DOCS))})",
            )
        for name in sorted(USER_FACING_DOCS & locale_files):
            path = locale_dir / name
            text = read_text(path)
            match = STUB_BANNER.search(text)
            if match is None:
                errors.append(f"docs/{code}/{name}: missing Language banner (UK+EN links)")
                continue
            if match.group("link_uk") != f"../{name}":
                errors.append(
                    f"docs/{code}/{name}: UK link {match.group('link_uk')!r}, expected '../{name}'",
                )
            if match.group("link_en") != f"../en/{name}":
                errors.append(
                    f"docs/{code}/{name}: EN link {match.group('link_en')!r}, expected '../en/{name}'",
                )
            if name not in USER_DOCS_ALLOW_FULL and "Translation pending" not in text:
                errors.append(f"docs/{code}/{name}: stub missing 'Translation pending' note")
    return errors


def check_donation_section(path: Path) -> list[str]:
    """Require intact USDT donation addresses in a product README."""
    errors: list[str] = []
    rel = path.relative_to(ROOT).as_posix() if path.is_absolute() else path.as_posix()
    if not path.is_file():
        return [f"{rel}: file missing (donation section)"]
    text = read_text(path)
    for marker in DONATION_MARKERS:
        if marker not in text:
            errors.append(f"{rel}: missing donation address {marker}")
    if "USDT" not in text:
        errors.append(f"{rel}: missing USDT donation section")
    return errors


def check_root_readme_stubs() -> list[str]:
    """Product README.<lang>.md stubs for user locales (not developer docs)."""
    errors: list[str] = []
    for path in (ROOT / "README.md", ROOT / "README.en.md"):
        errors.extend(check_donation_section(path))
    for code in STUB_LOCALES:
        path = ROOT / f"README.{code}.md"
        rel = path.name
        if not path.is_file():
            errors.append(f"{rel}: missing user-locale README stub")
            continue
        text = read_text(path)
        match = STUB_BANNER.search(text)
        if match is None:
            errors.append(f"{rel}: missing Language banner (UK+EN links)")
            continue
        if match.group("link_uk") != "README.md":
            errors.append(f"{rel}: UK link {match.group('link_uk')!r}, expected 'README.md'")
        if match.group("link_en") != "README.en.md":
            errors.append(f"{rel}: EN link {match.group('link_en')!r}, expected 'README.en.md'")
        if "Translation pending" not in text:
            errors.append(f"{rel}: stub missing 'Translation pending' note")
        if f"docs/{code}/USER_GUIDE.md" not in text:
            errors.append(f"{rel}: should link to docs/{code}/USER_GUIDE.md")
        errors.extend(check_donation_section(path))
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
    errors.extend(check_stub_locales())
    errors.extend(check_root_readme_stubs())

    if errors:
        print("Documentation parity check FAILED:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1

    print("OK: UK/EN + user-facing locale documentation parity")
    return 0


if __name__ == "__main__":
    sys.exit(main())
