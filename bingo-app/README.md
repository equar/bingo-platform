# bingo-app

Flutter player app for the bingo backend.

Requires Flutter 3.41.0 or newer and Dart 3.11.0 or newer. CI also checks
dependency resolution, analysis, and tests with Flutter 3.41.0 and 3.47.7.

Run it with:

```bash
flutter pub get
flutter gen-l10n
flutter run -d windows --dart-define=API_BASE_URL=http://localhost:8080
```

The local backend must be running at `http://localhost:8080`. `API_BASE_URL`
remains required; there is no runtime fallback. For Android emulators,
explicitly pass `--dart-define=API_BASE_URL=http://10.0.2.2:8080`. For a
deployed backend, replace the local URL with its HTTPS URL. Local HTTP is
accepted only in debug builds; profile and release builds require HTTPS and
reject localhost and other loopback addresses.

Live-game connections use the same host and port as the REST API. The client connects to `/ws` with STOMP, sends the HTTP JWT as a bearer token in the `CONNECT` frame, and subscribes to `/topic/session/{sessionId}/draws` for live draw events. If the backend is behind TLS, use an `https://` API base URL so the app derives `wss://` automatically.

Useful commands:

```bash
flutter test test/features/game
flutter analyze
```

## Android E2E and credentials

For the current guarded player Android workflow, use [Local E2E Testing](../docs/local-e2e-testing.md) and the [Module 06A-9 procedure](../docs/qa/modules/module-06a9-android-player-functional-e2e.md). It runs the real Flutter app on `emulator-5554` against the isolated `android-test` backend and `bingo_qa_test`, with read-only before/after reconciliation. A runnable test or a Flutter unit/widget test is not itself a passing Android E2E result; check the [QA status](../docs/qa/module-status.md) for the latest recorded outcome.

The older `integration_test/local_e2e_test.dart` harness and `scripts/run-local-e2e.ps1` remain for historical/local development use. They do not enforce the current isolated-schema preflight, and the script's default backend URL is the development service. Do not use this harness against `bingo`, persistent `bingo_qa`, or any shared database, and do not report its Flutter test-harness run as real Android E2E unless it was explicitly run on a device against an authorized isolated MySQL schema.

That legacy harness requires `BINGO_E2E_PRIMARY_PHONE` and `BINGO_E2E_PRIMARY_PASSWORD`; it has no committed account or PIN fallbacks. Its setup test generates a secondary PIN at runtime. For any approved local run, keep credentials in the ignored `e2e-secrets.json` copied from `e2e-secrets.example.json`, pass the file rather than secret values on the command line, and never retain credentials in logs or manifests.

## Direct-share production packages

Production backend: `https://api.bingo.bakaroo.com` (no `/api` suffix).
REST clients already include `/api/v1`; live draws use `wss://api.bingo.bakaroo.com/ws`.
From the repository root, run `bash deployment/mobile/build-packages.sh android`
to build signed APKs for both apps. See [package/signing instructions](../deployment/mobile/README.md)
for outputs, signing backups, and iOS Ad Hoc requirements.
