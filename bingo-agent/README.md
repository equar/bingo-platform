# Bingo Agent

Flutter counter-service app for Android and web, with an iOS project for
Ad Hoc distribution after Apple signing is configured. Requires Flutter
3.41.0+ and Dart 3.11.0+.

## Local development

Run from this directory with an explicit backend URL:

```bash
flutter pub get
flutter gen-l10n
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080
flutter test
flutter analyze
```

Use `http://10.0.2.2:8080` for an Android emulator and
`http://localhost:8080` for desktop or an iOS simulator. Profile and release
builds require HTTPS. The app supports English and Amharic; translation files
are in `lib/l10n/` and are generated with `flutter gen-l10n`.

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
