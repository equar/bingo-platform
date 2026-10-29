#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec python3 "$ROOT/bootstrap-github-actions-config.py" "${1:-$ROOT/github-actions.env}"
