#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PLATFORM="${1:-android}"
APP="${2:-all}"
case "$PLATFORM" in android|ios) ;; *) echo 'Usage: build-packages.sh android|ios [all|bingo-app|bingo-agent]' >&2; exit 1;; esac
case "$APP" in all) apps=(bingo-app bingo-agent);; bingo-app|bingo-agent) apps=("$APP");; *) echo 'Unknown app' >&2; exit 1;; esac
output="$ROOT/deployment/mobile/artifacts"
mkdir -p "$output"
for app in "${apps[@]}"; do
  cd "$ROOT/$app"
  flutter pub get
  flutter gen-l10n
  if [[ "$PLATFORM" == android ]]; then
    python3 "$ROOT/deployment/mobile/prepare-android-signing.py" "$app"
    flutter build apk --release --target=lib/main.dart --dart-define-from-file="$ROOT/deployment/mobile/production.json"
    cp build/app/outputs/flutter-apk/app-release.apk "$output/$app-android.apk"
    (cd "$output" && shasum -a 256 "$app-android.apk" > "$app-android.apk.sha256")
  else
    export_options="$ROOT/deployment/mobile/signing/$app-ExportOptions.plist"
    [[ -f "$export_options" ]] || { echo "Missing Ad Hoc export/signing configuration: $export_options" >&2; exit 1; }
    flutter build ipa --release --target=lib/main.dart --export-options-plist="$export_options" --dart-define-from-file="$ROOT/deployment/mobile/production.json"
    ipa="$(find build/ios/ipa -name '*.ipa' -print -quit)"
    [[ -n "$ipa" ]] || { echo 'No signed IPA produced' >&2; exit 1; }
    cp "$ipa" "$output/$app-ios.ipa"
    (cd "$output" && shasum -a 256 "$app-ios.ipa" > "$app-ios.ipa.sha256")
  fi
done
