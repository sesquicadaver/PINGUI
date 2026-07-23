#!/usr/bin/env bash
# PINGUI Java — launcher (Linux, macOS). Windows: pingui-java.bat
# GUI за замовчуванням від’єднується від термінала; CLI-режими лишаються foreground.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

# Major Java version from `java -version` (handles 1.8.0_xxx and 21/25 style).
java_major_version() {
  local java_cmd="${1:-java}"
  "$java_cmd" -version 2>&1 | awk -F '[".-]' '
    /version/ {
      if ($2 == "1") { print $3; exit }
      print $2
      exit
    }'
}

find_jdk21_home() {
  local candidate major
  for candidate in \
    "${PINGUI_JAVA_HOME:-}" \
    "${JAVA_HOME:-}" \
    /usr/lib/jvm/java-21-openjdk-amd64 \
    /usr/lib/jvm/java-21-openjdk \
    /usr/lib/jvm/java-1.21.0-openjdk-amd64 \
    /usr/lib/jvm/temurin-21-jdk-amd64 \
    /usr/lib/jvm/jdk-21* \
    "$HOME/.sdkman/candidates/java/21."* \
    "$HOME/.sdkman/candidates/java/temurin-21."* \
    ; do
    [[ -n "$candidate" && -x "$candidate/bin/java" ]] || continue
    major="$(java_major_version "$candidate/bin/java")"
    if [[ "$major" == "21" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

resolve_java_home() {
  local found major

  if found="$(find_jdk21_home)"; then
    export JAVA_HOME="$found"
    export PATH="$JAVA_HOME/bin:$PATH"
    return 0
  fi

  if ! command -v java >/dev/null 2>&1; then
    echo "[pingui-java] ПОМИЛКА: Java не знайдено. Потрібен JDK 21." >&2
    exit 1
  fi

  major="$(java_major_version java)"
  if [[ "$major" == "21" ]]; then
    return 0
  fi

  echo "[pingui-java] ПОМИЛКА: для збірки потрібен JDK 21, знайдено Java ${major}." >&2
  echo "[pingui-java] Gradle 8.10 не запускається під Java 25 (типова помилка: «What went wrong: 25.0.3»)." >&2
  echo "[pingui-java] Встановіть OpenJDK 21, наприклад: sudo apt install openjdk-21-jdk" >&2
  echo "[pingui-java] Або: export PINGUI_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64" >&2
  exit 1
}

# True when args require an attached console (daemon / export / help / …).
needs_console() {
  local a
  for a in "$@"; do
    case "$a" in
      --daemon | --stop | --status | --help | -h | --export-report | --export-schedule | --telemetry-dump | --telemetry-retention)
        return 0
        ;;
    esac
  done
  return 1
}

# Strip launcher-only flags; print remaining args as lines (safe for spaces via NUL — we use array rebuild).
strip_launcher_flags() {
  local -a out=()
  local a foreground=0
  for a in "$@"; do
    case "$a" in
      --foreground | --fg)
        foreground=1
        ;;
      *)
        out+=("$a")
        ;;
    esac
  done
  if [[ "$foreground" -eq 1 ]]; then
    printf 'FOREGROUND\n'
  else
    printf 'DETACH\n'
  fi
  printf '%s\n' "${out[@]+"${out[@]}"}"
}

gui_log_path() {
  local base
  if [[ -n "${PINGUI_GUI_LOG:-}" ]]; then
    printf '%s\n' "$PINGUI_GUI_LOG"
    return
  fi
  base="${XDG_CACHE_HOME:-$HOME/.cache}/pingui"
  mkdir -p "$base"
  printf '%s\n' "$base/gui.log"
}

ensure_install_dist() {
  ./gradlew installDist -q
}

run_app_foreground() {
  ensure_install_dist
  exec "$ROOT/build/install/pingui-java/bin/pingui-java" "$@"
}

run_gui_detached() {
  local log pid
  ensure_install_dist
  log="$(gui_log_path)"
  mkdir -p "$(dirname "$log")"
  nohup "$ROOT/build/install/pingui-java/bin/pingui-java" "$@" >>"$log" 2>&1 &
  pid=$!
  disown "$pid" 2>/dev/null || true
  echo "[pingui-java] GUI запущено у фоні (PID $pid)."
  echo "[pingui-java] Лог: $log"
  echo "[pingui-java] Передній план (дебаг): ./pingui-java.sh --foreground …"
}

run_gui_or_cli() {
  local mode
  local -a app_args=()
  local line first=1
  while IFS= read -r line; do
    if [[ "$first" -eq 1 ]]; then
      mode="$line"
      first=0
      continue
    fi
    app_args+=("$line")
  done < <(strip_launcher_flags "$@")

  if needs_console "${app_args[@]+"${app_args[@]}"}"; then
    run_app_foreground "${app_args[@]+"${app_args[@]}"}"
  fi
  if [[ "$mode" == "FOREGROUND" ]]; then
    run_app_foreground "${app_args[@]+"${app_args[@]}"}"
  fi
  run_gui_detached "${app_args[@]+"${app_args[@]}"}"
}

resolve_java_home

# GTK/JavaFX: harmless when D-Bus AT-SPI is missing (SSH, Docker, sudo). Override: NO_AT_BRIDGE=0
export NO_AT_BRIDGE="${NO_AT_BRIDGE:-1}"

CMD="${1:-run}"
shift || true

case "$CMD" in
  --help | -h | help)
    ensure_install_dist
    exec "$ROOT/build/install/pingui-java/bin/pingui-java" --help
    ;;
  --build | build)
    exec ./gradlew build "$@"
    ;;
  --package | package)
    exec ./gradlew jpackage "$@"
    ;;
  --foreground | --fg)
    run_gui_or_cli --foreground "$@"
    ;;
  --run | run | --)
    run_gui_or_cli "$@"
    ;;
  *)
    # Pass first token + rest as app args (e.g. ./pingui-java.sh --daemon …)
    run_gui_or_cli "$CMD" "$@"
    ;;
esac
