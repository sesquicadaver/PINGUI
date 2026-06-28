#!/usr/bin/env bash
# PINGUI Java — launcher (Linux, macOS). Windows: pingui-java.bat
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
  local candidate java_bin major
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

resolve_java_home

CMD="${1:-run}"
shift || true

case "$CMD" in
  --help|-h|help)
    exec ./gradlew run --args="--help" -q
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
