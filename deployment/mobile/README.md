# Direct-share mobile packages

Both apps build against `https://api.bingo.bakaroo.com` using the checked-in
`production.json`. The URL is an origin: existing REST endpoints include
`/api/v1`, and player WebSockets use `/ws` with WSS. No HTTP exceptions are
needed on Android or iOS.

## Android

From the repository root:

```bash
bash deployment/mobile/build-packages.sh android
```

Outputs are `artifacts/bingo-app-android.apk` and
`artifacts/bingo-agent-android.apk`, with SHA-256 checksum files. These are
universal release APKs suitable for file sharing, not Play Store AABs.
Recipients permit installation from the app they use to open the APK.

The helper creates a separate persistent release keystore per app if no
`android/key.properties` exists. Back up **all of `signing/` securely**;
keep those same keys for future app updates. Signing files and build outputs
are ignored by Git. Existing signing configuration is reused. The player's
Android ID is `com.equar.bingo.bingo_app`; the agent's is
`com.equar.bingo.bingo_agent`. Previously installed debug/demo apps may need
uninstalling if their signing identity or package ID differs.

## iOS

An IPA is not generally installable simply by opening a Telegram attachment.
Direct sharing uses Apple Ad Hoc distribution, with a paid Apple Developer
team, an Apple Distribution identity/private key, and a matching profile for
each app that includes every recipient's device UDID. Install using Apple
Configurator or an appropriate signed OTA distribution setup. TestFlight is
an alternative when enrolling recipients individually is impractical.

Both iOS projects are included. Configure each Runner target's bundle ID,
team, certificate, and Ad Hoc provisioning profile in Xcode. Use the checked-in `*-ExportOptions.plist.example` files as a starting point,
with your Apple team ID and profile names. Export an Ad Hoc
ExportOptions.plist for each app into `signing/bingo-app-ExportOptions.plist`
and `signing/bingo-agent-ExportOptions.plist`. Then run:

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

The restored iOS bundle IDs are `com.equar.bingo.bingoApp` (player) and
`com.equar.bingo.bingoAgent` (agent). Create their Apple profiles accordingly,
or update the Xcode bundle IDs and export profile mappings together.
The local Xcode installation currently requires its licence to be reviewed
and accepted before iOS compilation and native macOS-hosted widget tests work.

## Device compatibility and failed installs

Both shared Android APKs require **Android 7.0 (API 24) or newer**. They include
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
adb install -r deployment/mobile/artifacts/bingo-app-android.apk
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

## Player app branding

The player launcher master is `bingo-app/assets/branding/app-icon.png`, prepared
from the supplied Bingo artwork. To regenerate native icon sizes on macOS:

```bash
python3 deployment/mobile/generate-player-icons.py
```

This updates all five Android legacy densities, the Android 8+ adaptive icon
resources (blue background with an inset foreground), and every named entry in
the iOS AppIcon catalog. The iOS 1024px store icon is opaque. Keep the square
master without pre-applied rounded corners; the operating system supplies the
launcher mask. The agent app retains its separate existing branding.

The player login/register screen shares the home illustration, navy/blue palette
and orange primary action. Existing field keys and Ethiopian phone/six-digit PIN
validation remain in place. PIN visibility has localized English/Amharic labels;
fields and actions are disabled during submission. Auth UI regression tests cover
login, registration, PIN visibility, duplicate submission and narrow layouts with
large English and Amharic text.
