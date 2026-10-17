# Mobile Store Deployment

This document explains how the Flutter player app is released to Google Play internal testing, Google Play production, TestFlight, and the App Store.

## One-time setup

### Google Play

1. Create the app in Google Play Console and confirm the package name matches `BINGO_ANDROID_PACKAGE_NAME`.
2. Enable Play App Signing and upload the release keystore that corresponds to `BINGO_ANDROID_KEYSTORE_BASE64`.
3. Create a service account with Play Console permissions and store the JSON as `BINGO_GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64`.
4. Confirm the internal testing track exists and that the release notes and testers are configured.

### Apple

1. Create the app in App Store Connect and confirm the bundle identifier matches `BINGO_IOS_BUNDLE_ID`.
2. Create an App Store Connect API key and store the key id, issuer id, and private key in the GitHub secrets listed in the readiness doc.
3. Create the iOS distribution certificate and provisioning profile that match the bundle identifier and signing team.
4. Configure TestFlight access and any review notes that Apple should see when the build is submitted.

## Workflow map

- `bingo-app-mobile-ci.yml` validates the Flutter app, runs tests, and builds a release bundle without publishing anything.
- `bingo-app-android-internal-testing.yml` builds and signs the Android App Bundle, then uploads it to the Google Play internal testing track.
- `bingo-app-android-production.yml` promotes a previously tested Android version code from internal testing to production.
- `bingo-app-ios-testflight.yml` builds and signs the iOS IPA, then uploads it to TestFlight.
- `bingo-app-ios-app-store.yml` submits a processed iOS build for App Store review after manual approval.

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

1. Restore the missing iOS project before running the workflow.
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
