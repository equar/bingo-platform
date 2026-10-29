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
