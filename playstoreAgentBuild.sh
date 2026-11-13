#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$SCRIPT_DIR/bingo-agent"
API_BASE_URL='https://api.bingo.bakaroo.com'
BUILD_NUMBER="${1:-}"

if [[ ! "$BUILD_NUMBER" =~ ^[1-9][0-9]*$ ]]; then
  echo "Usage: $0 <build-number>" >&2
  echo 'Build number must be a positive integer greater than the previous Play release.' >&2
  exit 2
fi

if [[ ! -f "$APP_DIR/android/key.properties" ]]; then
  echo 'ERROR: bingo-agent/android/key.properties is missing.' >&2
  echo 'Configure it with the upload keystore registered for this Play Console app.' >&2
  exit 1
fi

SOURCE_BUILD_NUMBER="$(sed -n 's/^version: .*+\([0-9][0-9]*\)$/\1/p' "$APP_DIR/pubspec.yaml")"
if [[ -n "$SOURCE_BUILD_NUMBER" ]] && (( BUILD_NUMBER <= SOURCE_BUILD_NUMBER )); then
  echo "ERROR: Build number must be greater than pubspec.yaml build number $SOURCE_BUILD_NUMBER." >&2
  exit 1
fi

bash "$SCRIPT_DIR/bingo-player/tooling/validate_release_api_base_url.sh" "$API_BASE_URL" >/dev/null

cd "$APP_DIR"
flutter pub get
flutter gen-l10n
flutter build appbundle \
  --release \
  --build-number="$BUILD_NUMBER" \
  --target=lib/main.dart \
  --dart-define=API_BASE_URL="$API_BASE_URL"

ARTIFACT_DIR="$SCRIPT_DIR/deployment/mobile/artifacts"
mkdir -p "$ARTIFACT_DIR"
ARTIFACT="$ARTIFACT_DIR/bingo-agent-playstore-${BUILD_NUMBER}.aab"
cp build/app/outputs/bundle/release/app-release.aab "$ARTIFACT"
(cd "$ARTIFACT_DIR" && shasum -a 256 "$(basename "$ARTIFACT")" > "$(basename "$ARTIFACT").sha256")

echo "Built Agent App Bundle for $API_BASE_URL"
echo "Artifact: $ARTIFACT"
echo "Checksum: $ARTIFACT.sha256"
echo 'Before upload, confirm this version code exceeds the latest Play Console version and the configured key is the registered Play upload key.'
