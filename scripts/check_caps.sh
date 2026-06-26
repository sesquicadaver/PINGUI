#!/usr/bin/env bash
# Verify raw ICMP capability before starting PINGUI.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PYTHON="${ROOT}/.venv/bin/python"
if [[ ! -x "$PYTHON" ]]; then
  echo "ERROR: .venv not found. Run scripts/run_dev.sh first." >&2
  exit 1
fi

"$PYTHON" -c "
from pingui.icmp.raw_socket import check_raw_icmp_permission
check_raw_icmp_permission()
print('OK: raw ICMP permission available')
"
