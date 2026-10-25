# Module 04A-R — Android Network Recovery and Retry Verification

Date: 2026-10-24  
Device: `emulator-5554` (API 37)  
App: `com.example.bingo_app`, normal debug build, authenticated synthetic player #181  
Backend: host QA service `http://localhost:8081`; emulator service `http://10.0.2.2:8081`

## Result

**PASS. The Module 04A Android retry gap is closed; Module 04A is COMPLETE.** A Flutter loading-state defect was reproduced and fixed, then the Android recovery journey was rerun successfully on the fixed APK.

The prior recovery runs did not prove a Retry defect. The retained player session token had expired, so requests were diverted to session-expired handling. A prior package-wide network block also disconnected the Flutter VM test driver. Finally, radio controls alone left the emulator's virtual route to the host reachable. These explained earlier inconclusive runs. A fresh player login and reversible per-package Android network block made the offline lobby request deterministic while keeping ADB available.

## Android journey

1. Confirmed host backend health `UP` and emulator TCP access to `10.0.2.2:8081`.
2. Signed in through the app UI with existing synthetic player #181, opened Rooms, and confirmed the server room list loaded.
3. Enabled Android connectivity chain 3 and denied networking only for `com.example.bingo_app`. Tapped the lobby refresh action in the app. The lobby showed “Something went wrong” and “Couldn't load rooms. Check your connection and retry.” Captured [offline error](../evidence/module-04a-r-offline-error.png).
4. Restored package networking and disabled the firewall chain. Independently confirmed emulator TCP access to the backend (`nc` exit 0) and host health `UP`.
5. Tapped “Try again” in the still-visible error screen. The app process PID remained `8827` across the recovery; no restart, relaunch, or navigation was used. After the request completed, the error/loading state was gone and the server room list was rendered. Captured [recovered lobby](../evidence/module-04a-r-recovered-lobby.png).
6. Opened the first existing room from that recovered list. [Recovered room/session details](../evidence/module-04a-r-recovered-room-session.png) show room `M02-1792735981360 QA Room`, active, ETB 25.0, WEEKLY (`Africa/Addis_Ababa`), capacity 100, and its existing DRAFT session with matching ETB 25.0/capacity 100 and stored UTC schedule.

The original retry-flow diagnosis exposed a confirmed defect: invalidating a failed FutureProvider retained the previous error branch during refresh, leaving Retry visible and permitting duplicate requests. The lobby now renders the loading branch during refresh (`skipLoadingOnRefresh: false`). A widget regression holds the retry request pending and verifies one retry request, no visible Retry action while pending, and successful completion. After this fix, the fixed APK was rerun through offline failure, Retry while offline (loading action disappears), connectivity restoration, independent emulator/backend probe, and an actual Retry tap in the same screen. The app process PID remained 11044 through the post-fix recovery; the recovered lobby and room/session detail were captured.

## Focused checks

- Auth remained valid through the same screen and process; no session-expired prompt appeared.
- Successful data replaced the error state; no stuck loading indicator remained after settlement.
- Error content contained only generic connectivity guidance; no credentials, tokens, or server internals appeared.
- Retry and room/session discovery are GET/read-only operations. No ticket purchase or gameplay action was exposed or taken.
- Lobby data is read with an auto-disposed Riverpod `FutureProvider`; refresh/retry invalidates it. No explicit Dio `CancelToken` is wired for a request if the provider is disposed mid-flight. Cancellation was not needed in this recovery flow and no cancellation failure was reproduced.
- No duplicate session or financial/game mutation was observed. The SELECT-only persistent snapshot before and after is byte-for-byte text-identical: 33 rooms, 18 sessions, 0 tickets/draws/claims/entitlements, and 19 wallet transactions. Existing player wallets and Agent #3 float/journals are included in the snapshot.

## Verification

- Real Android offline → online → actual Retry → recovered lobby → room/session details: **PASS**.
- Focused Flutter lobby tests: **6/6 PASS** (`test/features/lobby/lobby_screen_test.dart`), including error/retry recovery and pending duplicate-request guard.
- Flutter analyzer: **No issues found**.
- Persistent `bingo_qa` reconciliation: **IDENTICAL** to the Module 04A after snapshot; SQL contains SELECT statements only.
- Backend tests were not rerun because no backend code changed. The existing Module 04A full backend baseline is 146/146 PASS; Flutter baseline was 26/26 PASS.

## Evidence

- [Offline error screenshot](../evidence/module-04a-r-offline-error.png)
- [Retry pending/loading screenshot](../evidence/module-04a-r-retry-pending.png)`r`n- [Recovered lobby screenshot](../evidence/module-04a-r-recovered-lobby.png)
- [Recovered room/session screenshot](../evidence/module-04a-r-recovered-room-session.png)
- [Android results and exact checks](../evidence/module-04a-r-android-results.txt)
- [Flutter results](../evidence/module-04a-r-flutter-results.txt)
- [Read-only database after snapshot](../evidence/module-04a-r-database-after.txt), compared to the [Module 04A after snapshot](../evidence/module-04a-database-after.txt)

Smallest fix: lobby `AsyncValue.when` now sets `skipLoadingOnRefresh: false`; regression `pending retry cannot be submitted again` proves duplicate retry requests are prevented by the loading state. No ticket, wallet, room, or session was created or modified. No persistent database reset/truncate/delete, backend restart, commit, or push occurred. Module 02 remains INCOMPLETE; Module 03 remains COMPLETE FOR DEFINED QA SCOPE. Module 04B was not started.

