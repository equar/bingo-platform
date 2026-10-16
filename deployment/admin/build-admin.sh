#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
source "$REPO_ROOT/deployment/lib/common.sh"

VITE_API_BASE_URL_VALUE="${VITE_API_BASE_URL:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --api-base-url)
      VITE_API_BASE_URL_VALUE="$2"
      shift
      ;;
    --help|-h)
      cat <<'EOF'
Usage: build-admin.sh --api-base-url https://bingo.example.com

Environment variables:
  VITE_API_BASE_URL  Public origin used by the Vite build.
EOF
      exit 0
      ;;
    *)
      die "Unknown argument: $1"
      ;;
  esac
  shift
done

[[ -n "$VITE_API_BASE_URL_VALUE" ]] || die "VITE_API_BASE_URL is required"

require_command npm

cd "$REPO_ROOT/bingo-admin"

if [[ -f package-lock.json ]]; then
  npm ci
else
  npm install
fi

VITE_API_BASE_URL="$VITE_API_BASE_URL_VALUE" npm run build

[[ -f dist/index.html ]] || die "Admin build did not produce dist/index.html"

printf '%s\n' "$REPO_ROOT/bingo-admin/dist"
