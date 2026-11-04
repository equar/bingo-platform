# Device Runtime Checks — QA_PLATFORM_20261103_1806

Both real Android debug apps were launched on `emulator-5554` with `API_BASE_URL=http://10.0.2.2:8082`; Player purchases were explicitly disabled. No backend was listening, no login or business mutation was performed, and these launches are not backend E2E.

| App | Build / launch | Hot restart | Runtime errors |
|---|---|---|---|
| Agent | Debug APK built and installed; login UI launched. | Succeeded. | None reported by Dart runtime error tool. |
| Player | Debug APK built and installed; unauthenticated UI launched. | Succeeded. | None reported by Dart runtime error tool. |

The debug sessions were stopped after restart; only the exact locally launched Flutter processes and their respective app packages were stopped. No app data was cleared. The mocked integration tests are recorded separately in `agent-mocked-android-smoke.log` and `player-mocked-android-smoke.log`.

The cold debug launches logged skipped-frame warnings (Agent: 311 then 96; Player: 289 then 107). These are unprofiled debug-startup observations, not a production performance measurement; no baseline or steady-state trace was captured.
