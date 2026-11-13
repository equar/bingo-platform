#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$SCRIPT_DIR/bingo-app"
PRODUCTION_CONFIG="$SCRIPT_DIR/deployment/mobile/production.json"
ANDROID_APK="$APP_DIR/build/app/outputs/flutter-apk/app-release.apk"
IOS_APP="$APP_DIR/build/ios/iphonesimulator/Runner.app"
ANDROID_PACKAGE="com.bakaroo.bingo_app"
IOS_BUNDLE_ID="com.bakaroo.bingo.bingoApp"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "ERROR: Required command '$1' is not available on PATH." >&2
    exit 1
  fi
}

require_command flutter
require_command adb

if [[ ! -f "$PRODUCTION_CONFIG" ]]; then
  echo "ERROR: Production mobile configuration is missing: $PRODUCTION_CONFIG" >&2
  exit 1
fi

if ! grep -Fq '"API_BASE_URL": "https://api.bingo.bakaroo.com"' "$PRODUCTION_CONFIG"; then
  echo "ERROR: Expected the AWS production API URL in $PRODUCTION_CONFIG." >&2
  exit 1
fi

android_devices=()
while IFS= read -r serial; do
  if [[ "$(adb -s "$serial" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')" == "1" ]]; then
    android_devices+=("$serial")
  fi
done < <(adb devices | awk '$2 == "device" { print $1 }')

ios_devices=()
if command -v xcrun >/dev/null 2>&1; then
  while IFS= read -r identifier; do
    ios_devices+=("$identifier")
  done < <(
    xcrun simctl list devices booted |
      sed -nE 's/.*\(([0-9A-Fa-f-]{36})\) \(Booted\).*/\1/p'
  )
fi

if (( ${#android_devices[@]} == 0 && ${#ios_devices[@]} == 0 )); then
  echo "ERROR: No booted Android emulators or iOS simulators were found." >&2
  exit 1
fi

cd "$APP_DIR"

if (( ${#android_devices[@]} > 0 )); then
  echo "Building Android release APK with https://api.bingo.bakaroo.com..."
  flutter build apk --release \
    --dart-define-from-file="$PRODUCTION_CONFIG" \
    --dart-define=BINGO_PLAYER_PURCHASES_ENABLED=true

  for serial in "${android_devices[@]}"; do
    echo "Installing Android release on $serial..."
    if install_output="$(adb -s "$serial" install -r "$ANDROID_APK" 2>&1)"; then
      printf '%s\n' "$install_output"
    elif grep -Fq 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' <<<"$install_output"; then
      printf '%s\n' "$install_output" >&2
      echo "Replacing incompatible prior signature on $serial for $ANDROID_PACKAGE..." >&2
      adb -s "$serial" uninstall "$ANDROID_PACKAGE"
      adb -s "$serial" install "$ANDROID_APK"
    else
      printf '%s\n' "$install_output" >&2
      echo "ERROR: Android installation failed on $serial." >&2
      exit 1
    fi
    adb -s "$serial" shell am start -n "$ANDROID_PACKAGE/$ANDROID_PACKAGE.MainActivity" >/dev/null
    adb -s "$serial" shell dumpsys package "$ANDROID_PACKAGE" |
      grep -E 'versionCode=|versionName=' |
      head -2
  done
fi

if (( ${#ios_devices[@]} > 0 )); then
  echo "Building iOS simulator app with https://api.bingo.bakaroo.com..."
  flutter build ios --simulator --debug \
    --dart-define-from-file="$PRODUCTION_CONFIG" \
    --dart-define=BINGO_PLAYER_PURCHASES_ENABLED=true

  for identifier in "${ios_devices[@]}"; do
    echo "Installing iOS simulator app on $identifier..."
    xcrun simctl install "$identifier" "$IOS_APP"
    xcrun simctl launch "$identifier" "$IOS_BUNDLE_ID"
    xcrun simctl get_app_container "$identifier" "$IOS_BUNDLE_ID" app >/dev/null
  done
fi

echo "Installed production-configured player builds on ${#android_devices[@]} Android emulator(s) and ${#ios_devices[@]} iOS simulator(s)."
