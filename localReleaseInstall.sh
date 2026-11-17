#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$SCRIPT_DIR/bingo-player"
PRODUCTION_CONFIG="$SCRIPT_DIR/deployment/mobile/production.json"
ANDROID_APK="$APP_DIR/build/app/outputs/flutter-apk/app-release.apk"
IOS_APP="$APP_DIR/build/ios/iphonesimulator/Runner.app"
ANDROID_PACKAGE="com.bakaroo.bingo_app"
IOS_BUNDLE_ID="com.bakaroo.bingo.bingoApp"
TARGET_PLATFORM="${1:-both}"
API_HOST="api.bingo.bakaroo.com"
ANDROID_EMULATOR_DNS_SERVERS="${ANDROID_EMULATOR_DNS_SERVERS:-8.8.8.8,1.1.1.1}"

usage() {
  echo "Usage: $(basename "$0") {android|ios|both}" >&2
  echo "Android emulator DNS fallback: ANDROID_EMULATOR_DNS_SERVERS (default: 8.8.8.8,1.1.1.1)" >&2
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "ERROR: Required command '$1' is not available on PATH." >&2
    exit 1
  fi
}

android_api_dns_fails() {
  local serial="$1"
  local output
  output="$(adb -s "$serial" shell ping -c 1 -W 2 "$API_HOST" </dev/null 2>&1 || true)"
  grep -Eiq 'unknown host|name or service not known|temporary failure in name resolution|bad address' <<<"$output"
}

emulator_binary() {
  local sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
  if [[ -x "$sdk/emulator/emulator" ]]; then
    printf '%s\n' "$sdk/emulator/emulator"
  elif command -v emulator >/dev/null 2>&1; then
    command -v emulator
  else
    return 1
  fi
}

refresh_emulator_dns() {
  local serial="$1"
  local avd_name port emulator_bin state
  avd_name="$(adb -s "$serial" shell getprop ro.boot.qemu.avd_name </dev/null 2>/dev/null | tr -d '\r')"
  port="${serial#emulator-}"
  if [[ -z "$avd_name" || ! "$port" =~ ^[0-9]+$ ]]; then
    echo "ERROR: Cannot safely identify Android emulator $serial for a DNS refresh." >&2
    return 1
  fi
  if ! emulator_bin="$(emulator_binary)"; then
    echo "ERROR: Android emulator DNS lookup failed on $serial and the emulator executable was not found." >&2
    return 1
  fi

  echo "Android emulator $serial cannot resolve $API_HOST; restarting $avd_name with DNS servers $ANDROID_EMULATOR_DNS_SERVERS..."
  adb -s "$serial" emu kill >/dev/null
  for _ in {1..30}; do
    state="$(adb -s "$serial" get-state 2>/dev/null || true)"
    [[ "$state" != "device" ]] && break
    sleep 1
  done
  state="$(adb -s "$serial" get-state 2>/dev/null || true)"
  if [[ "$state" == "device" ]]; then
    echo "ERROR: Android emulator $serial did not stop; refusing to start a second instance." >&2
    return 1
  fi

  "$emulator_bin" -avd "$avd_name" -port "$port" \
    -dns-server "$ANDROID_EMULATOR_DNS_SERVERS" -no-snapshot-load \
    </dev/null >/dev/null 2>&1 &

  for _ in {1..120}; do
    state="$(adb -s "$serial" shell getprop sys.boot_completed </dev/null 2>/dev/null | tr -d '\r' || true)"
    [[ "$state" == "1" ]] && break
    sleep 2
  done
  state="$(adb -s "$serial" shell getprop sys.boot_completed </dev/null 2>/dev/null | tr -d '\r' || true)"
  if [[ "$state" != "1" ]]; then
    echo "ERROR: Android emulator $serial did not finish booting after the DNS refresh." >&2
    return 1
  fi
  if android_api_dns_fails "$serial"; then
    echo "ERROR: Android emulator $serial still cannot resolve $API_HOST. This network may block public DNS; configure Android Private DNS or the network's DNS resolver." >&2
    return 1
  fi
}

if (( $# > 1 )); then
  usage
  exit 64
fi

case "$TARGET_PLATFORM" in
  android)
    install_android=true
    install_ios=false
    ;;
  ios)
    install_android=false
    install_ios=true
    ;;
  both)
    install_android=true
    install_ios=true
    ;;
  *)
    usage
    exit 64
    ;;
esac

require_command flutter
if [[ "$install_android" == true ]]; then
  require_command adb
fi
if [[ "$install_ios" == true ]]; then
  require_command xcrun
fi

if [[ ! -f "$PRODUCTION_CONFIG" ]]; then
  echo "ERROR: Production mobile configuration is missing: $PRODUCTION_CONFIG" >&2
  exit 1
fi

if ! grep -Fq '"API_BASE_URL": "https://api.bingo.bakaroo.com"' "$PRODUCTION_CONFIG"; then
  echo "ERROR: Expected the AWS production API URL in $PRODUCTION_CONFIG." >&2
  exit 1
fi

android_devices=()
android_device_types=()
android_unauthorized_devices=()
android_device_count=0
android_emulator_count=0
android_physical_device_count=0
if [[ "$install_android" == true ]]; then
  while IFS= read -r serial; do
    android_devices+=("$serial")
    ((android_device_count += 1))
    if [[ "$(adb -s "$serial" shell getprop ro.kernel.qemu </dev/null 2>/dev/null | tr -d '\r')" == "1" ]]; then
      android_device_types+=("emulator")
      ((android_emulator_count += 1))
    else
      android_device_types+=("physical device")
      ((android_physical_device_count += 1))
    fi
  done < <(adb devices | awk '$2 == "device" { print $1 }')

  while IFS= read -r serial; do
    android_unauthorized_devices+=("$serial")
  done < <(adb devices | awk '$2 == "unauthorized" { print $1 }')
fi

ios_devices=()
ios_device_count=0
if [[ "$install_ios" == true ]]; then
  while IFS= read -r identifier; do
    ios_devices+=("$identifier")
    ((ios_device_count += 1))
  done < <(
    xcrun simctl list devices booted |
      sed -nE 's/.*\(([0-9A-Fa-f-]{36})\) \(Booted\).*/\1/p'
  )
fi

if [[ "$install_android" == true && $android_device_count == 0 ]]; then
  echo "ERROR: No booted Android emulators or attached physical Android devices were found." >&2
  exit 1
fi
if [[ "$install_android" == true && ${#android_unauthorized_devices[@]} -gt 0 ]]; then
  echo "ERROR: Android device(s) require USB debugging authorization: ${android_unauthorized_devices[*]}. Unlock the phone and accept its USB debugging prompt; no installation was attempted." >&2
  exit 1
fi
if [[ "$install_ios" == true && $ios_device_count == 0 ]]; then
  echo "ERROR: No booted iOS simulators were found." >&2
  exit 1
fi

echo "Installing for $TARGET_PLATFORM: discovered $android_emulator_count booted Android emulator(s), $android_physical_device_count attached physical Android device(s), and $ios_device_count booted iOS simulator(s)."
if [[ "$install_android" == true ]]; then
  for index in "${!android_devices[@]}"; do
    echo "  Android ${android_device_types[$index]}: ${android_devices[$index]}"
    serial="${android_devices[$index]}"
    if android_api_dns_fails "$serial"; then
      if [[ "${android_device_types[$index]}" == "emulator" ]]; then
        refresh_emulator_dns "$serial"
      else
        echo "ERROR: Physical Android device $serial cannot resolve $API_HOST. Check its current Wi-Fi DNS or configure Android Private DNS." >&2
        exit 1
      fi
    fi
  done
fi
if [[ "$install_ios" == true ]]; then
  for identifier in "${ios_devices[@]}"; do
    echo "  iOS: $identifier"
  done
fi

cd "$APP_DIR"
failures=()
installed_android=0
installed_ios=0

if [[ "$install_android" == true ]]; then
  echo "Building Android release APK with https://api.bingo.bakaroo.com..."
  flutter build apk --release \
    --dart-define-from-file="$PRODUCTION_CONFIG" \
    --dart-define=BINGO_PLAYER_PURCHASES_ENABLED=true

  for index in "${!android_devices[@]}"; do
    serial="${android_devices[$index]}"
    device_type="${android_device_types[$index]}"
    echo "Installing Android release on $device_type $serial..."
    if install_output="$(adb -s "$serial" install -r "$ANDROID_APK" 2>&1)"; then
      printf '%s\n' "$install_output"
    elif grep -Fq 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' <<<"$install_output"; then
      printf '%s\n' "$install_output" >&2
      if [[ "$device_type" == "physical device" ]]; then
        failures+=("Android $serial: update blocked by incompatible signature; existing app was left installed")
        continue
      fi
      echo "Replacing incompatible prior signature on $serial for $ANDROID_PACKAGE..." >&2
      if ! adb -s "$serial" uninstall "$ANDROID_PACKAGE" ||
        ! adb -s "$serial" install "$ANDROID_APK"; then
        failures+=("Android $serial: replacement installation failed")
        continue
      fi
    else
      printf '%s\n' "$install_output" >&2
      failures+=("Android $serial: installation failed")
      continue
    fi
    if [[ "$device_type" != "physical device" ]] &&
      ! adb -s "$serial" shell am start -n "$ANDROID_PACKAGE/$ANDROID_PACKAGE.MainActivity" >/dev/null; then
      failures+=("Android $serial: app launch failed")
      continue
    fi
    if ! adb -s "$serial" shell dumpsys package "$ANDROID_PACKAGE" |
      grep -E 'versionCode=[0-9]+' >/dev/null; then
      failures+=("Android $serial: installed package verification failed")
      continue
    fi
    adb -s "$serial" shell dumpsys package "$ANDROID_PACKAGE" |
      grep -E 'versionCode=|versionName=' |
      head -2
    ((installed_android += 1))
  done
fi

if [[ "$install_ios" == true ]]; then
  echo "Building iOS simulator app with https://api.bingo.bakaroo.com..."
  flutter build ios --simulator --debug \
    --dart-define-from-file="$PRODUCTION_CONFIG" \
    --dart-define=BINGO_PLAYER_PURCHASES_ENABLED=true

  for identifier in "${ios_devices[@]}"; do
    echo "Installing iOS simulator app on $identifier..."
    if ! xcrun simctl install "$identifier" "$IOS_APP"; then
      failures+=("iOS $identifier: installation failed")
      continue
    fi
    if ! xcrun simctl launch "$identifier" "$IOS_BUNDLE_ID"; then
      failures+=("iOS $identifier: app launch failed")
      continue
    fi
    if ! xcrun simctl get_app_container "$identifier" "$IOS_BUNDLE_ID" app >/dev/null; then
      failures+=("iOS $identifier: installed app verification failed")
      continue
    fi
    ((installed_ios += 1))
  done
fi

echo "Installed production-configured player builds on $installed_android Android device(s) and $installed_ios iOS simulator(s)."
if (( ${#failures[@]} > 0 )); then
  printf 'ERROR: Installation failed on %d device(s):\n' "${#failures[@]}" >&2
  printf '  - %s\n' "${failures[@]}" >&2
  exit 1
fi
