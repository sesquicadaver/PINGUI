#!/usr/bin/env bash
# PINGUI Java — cross-platform launcher (Linux, macOS, Windows/WSL).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

if ! command -v java >/dev/null 2>&1; then
  echo "[pingui-java] ПОМИЛКА: Java 21+ не знайдено. Встановіть JDK 21." >&2
  exit 1
fi

JAVA_VER="$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')"
if [[ "${JAVA_VER:-0}" -lt 21 ]]; then
  echo "[pingui-java] ПОМИЛКА: потрібен Java 21+, знайдено: ${JAVA_VER:-unknown}" >&2
  exit 1
fi

CMD="${1:-run}"
shift || true

case "$CMD" in
  --help|-h|help)
    exec ./gradlew run --args="--help" -q
    ;;
  --test|test)
    exec ./gradlew test "$@"
    ;;
  --build|build)
    exec ./gradlew build "$@"
    ;;
  --package|package)
    exec ./gradlew jpackage "$@"
    ;;
  --run|run)
    if [[ $# -gt 0 ]]; then
      exec ./gradlew run --args="$*" -q
    else
      exec ./gradlew run -q
    fi
    ;;
  *)
    exec ./gradlew run --args="$CMD $*" -q
    ;;
esac
