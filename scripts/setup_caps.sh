#!/usr/bin/env bash
# Grant cap_net_raw to the venv Python used by PINGUI.
#
# setcap cannot target symlinks. Default venv uses symlinks to /usr/bin/python3,
# so this script prefers a --copies venv (real binary in .venv/bin/python3.*).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ ! -d .venv ]]; then
  echo "ERROR: .venv not found. Run ./scripts/run_dev.sh first." >&2
  exit 1
fi

pick_cap_target() {
  local candidate
  for candidate in .venv/bin/python3.12 .venv/bin/python3.11 .venv/bin/python3; do
    if [[ -f "$candidate" ]] && [[ ! -L "$candidate" ]]; then
      echo "$ROOT/$candidate"
      return 0
    fi
  done
  readlink -f "${ROOT}/.venv/bin/python3"
}

TARGET="$(pick_cap_target)"
VENV_LINK="${ROOT}/.venv/bin/python3"

if [[ -L "$VENV_LINK" ]] && [[ "$TARGET" == /usr/* ]]; then
  cat >&2 <<EOF
ERROR: .venv/bin/python3 is a symlink to system Python ($TARGET).
setcap does not work on symlinks.

Recommended (capability only on project venv copy):
  rm -rf .venv
  python3 -m venv --copies .venv
  .venv/bin/pip install -U pip
  .venv/bin/pip install -e ".[dev]"
  sudo setcap cap_net_raw+ep .venv/bin/python3.12

Alternative (capability on system Python — affects all uses of $TARGET):
  sudo setcap cap_net_raw+ep $TARGET
EOF
  exit 1
fi

echo "Applying cap_net_raw+ep to: $TARGET"
sudo setcap cap_net_raw+ep "$TARGET"
getcap "$TARGET"
echo "OK. Run: ./scripts/check_caps.sh"
