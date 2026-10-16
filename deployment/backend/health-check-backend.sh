#!/usr/bin/env bash
set -euo pipefail

URL="${1:-${BACKEND_HEALTH_URL:-http://127.0.0.1:8081/actuator/health}}"

curl -fsS "$URL" | grep -q '"status":"UP"'
