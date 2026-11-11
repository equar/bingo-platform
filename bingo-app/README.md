# Bingo Player

Flutter player app for the Bingo backend. Requires Flutter 3.41.0+ and Dart
3.11.0+.

## Local development

Install Flutter 3.41.0+ and Dart 3.11.0+. The repository launcher works from
any directory and starts the app with the correct API and live-draw URLs:

```powershell
.\scripts\Run-BingoLocal.ps1 -App player -Platform android
.\scripts\Run-BingoLocal.ps1 -App player -Platform chrome
.\scripts\Run-BingoLocal.ps1 -App player -Platform windows
```

For Android, the launcher selects a connected device or starts the only
configured emulator. Use `-DeviceId <id>` when you want a specific device.
Chrome uses port 5180. Windows desktop builds require Visual Studio 2022 Build
Tools with the **Desktop development with C++** workload and a Windows 10/11
SDK. Start MySQL and the backend `dev` profile first; the backend guide
explains its local `.env` setup.

The equivalent VS Code launch configurations are **Bingo Player — Android
emulator**, **Bingo Player — Chrome**, and **Bingo Player — Windows**. For
Android, select the emulator in the VS Code device picker. The app uses the
backend origin `http://10.0.2.2:8080` in the emulator and
`http://localhost:8080` in Chrome/Windows. REST routes are under `/api/v1`;
live draws use `ws://<backend-host>:8080/ws`. Chrome authentication is stored
for its origin; logout clears that app's saved session. Chrome's localhost
origin is separate from the Agent app's origin on port 5181.

Switching platforms starts a separate app instance and does not transfer an
in-progress game session. Development HTTP/WS URLs are debug-only; profile and
release builds require HTTPS/WSS and reject loopback addresses. Local player
accounts must be configured through the backend's documented development
account setup or created through its supported registration flow. Keep local
credentials in the ignored backend `.env`, never in launch files.

Useful checks:

```bash
flutter test
flutter analyze
```

## Device compatibility

The player release baseline is Android 7.0 (API 24) and iOS 15. iOS 15 still
supports iPhone 6s and first-generation iPhone SE hardware. The Android
SDK levels are pinned in `android/app/build.gradle.kts`; iOS deployment targets
are pinned in the Podfile and Xcode project so SDK upgrades cannot silently
drop older devices. Before each release, test the login, wallet, lobby, live
game reconnect, and app restart flows on:

- Android API 24 on a low-memory phone or emulator;
- a current Android device;
- iOS 15 on an iPhone 6s, first-generation iPhone SE, or simulator; and
- a current iPhone and iPad.

The app follows the device light/dark appearance and respects system text
scaling. On older or lower-end hardware, keep the same functional checks while
also exercising weak-network and background/foreground live-game recovery.

## Game and community behavior

Live draws use STOMP at `/ws` with the API JWT in the `CONNECT` frame and
subscribe to `/topic/session/{sessionId}/draws`. The backend is authoritative
for cards, called numbers, ownership, claims, and persisted session state.
See the [backend API/WebSocket guide](../bingo-backend/README.md) and
[community-feature rules](../docs/community-features.md).

## Functional E2E

Use the [guarded real E2E runbook](../docs/qa/platform-e2e-runbook.md) and
[current scenario matrix](../docs/qa/platform-e2e-scenario-matrix.md). The
legacy `integration_test/local_e2e_test.dart` and
`scripts/run-local-e2e.ps1` do not enforce current database isolation; do not
run them against development, persistent QA, shared, or production databases
or report a test-harness run as device E2E.

Signed package and store release instructions are in the
[mobile release guide](../deployment/mobile/README.md).

### Ethiopian dates and times

Player-facing dates use the Ethiopian calendar (including Pagume and its leap
day), with month names in the selected English or Amharic language. Times use
Ethiopia's UTC+3 timezone and Ethiopian 12-hour clock, with explicit daytime or
nighttime labels: 07:00 EAT is 1:00 daytime. The date changes at local midnight.
Use `EthiopianDateTime` in `src/core/localization/ethiopian_date_time.dart` for
all displayed timestamps, rather than `DateTime.toString()` or `toLocal()`.
API/storage timestamps remain ISO-8601 instants; durations, token expiry and
scheduling comparisons continue to use `DateTime`.
