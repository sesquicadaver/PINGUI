#!/usr/bin/env bash
# Bootstrap venv and run PINGUI (Linux only, venv required).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ ! -d .venv ]]; then
  # --copies: real python binary in venv (required for setcap / cap_net_raw).
  python3 -m venv --copies .venv
  .venv/bin/pip install -U pip
  .venv/bin/pip install -e ".[dev]"
fi

export PYTHONPATH="${ROOT}/src${PYTHONPATH:+:$PYTHONPATH}"
exec .venv/bin/python -m pingui "$@"
