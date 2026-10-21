# bingo-agent

Flutter counter-service agent app for Android, iOS, and web.

Requires Flutter 3.41.0 or newer and Dart 3.11.0 or newer. CI checks both the
minimum Flutter version (3.41.0) and the current pinned version (3.47.7).

Run the app with an explicitly supplied backend URL, for example:

```bash
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

Use `http://10.0.2.2:8080` for an Android emulator and `http://localhost:8080`
for desktop or an iOS simulator. Production/profile builds require HTTPS.

## Localization

The app supports English (`en`) and Amharic (`am`) through Flutter gen-l10n. Translation resources live in `lib/l10n/app_en.arb` and `lib/l10n/app_am.arb`; run `flutter gen-l10n` from this directory after changing them. The device's selected locale determines the app language.

Agent float top-up submissions create requests only. The float is credited only after an authorized administrator approves the request.
