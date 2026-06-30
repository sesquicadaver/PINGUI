#!/usr/bin/env bash
# Layer dependency guard (B-063): lower layers must not import ui.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/src/main/java/io/pingui"
VIOLATIONS=0

check_no_ui_imports() {
  local layer="$1"
  local dir="$SRC/$layer"
  if [[ ! -d "$dir" ]]; then
    return
  fi
  if rg -q 'import io\.pingui\.ui\.' "$dir" 2>/dev/null; then
    echo "ERROR: io.pingui.$layer must not import io.pingui.ui"
    rg 'import io\.pingui\.ui\.' "$dir" || true
    VIOLATIONS=$((VIOLATIONS + 1))
  fi
}

for layer in config monitor probe model geoip platform; do
  check_no_ui_imports "$layer"
done

if [[ "$VIOLATIONS" -gt 0 ]]; then
  echo "layerCheck failed: $VIOLATIONS violation(s)"
  exit 1
fi

echo "layerCheck OK: no forbidden ui imports in lower layers"
