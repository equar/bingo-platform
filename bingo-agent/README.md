# bingo-agent

Flutter counter-service agent app for Android, iOS, and web.

## Localization

The app supports English (`en`) and Amharic (`am`) through Flutter gen-l10n. Translation resources live in `lib/l10n/app_en.arb` and `lib/l10n/app_am.arb`; run `flutter gen-l10n` from this directory after changing them. The device's selected locale determines the app language.

Agent float top-up submissions create requests only. The float is credited only after an authorized administrator approves the request.
