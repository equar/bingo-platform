# Validation Report

## Scope

Validation was run against the local bingo platform workspace using the actual backend, an isolated local MySQL test database, and the Flutter agent app.

## Backend

### Environment

- Isolated MySQL server started on `127.0.0.1:3307`
- Test database: `bingo_test`
- Backend test profile: `bingo-backend/src/test/resources/application-test.yml`

### Commands

- `mysql -h 127.0.0.1 -P 3307 -u root -e "DROP DATABASE IF EXISTS bingo_test; CREATE DATABASE bingo_test;"`
- `Set-Location d:/GitHub/bingo-platform/bingo-backend; mvn test`

### Result

- `mvn test` passed
- Tests run: `17`
- Failures: `0`
- Errors: `0`
- Skipped: `0`

### Coverage observed in the passing suite

- Authentication and security flows
- Wallet and deposit flows
- Bingo game creation and session flows
- Live draw websocket behavior

## Flutter Agent App

### Commands

- `Set-Location d:/GitHub/bingo-platform/bingo-agent; flutter doctor -v`
- `Set-Location d:/GitHub/bingo-platform/bingo-agent; flutter test`
- `Set-Location d:/GitHub/bingo-platform/bingo-agent; flutter analyze`
- `Set-Location d:/GitHub/bingo-platform/bingo-agent; flutter build web`

### Result

- `flutter test` passed
- `flutter analyze` passed
- `flutter build web` passed after generating web platform support

### Notes

- The app now has generated `web/` platform support so the web build is valid in this workspace.
- The smoke test checks the real app entrypoint and the expected missing-configuration screen when `API_BASE_URL` is not provided.

## Mobile and Desktop Toolchains

### Android

- Installed locally under `C:\Users\16414\AppData\Local\Android\Sdk`
- Required packages installed: `platform-tools`, `platforms/android-36`, `build-tools/28.0.3`
- `flutter doctor -v` now reports the Android toolchain as passing

### Windows Desktop

- Not available in this environment
- `flutter doctor -v` reported: Visual Studio not installed

## Summary

The backend is green against the isolated local MySQL database, and the Flutter agent app is green for analysis, tests, and web build. The Android SDK/toolchain issue is fixed and `flutter doctor -v` now passes for Android; Windows desktop builds are still unavailable without Visual Studio.
