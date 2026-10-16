#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
source "$REPO_ROOT/deployment/lib/common.sh"

require_command mvn

cd "$REPO_ROOT/bingo-backend"
mvn -DskipTests clean package

artifact_path="$(find target -maxdepth 1 -type f -name '*.jar' ! -name 'original-*.jar' | sort | tail -n 1)"
[[ -n "$artifact_path" ]] || die "Backend jar was not produced"
[[ -f "$artifact_path" ]] || die "Backend jar was not found: $artifact_path"

printf '%s\n' "$artifact_path"
