#!/usr/bin/env bash
# Застарілий wrapper — використовуйте ./pingui.sh з кореня репозиторію.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec "${ROOT}/pingui.sh" "$@"
