#!/usr/bin/env bash
# CI pipeline: venv → ruff → mypy → pytest (no network tests by default).
# Еквівалент як ./pingui.sh --deploy (без apt/setcap).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 -m venv .venv
.venv/bin/pip install -U pip
.venv/bin/pip install -e ".[dev,gui]"

export QT_QPA_PLATFORM="${QT_QPA_PLATFORM:-offscreen}"

.venv/bin/ruff check src tests
.venv/bin/mypy src/pingui
.venv/bin/pytest tests -m "not network" --cov=pingui --cov-report=term-missing --cov-fail-under=80
python scripts/check_imports.py
python scripts/check_doc_parity.py
