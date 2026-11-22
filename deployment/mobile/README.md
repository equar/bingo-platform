# Direct-share mobile packages

The Player app builds against `https://api.bingo.bakaroo.com` using the checked-in
`production.json`. The URL is an origin: existing REST endpoints include
`/api/v1`, and player WebSockets use `/ws` with WSS. No HTTP exceptions are
needed on Android or iOS.

## Android

From the repository root:

```bash
bash deployment/mobile/build-packages.sh android
```

Output is `artifacts/bingo-player-android.apk`, with a SHA-256 checksum file.
It is a universal release APK suitable for file sharing, not a Play Store AAB.
Recipients permit installation from the app they use to open the APK.

The helper creates or reuses the Player release keystore if no
`android/key.properties` exists. Back up `signing/` securely and keep the same
key for future updates. Signing files and build outputs are ignored by Git.
The Player Android application ID is `com.bakaroo.bingo_app`. Previously
installed debug/demo builds may need uninstalling if signing differs.

The Play Store helper (`playstoreBuild.sh`) never generates a signing key. For
the Player, it restores the ignored `bingo-player/android/key.properties` from
the existing `signing/bingo-app.jks` and `signing/bingo-app.json` upload key
pair. If that pair is unavailable, restore the registered Play upload key
instead of creating a replacement.

## iOS

An IPA is not generally installable simply by opening a Telegram attachment.
Direct sharing uses Apple Ad Hoc distribution, with a paid Apple Developer
team, an Apple Distribution identity/private key, and a matching profile that
includes every recipient's device UDID. Install using Apple
Configurator or an appropriate signed OTA distribution setup. TestFlight is
an alternative when enrolling recipients individually is impractical.

Configure the Player Runner target's bundle ID, team, certificate, and Ad Hoc
provisioning profile in Xcode. Use the checked-in Player ExportOptions example
as a starting point, with your Apple team ID and profile name. Export an Ad Hoc
ExportOptions.plist to `signing/bingo-player-ExportOptions.plist`. Then run:

```bash
bash deployment/mobile/build-packages.sh ios
```

The helper writes signed IPAs to `artifacts/`. No installable iOS IPA can be
produced until the signing identity and registered-device profiles are supplied.
Unsigned archives are not substitutes for installable packages.

## CORS

Native Android/iOS networking is not subject to browser CORS. Do not add `*`,
Telegram URLs, `file://`, or arbitrary `null` origins. Browser REST/WebSocket
allowlists use exact HTTPS origins such as `https://admin.bingo.bakaroo.com`
and `https://bingo.bakaroo.com`; they never contain API paths. JWT and role
checks apply to native requests as well as browser requests.

Production device validation remains separate from local analysis/widget tests.
Do not run financial integration scenarios against the production database
merely to test a package build.

The checked-in Player iOS bundle ID is `com.bakaroo.bingo.bingoApp`. Keep its
Apple profile and Xcode bundle ID aligned.
The local Xcode installation currently requires its licence to be reviewed
and accepted before iOS compilation and native macOS-hosted widget tests work.

## Device compatibility and failed installs

The shared Player Android APK requires **Android 7.0 (API 24) or newer**. It includes
`armeabi-v7a` (32-bit ARM), `arm64-v8a` (64-bit ARM) and `x86_64`. One universal
APK is the default so recipients do not need to choose a CPU variant. Legacy
32-bit Intel Android phones are unsupported. Android 6 and older are unsupported
by the current Flutter runtime; lowering `minSdk` alone cannot add support.
Country does not determine APK compatibility: Android version, CPU, available
storage, signing identity and installer policy do. Low-end devices still need
real-device performance checks; universal ABI support does not guarantee speed.

Each Android build now verifies:

- Application ID and minimum Android version.
- Valid v2 APK signature and the original per-app release certificate, pinned in
  `android-signing-certificates.json` (public fingerprints, not private keys).
- All three supported CPU architectures.
- 16 KB ZIP alignment and ELF segment alignment for native libraries.
- A strictly greater version code than the previous shared APK, when present.

Version `0.1.1+2` follows the previously shared `0.1.0+1`. For the next release,
increase `version:` in both app pubspec files, or supply a unique increasing build
number, for example:

```bash
BINGO_BUILD_NUMBER=3 bash deployment/mobile/build-packages.sh android all
```

Do not delete old artifacts to bypass the version check. Retain release records
when building on another machine. Never regenerate signing keys for an update;
restore the existing private keys securely. Release Gradle builds no longer fall
back to a debug key. Debug builds still use their normal debug configuration.
The helper saves a `.compatibility.json` report next to each shared APK. These
checks inspect the artifact; they do not substitute for device installation tests.

For a failed installation, record the phone model, Android version, exact error,
APK filename, and whether Bingo was already installed. Use the actual APK file
rather than an AAB, partial download, ZIP, or link preview. Verify the provided
SHA-256 checksum after downloading when possible. Allow installation from the
specific trusted file-sharing/file-manager app in Android settings and ensure
there is enough free storage for the package and its extracted app data.

If an older demo/debug app has a different signing certificate, Android will
reject an update. Preserve any needed local data before considering removal;
uninstalling deletes local app data and should not be the first troubleshooting
step. The normal release-to-release path retains the original key and installs
as an update. ADB can identify the exact cause without guessing:

```bash
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.cpu.abilist
adb install -r deployment/mobile/artifacts/bingo-player-android.apk
```

Common ADB errors: `INSTALL_FAILED_OLDER_SDK` means unsupported Android version;
`INSTALL_FAILED_NO_MATCHING_ABIS` means unsupported CPU/package combination;
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` indicates a conflicting existing signing
identity; `INSTALL_FAILED_VERSION_DOWNGRADE` indicates an older build number;
`INSTALL_FAILED_INSUFFICIENT_STORAGE` means more space is needed. Installer or
organization policy may also prevent sideloading; package changes cannot override
those restrictions.

Before wide distribution, test clean installation, release-to-release update,
launch/login, Amharic text, slow networks, and low-memory behavior on a physical
32-bit ARM Android 7-era device and a modern ARM64 device, including a 16 KB device
if available. No physical phones were connected during this review, so the
reported failures cannot yet be attributed to a particular cause.

The iOS projects currently target iOS 13+, but installable IPA distribution still
requires valid Apple signing and device provisioning. A Telegram IPA is not
universally installable on iPhones. Apple signing and actual iPhone compatibility
remain unverified until that configuration is supplied.

References: [Flutter Android releases](https://docs.flutter.dev/deployment/android),
[Android page-size compatibility](https://developer.android.com/guide/practices/page-sizes),
[Android app signing](https://developer.android.com/studio/publish/app-signing).

## Google Play and Apple store releases

The store workflows publish separately from the direct-share APK/Ad Hoc
workflow above. All store workflows are manually dispatched. Repository files
do not verify store credentials, app records, signing profiles, environment
approvals, or a successful upload. Complete store listings with final metadata
and approved privacy/support URLs, and resolve Product/Legal review of payment,
wallet, prize, and bingo policies before public release.

### One-time store setup

**Google Play**

1. Create the Play Console app with the package name configured by the
   `ANDROID_PACKAGE_NAME` repository variable. It must match Android's
   `applicationId`.
2. Enable Play App Signing and configure the corresponding upload keystore.
3. Create a service account with the required Play Console permissions.
4. Save the downloaded service-account JSON as the `GOOGLE_PLAY_JSON`
   repository secret:

   ```bash
   gh secret set GOOGLE_PLAY_JSON < /path/to/google-play-service-account.json
   ```

5. Confirm the internal testing track, release notes, and testers.

**Apple**

1. Create the App Store Connect app using the `IOS_BUNDLE_ID` repository
   variable.
2. Create an App Store Connect API key. Store one JSON credential bundle as
   `APPSTORE_DEPLOYER_KEY` with `key_id`, `issuer_id`, and `private_key` fields;
   the private key is the downloaded `.p8` contents:

   ```json
   {
     "key_id": "KEY_ID_FROM_APP_STORE_CONNECT",
     "issuer_id": "ISSUER_ID_FROM_APP_STORE_CONNECT",
     "private_key": "-----BEGIN PRIVATE KEY-----\\n...\\n-----END PRIVATE KEY-----"
   }
   ```

   Save it as a local, ignored file and upload it without printing it:

   ```bash
   gh secret set APPSTORE_DEPLOYER_KEY < /path/to/appstore-deployer-key.json
   ```
3. Create the distribution certificate and provisioning profile for the
   configured bundle ID/team.
4. Configure TestFlight access and App Review notes.

### Upload signing credentials

Run these commands from the repository root. They pipe file contents directly
to `gh` instead of displaying Base64 credentials. The Android values come from
the existing, ignored `deployment/mobile/signing/bingo-app.jks` and
`bingo-app.json`; do not regenerate or replace the registered Play upload key.

```bash
base64 -i deployment/mobile/signing/bingo-app.jks | tr -d '\n' | gh secret set BINGO_ANDROID_KEYSTORE_BASE64 --repo equar/bingo-platform
python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[sys.argv[2]], end="")' deployment/mobile/signing/bingo-app.json storePassword | gh secret set BINGO_ANDROID_KEYSTORE_PASSWORD --repo equar/bingo-platform
python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[sys.argv[2]], end="")' deployment/mobile/signing/bingo-app.json keyAlias | gh secret set BINGO_ANDROID_KEY_ALIAS --repo equar/bingo-platform
python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[sys.argv[2]], end="")' deployment/mobile/signing/bingo-app.json keyPassword | gh secret set BINGO_ANDROID_KEY_PASSWORD --repo equar/bingo-platform
gh secret set GOOGLE_PLAY_JSON --repo equar/bingo-platform < /path/to/google-play-service-account.json
```

Upload the iOS signing assets created from Apple Developer/App Store Connect.
The profile must match `IOS_BUNDLE_ID`. Enter the `.p12` password without
echoing it; set the CI keychain password to a random value:

```bash
base64 -i /path/to/bingo-distribution-certificate.p12 | tr -d '\n' | gh secret set BINGO_IOS_DISTRIBUTION_CERTIFICATE_BASE64 --repo equar/bingo-platform
base64 -i /path/to/bingo-app-store.mobileprovision | tr -d '\n' | gh secret set BINGO_IOS_PROVISIONING_PROFILE_BASE64 --repo equar/bingo-platform
printf 'Distribution certificate password: '
IFS= read -r -s IOS_CERT_P12_PASSWORD
printf '\n'
printf '%s' "$IOS_CERT_P12_PASSWORD" | gh secret set BINGO_IOS_DISTRIBUTION_CERTIFICATE_PASSWORD --repo equar/bingo-platform
unset IOS_CERT_P12_PASSWORD
openssl rand -hex 32 | tr -d '\n' | gh secret set BINGO_IOS_KEYCHAIN_PASSWORD --repo equar/bingo-platform
```

`GOOGLE_PLAY_JSON` and `APPSTORE_DEPLOYER_KEY` are raw JSON values, not
Base64. The production Android and iOS workflows use the
`bingo-app-android-production` and `bingo-app-ios-production` Environments;
create those environments first and optionally set environment-scoped store
credentials to override repository values:

```bash
gh secret set GOOGLE_PLAY_JSON --repo equar/bingo-platform --env bingo-app-android-production < /path/to/google-play-service-account.json
gh secret set APPSTORE_DEPLOYER_KEY --repo equar/bingo-platform --env bingo-app-ios-production < /path/to/appstore-deployer-key.json
```

### Store workflow map

- `bingo-player-mobile-ci.yml` validates Flutter, runs tests, and builds a release
  bundle without publishing.
- `bingo-player-android-internal-testing.yml` builds/signs an Android App Bundle
  and uploads it to the Play internal-testing track.
- `bingo-player-android-production.yml` promotes the previously tested version
  code without rebuilding.
- `bingo-player-ios-testflight.yml` builds/signs an IPA and uploads it to
  TestFlight.
- `bingo-player-ios-app-store.yml` submits a processed build for App Review after
  manual approval.

Repository variables:

- `ANDROID_PACKAGE_NAME`
- `IOS_BUNDLE_ID`

Repository secrets:

- `BINGO_ANDROID_KEYSTORE_BASE64`
- `BINGO_ANDROID_KEYSTORE_PASSWORD`
- `BINGO_ANDROID_KEY_ALIAS`
- `BINGO_ANDROID_KEY_PASSWORD`
- `GOOGLE_PLAY_JSON`
- `APPSTORE_DEPLOYER_KEY`
- `BINGO_IOS_DISTRIBUTION_CERTIFICATE_BASE64`
- `BINGO_IOS_DISTRIBUTION_CERTIFICATE_PASSWORD`
- `BINGO_IOS_PROVISIONING_PROFILE_BASE64`
- `BINGO_IOS_KEYCHAIN_PASSWORD`

`APPSTORE_DEPLOYER_KEY` is configured as a repository secret, but GitHub does
not expose its contents. Confirm it is valid JSON with `key_id`, `issuer_id`,
and `private_key` before running an iOS store workflow. The Android store
workflows use only `GOOGLE_PLAY_JSON` containing raw JSON. The
`mobile_api_base_url` and rollout values are workflow-dispatch inputs on the
internal testing and TestFlight workflows, not repository variables. Their
default is the HTTP test API; only those upload lanes explicitly allow HTTP,
and only for `api.test.bakaroo.com`.

Production promotion/submission use the `bingo-app-android-production` and
`bingo-app-ios-production` GitHub Environments. These environments are not
currently present in the repository; create them and require reviewers before
running either production workflow. Add production-scoped store secrets there
to override repository secrets. Internal testing and TestFlight use
repository-level credentials and do not use those production environments.

### Production build provenance

1. For a test build, dispatch the internal-testing or TestFlight workflow from
   `develop` and keep its default test API URL.
2. To create a production candidate, dispatch the same workflow from `release`
   and set `mobile_api_base_url` to `https://api.bingo.bakaroo.com`. The
   workflow records the backend URL, application ID, build number, commit, and
   run ID in a provenance artifact after the store upload succeeds.
3. In the corresponding production promotion/submission workflow, provide the
   exact Android version code or iOS app version and build number, plus the
   source workflow run ID (the numeric ID in the source run's URL). The
   workflow verifies the successful `release`-branch upload, matching build
   and app ID, and production API provenance before it can promote or submit
   the build.

Test-backend builds are rejected by the production workflows even if the
production workflow is given a production API URL. For iOS, the verified build
number is explicitly passed to Fastlane App Store submission.

### Release flow and recovery

1. Run Android internal testing from `release` with the production API URL and
   verify the uploaded candidate in Play Console.
2. Promote that exact Android version code and source workflow run ID.
   Configure a staged rollout fraction only when intended.
3. Run iOS TestFlight from `release` with the production API URL; confirm the
   processed build in App Store Connect.
4. Submit that exact iOS app version, build number, and source workflow run ID
   for review through the manually approved production workflow.
5. If an upload failed before store acceptance, fix the workflow and rerun with
   the same release inputs. If a wrong build reached a store, verify store
   state first; do not rebuild blindly. Rotate signing material in the store
   console before changing GitHub secrets.

Recheck current Google Play billing and Apple review requirements before
enabling payment-adjacent or prize mechanics. Confirm licensing,
geo-restrictions, age gating, and disclosures with Product/Legal owners. Store
workflow files alone do not establish compliance or release readiness.
