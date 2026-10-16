#!/usr/bin/env bash
set -euo pipefail

URL="${1:-${ADMIN_HEALTH_URL:-https://bingo.example.com/}}"

curl -fsS "$URL" >/dev/null
