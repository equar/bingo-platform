#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BACKEND_ARGS=()
ADMIN_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      BACKEND_ARGS+=(--dry-run)
      ADMIN_ARGS+=(--dry-run)
      ;;
    --backend-env-file|--server|--ssh-key|--backend-service-name|--remote-backend-root|--service-user)
      BACKEND_ARGS+=("$1" "$2")
      if [[ "$1" == "--server" || "$1" == "--ssh-key" ]]; then
        ADMIN_ARGS+=("$1" "$2")
      fi
      shift
      ;;
    --public-url|--remote-admin-root)
      ADMIN_ARGS+=("$1" "$2")
      shift
      ;;
    --help|-h)
      cat <<'EOF'
Usage: deploy-all.sh [--dry-run] [backend options] [admin options]

Backend options:
  --backend-env-file PATH
  --server USER@HOST
  --ssh-key PATH
  --remote-backend-root PATH
  --backend-service-name NAME
  --service-user NAME

Admin options:
  --public-url https://bingo.example.com
  --server USER@HOST
  --ssh-key PATH
  --remote-admin-root PATH
EOF
      exit 0
      ;;
    *)
      echo "ERROR: Unknown argument: $1" >&2
      exit 1
      ;;
  esac
  shift
done

"$SCRIPT_DIR/backend/deploy-backend.sh" "${BACKEND_ARGS[@]}"
"$SCRIPT_DIR/admin/deploy-admin.sh" "${ADMIN_ARGS[@]}"
