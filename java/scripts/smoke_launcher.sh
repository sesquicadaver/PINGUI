#!/usr/bin/env bash
# P26-004 — Linux/macOS launcher smoke matrix (quoting, detach, foreground, fail→log).
# Isolated fake install tree — does not overwrite the real installDist binary or start JavaFX.
set -euo pipefail

REPO_JAVA="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/pingui-launcher-smoke.XXXXXX")"
cleanup() {
  rm -rf "$TMP"
}
trap cleanup EXIT

ROOT="$TMP/java"
mkdir -p "$ROOT/build/install/pingui-java/bin"
cp "$REPO_JAVA/pingui-java.sh" "$ROOT/pingui-java.sh"
chmod +x "$ROOT/pingui-java.sh"

BIN="$ROOT/build/install/pingui-java/bin/pingui-java"
ARGS_FILE="$TMP/args.txt"
GUI_LOG="$TMP/gui.log"
STUB_LOG="$TMP/stub.log"

cat >"$BIN" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
: "${PINGUI_SMOKE_ARGS:?}"
: "${PINGUI_SMOKE_LOG:?}"
: >"$PINGUI_SMOKE_ARGS"
for a in "$@"; do
  printf '%s\n' "$a" >>"$PINGUI_SMOKE_ARGS"
done
echo "stub-ok argv=$#" >>"$PINGUI_SMOKE_LOG"
if [[ "${PINGUI_SMOKE_FAIL:-0}" == "1" ]]; then
  echo "stub-fail" >&2
  exit 1
fi
exit 0
STUB
chmod +x "$BIN"

export PINGUI_SKIP_INSTALL_DIST=1
export PINGUI_GUI_LOG="$GUI_LOG"
export PINGUI_SMOKE_ARGS="$ARGS_FILE"
export PINGUI_SMOKE_LOG="$STUB_LOG"
unset PINGUI_SMOKE_FAIL || true

fail() {
  echo "[smoke_launcher] FAIL: $*" >&2
  exit 1
}

assert_file_contains() {
  local file="$1" needle="$2"
  [[ -f "$file" ]] || fail "missing file $file"
  grep -F -q -- "$needle" "$file" || fail "expected '$needle' in $file (got: $(tr '\n' ' ' <"$file"))"
}

await_file() {
  local file="$1" timeout_s="${2:-5}"
  local i
  for ((i = 0; i < timeout_s * 10; i++)); do
    if [[ -s "$file" ]]; then
      return 0
    fi
    sleep 0.1
  done
  fail "timeout waiting for $file"
}

echo "[smoke_launcher] 1/5 quoting (spaces in paths) via detached GUI"
: >"$ARGS_FILE"
: >"$STUB_LOG"
: >"$GUI_LOG"
CFG="$TMP/path with spaces/hosts.yaml"
DB="$TMP/my db/session.db"
mkdir -p "$(dirname "$CFG")" "$(dirname "$DB")"
touch "$CFG"
out="$("$ROOT/pingui-java.sh" -- --config "$CFG" --session-db "$DB" 2>&1 || true)"
echo "$out" | grep -F -q "GUI запущено у фоні" || fail "detached banner missing: $out"
echo "$out" | grep -F -q "$GUI_LOG" || fail "log path not printed: $out"
await_file "$ARGS_FILE"
assert_file_contains "$ARGS_FILE" "--config"
assert_file_contains "$ARGS_FILE" "$CFG"
assert_file_contains "$ARGS_FILE" "--session-db"
assert_file_contains "$ARGS_FILE" "$DB"

echo "[smoke_launcher] 2/5 --foreground keeps console (exec stub)"
: >"$ARGS_FILE"
: >"$STUB_LOG"
"$ROOT/pingui-java.sh" --foreground -- --config "$CFG"
assert_file_contains "$ARGS_FILE" "$CFG"
assert_file_contains "$STUB_LOG" "stub-ok"

echo "[smoke_launcher] 3/5 CLI --help stays attached"
: >"$ARGS_FILE"
: >"$STUB_LOG"
"$ROOT/pingui-java.sh" --help
assert_file_contains "$ARGS_FILE" "--help"

echo "[smoke_launcher] 4/5 --daemon forces attached console"
: >"$ARGS_FILE"
: >"$STUB_LOG"
"$ROOT/pingui-java.sh" -- --daemon --config "$CFG"
assert_file_contains "$ARGS_FILE" "--daemon"
assert_file_contains "$ARGS_FILE" "$CFG"

echo "[smoke_launcher] 5/5 detached failure lands in GUI log"
: >"$ARGS_FILE"
: >"$STUB_LOG"
: >"$GUI_LOG"
export PINGUI_SMOKE_FAIL=1
out="$("$ROOT/pingui-java.sh" -- --config "$CFG" 2>&1 || true)"
unset PINGUI_SMOKE_FAIL
echo "$out" | grep -F -q "Лог:" || fail "log hint missing on fail path: $out"
await_file "$GUI_LOG"
assert_file_contains "$GUI_LOG" "stub-fail"

echo "[smoke_launcher] OK"
