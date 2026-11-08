# Bingo Agent

Flutter counter-service app for Android, web, and Windows, with an iOS project
for Ad Hoc distribution after Apple signing is configured. Requires Flutter
3.41.0+ and Dart 3.11.0+.

## Local development

Run any platform from the repository root:

```powershell
.\scripts\Run-BingoLocal.ps1 -App agent -Platform android
.\scripts\Run-BingoLocal.ps1 -App agent -Platform chrome
.\scripts\Run-BingoLocal.ps1 -App agent -Platform windows
```

The launcher chooses a connected Android device or starts the only configured
emulator. Pass `-DeviceId <id>` to select a particular device or emulator.
Chrome uses port 5181. Windows builds require Visual Studio 2022 Build Tools
with **Desktop development with C++** and a Windows 10/11 SDK. Start MySQL and
the backend `dev` profile before running the app.

The equivalent VS Code configurations are **Bingo Agent — Android emulator**,
**Bingo Agent — Chrome**, and **Bingo Agent — Windows**. Select the desired
emulator in VS Code's device picker. The app uses
`http://10.0.2.2:8080` on Android and `http://localhost:8080` on Chrome and
Windows. The API origin is the backend root; routes are under `/api/v1`. The
Agent app has no live-draw WebSocket client. Chrome uses a separate origin from
Player (`localhost:5180`), and logout clears the Agent's saved session.

Switching platforms starts a separate app instance and does not transfer an
in-progress workflow. Development HTTP is debug-only; profile and release
builds require a non-loopback HTTPS URL. A local Agent account must be
configured through the backend's documented development account setup and
approved for agent access. Store its phone/PIN only in the ignored backend
`.env`; never add credentials to launch settings. The app supports English
and Amharic; translation files are in `lib/l10n/` and can be regenerated with
`flutter gen-l10n`.

Run the app's checks from this directory with `flutter test` and
`flutter analyze`.

Agents authenticate with phone and PIN. Float top-up submissions create
requests; an authorized administrator must approve a request before the float
is credited. Ticket sales debit float and may accrue commission.

## Functional E2E and release

Use the [guarded real E2E runbook](../docs/qa/platform-e2e-runbook.md) and
[current scenario matrix](../docs/qa/platform-e2e-scenario-matrix.md). The
launcher requires existing approved/pending fixtures and verifies the isolated
`android-test` backend; it does not create accounts or enable financial
posting. Keep phone/PIN values only in ignored local configuration, never logs.

Signed package and store release instructions are in the
[mobile release guide](../deployment/mobile/README.md).
