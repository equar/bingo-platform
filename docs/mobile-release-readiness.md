# Mobile Release Readiness

This repository now has release automation for the Flutter player app, but the workflows are only ready for production use after the store accounts, signing material, and public backend URL are in place.

## What is ready

- The app already exposes its backend through `--dart-define=API_BASE_URL=...`, so the release workflows can inject a public HTTPS API endpoint without changing the runtime code path.
- Android release signing is wired into `bingo-app/android/app/build.gradle.kts`, with release builds requiring a release signing configuration; missing keys fail the build.
- Fastlane lanes now exist for Android internal testing, Android production promotion, iOS TestFlight upload, and iOS App Store submission.
- The release workflows are manual `workflow_dispatch` jobs, so nothing is published automatically.
- The release URL validator rejects localhost, emulator, and private network endpoints before a release build is created.

## Current blockers

- The iOS project tree in this repository is incomplete. `bingo-app/ios/Runner.xcodeproj` is not present, so the iOS workflows are intentionally fail-fast until the project is restored.
- Android release signing still needs real keystore material, passwords, and a Google Play service account.
- Apple release signing still needs the distribution certificate, provisioning profile, and App Store Connect API key.
- The Google Play and App Store records must already exist, including bundle identifiers, app metadata, privacy policy URLs, and support URLs.
- Because this app includes wallet, transfer, and bingo gameplay features, the final store review must also confirm the app fits the current Google Play and Apple policy rules for payments, rewards, and any real-money or prize-related flows.

## Required configuration

Set these repository variables in GitHub Actions:

- `BINGO_MOBILE_API_BASE_URL`
- `BINGO_ANDROID_PACKAGE_NAME`
- `BINGO_IOS_BUNDLE_ID`
- `BINGO_ANDROID_PRODUCTION_ROLLOUT`

Set these repository secrets in GitHub Actions:

- `BINGO_ANDROID_KEYSTORE_BASE64`
- `BINGO_ANDROID_KEYSTORE_PASSWORD`
- `BINGO_ANDROID_KEY_ALIAS`
- `BINGO_ANDROID_KEY_PASSWORD`
- `BINGO_GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64`
- `BINGO_APP_STORE_CONNECT_KEY_ID`
- `BINGO_APP_STORE_CONNECT_ISSUER_ID`
- `BINGO_APP_STORE_CONNECT_PRIVATE_KEY_BASE64`
- `BINGO_IOS_DISTRIBUTION_CERTIFICATE_BASE64`
- `BINGO_IOS_DISTRIBUTION_CERTIFICATE_PASSWORD`
- `BINGO_IOS_PROVISIONING_PROFILE_BASE64`
- `BINGO_IOS_KEYCHAIN_PASSWORD`

## Build numbering

- Android production promotion uses the explicit `android_version_code` input and promotes that version code from the internal track to production.
- iOS App Store submission uses the explicit `ios_build_number` input so the operator chooses the already processed build that should be submitted for review.
- Keep the version and build numbers in `pubspec.yaml` aligned with what is uploaded to each store.

## Environment approvals

- The production workflows are marked with GitHub environments, but approvals are enforced in the repository settings, not inside the YAML file itself.
- Configure required reviewers for the production environments before using the production submission jobs.

## Store policy notes

- Google Play billing rules require in-app purchases and digital content payments to use Google Play billing when applicable, and the policy specifically calls out gambling-related payment flows as a separate restriction area.
- Apple review for games and payment-adjacent apps is stricter when the product offers prizes, wallets, transfers, or gambling-like behavior.
- If the product includes any real-money, prize, or chance-based mechanics, verify licensing, geo-restrictions, age gating, and disclosure requirements before enabling the release workflows.
