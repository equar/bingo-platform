# Mobile Store Deployment

This document explains how the Flutter player app is released to Google Play internal testing, Google Play production, TestFlight, and the App Store.

## Current readiness

All four store workflows are manually dispatched. The iOS project is present at
`bingo-app/ios/Runner.xcodeproj`; both iOS workflows also fail fast if that
project is absent. Repository files alone do not verify store credentials,
store records, signing profiles, environment approvals, or a successful upload.
The release URL validator requires HTTPS and rejects localhost, emulator,
loopback, and private addresses. Missing target-specific signing or store
credentials block a successful release build or upload. Complete store listings
with final metadata and approved privacy/support URLs, and resolve product/legal
review of payment, wallet, prize, and bingo policies before public release.

## One-time setup

### Google Play

1. Create the app in Google Play Console and confirm the package name matches `BINGO_ANDROID_PACKAGE_NAME`.
2. Enable Play App Signing and upload the release keystore that corresponds to `BINGO_ANDROID_KEYSTORE_BASE64`.
3. Create a service account with Play Console permissions and store the JSON as `BINGO_GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64`.
4. Confirm the internal testing track exists and that the release notes and testers are configured.

### Apple

1. Create the app in App Store Connect and confirm the bundle identifier matches `BINGO_IOS_BUNDLE_ID`.
2. Create an App Store Connect API key and configure the key id, issuer id, and private key as GitHub secrets listed below.
3. Create the iOS distribution certificate and provisioning profile that match the bundle identifier and signing team.
4. Configure TestFlight access and any review notes that Apple should see when the build is submitted.

## Workflow map

- `bingo-app-mobile-ci.yml` validates the Flutter app, runs tests, and builds a release bundle without publishing anything.
- `bingo-app-android-internal-testing.yml` builds and signs the Android App Bundle, then uploads it to the Google Play internal testing track.
- `bingo-app-android-production.yml` promotes a previously tested Android version code from internal testing to production.
- `bingo-app-ios-testflight.yml` builds and signs the iOS IPA, then uploads it to TestFlight.
- `bingo-app-ios-app-store.yml` submits a processed iOS build for App Store review after manual approval.

## GitHub Actions configuration

Repository variables:

- `BINGO_MOBILE_API_BASE_URL`
- `BINGO_ANDROID_PACKAGE_NAME`
- `BINGO_IOS_BUNDLE_ID`
- `BINGO_ANDROID_PRODUCTION_ROLLOUT`

Repository secrets:

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

Production promotion/submission use the `bingo-app-android-production` and
`bingo-app-ios-production` GitHub environments. Configure required reviewers in
repository settings; internal testing and TestFlight workflows do not use
those production environments. The workflows validate the API URL before
building or submitting.

## Release flow

### Android internal testing

1. Run the Android internal testing workflow manually.
2. Provide the public HTTPS backend URL, or rely on `BINGO_MOBILE_API_BASE_URL`.
3. The workflow writes `android/key.properties` from secrets, validates the backend URL, builds the release AAB, and uploads it to the internal track.
4. Review the uploaded artifact in Play Console before promoting it.

### Android production promotion

1. Run the Android production workflow manually after the internal build has been verified.
2. Enter the exact `android_version_code` that was tested.
3. Confirm the rollout fraction if you want a staged production release.
4. The workflow promotes that version code from internal testing to production without rebuilding.

### iOS TestFlight

1. Confirm the iOS project exists and signing secrets are configured.
2. Run the iOS TestFlight workflow manually.
3. The workflow imports the signing certificate, installs the provisioning profile, builds the IPA, and uploads it to TestFlight.
4. Verify the build in App Store Connect before using it for App Review.

### iOS App Store submission

1. Run the iOS App Store submission workflow manually after the build has been processed in App Store Connect.
2. Provide the exact `ios_build_number` that should be submitted.
3. The workflow validates the backend URL, loads the App Store Connect API key, and submits the build for review.
4. Keep the GitHub environment approval in place so production submission cannot happen accidentally.

## Recovery guidance

- If Google Play upload fails before the console accepts the artifact, fix the workflow and rerun it with the same release inputs.
- If a release reaches Play Console or App Store Connect with the wrong build number, do not rebuild blindly. Verify the store state first and only rerun the exact release path that matches the published artifact.
- If signing material changes, rotate the keystore or certificates in the store consoles before changing the GitHub secrets.
- If the app review process flags policy concerns, pause production promotion until the legal and product review is complete.

## Store policy notes

- Recheck current Google Play billing requirements for in-app purchases, digital content, and gambling-related payment flows before enabling a public release.
- Apple review is stricter for games and payment-adjacent apps offering prizes, wallets, or transfers; verify current requirements with product/legal owners.
- For real-money, prize, or chance-based mechanics, confirm licensing, geo-restrictions, age gating, and disclosures before enabling store releases.
