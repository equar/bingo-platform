# Runtime Validation Report

**Generated:** 2026-10-22  
**Target:** `D:\GitHub\bingo-platform`  
**Release decision:** **NEEDS_SIGNOFF / NO-GO**

## Summary

| Step | Status | Exit Code | Details |
|---|---|---:|---|
| Startup | PASS | 0 | Isolated backend on `127.0.0.1:18080`; `/actuator/health` returned HTTP 200. Admin UI on `127.0.0.1:15173` returned HTTP 200. |
| Integration | PASS | 0 | Current backend source, isolated MySQL 8.0.36 and Mailpit: 88 tests, 0 failures, 0 errors, 0 skipped; executable JAR packaged. |
| Admin browser E2E | PARTIAL | 0 | `sessions-admin.spec.ts`: 1 passed, 0 failed. Four other discovered browser tests remain unrun. |
| Android player E2E | PARTIAL | 0 | `setup` and `support` phases passed on `emulator-5554`. Each run reported 5 passed/0 failed/0 skipped, with only the selected journey active; four other phase cases returned early by design. |

**Overall:** NEEDS_SIGNOFF. The executed checks passed, but required browser/device coverage, full financial reconciliation, load testing, V14 recovery validation, and production/legal/operational approvals remain outstanding.

## Environment and validation stack

```yaml
environment:
  docker: AVAILABLE — Docker CLI operated disposable MySQL/Mailpit containers; Java Testcontainers could not discover the Docker engine from this Windows host.
  node: AVAILABLE — Node.js 22.12.0
  playwright: AVAILABLE — Chromium installed; targeted Playwright test exited 0.
  infra-tier: PRIMARY(Docker-based) — disposable MySQL 8.0.36 and Mailpit; no embedded database fallback.
  browser-tier: PRIMARY(Playwright) — one targeted admin journey ran; browser tier is partial, not skipped.
startup: PASS — isolated Spring backend, `/actuator/health` HTTP 200; admin UI HTTP 200.
integration: PASS — exit_code: 0, passed: 88, failed: 0, skipped: 0; full backend unit/integration/packaging scope.
e2e: PARTIAL — exit_code: 0 for each final command; admin: 1 passed, 0 failed; Android: setup and support journeys passed, with four phase-filtered cases returning early in each run.
overall: NEEDS_SIGNOFF — executed checks passed, but mandatory release gates remain incomplete.
```

The follow-up used disposable synthetic accounts and a loopback-only database/backend/admin environment. Android access used temporary emulator port forwarding, removed after each run. No production or shared financial service or payment provider was used. Credentials were kept outside the repository and omitted from test output and this report.

## Legacy test reuse and changes

| Test file | Decision | Change reason |
|---|---|---|
| `bingo-admin/tests/e2e/sessions-admin.spec.ts` | reuse-with-edits | `/api/v1/admin/rooms` is paginated; setup and cleanup now read `content`, request a bounded page, and fail explicitly on unsuccessful list responses. |
| `bingo-app/integration_test/local_e2e_test.dart` | reuse-with-edits | Await the invalid-login response before retrying, use a separate synthetic invalid phone, verify configured fields without printing credentials, and explicitly switch to registration and enter the secondary player's display name. |

## Test evidence

- Backend verification: isolated-source `mvn -q clean verify`; 88 tests passed and executable JAR packaged.
- Admin E2E: `npm exec playwright test tests/e2e/sessions-admin.spec.ts`; 1 passed, exit code 0.
- Android setup: `flutter test integration_test/local_e2e_test.dart -d emulator-5554` with isolated local defines and `BINGO_E2E_PHASE=setup`; exit code 0.
- Android support: same integration test with `BINGO_E2E_PHASE=support`; exit code 0.
- Flutter analyzer: `flutter analyze`; no issues found.
- Related status and remaining release gates: [qa-execution-plan-and-results.md](./qa-execution-plan-and-results.md), [release-checklist.md](./release-checklist.md), and [release-blockers.md](./release-blockers.md).
- Playwright traces, screenshots, and video remained disabled; no credential-bearing E2E artifacts were produced.

## Issues found and disposition

| Issue | Disposition |
|---|---|
| Admin room API returns a paginated object rather than an array. | Fixed the E2E setup and cleanup to use `content`; targeted browser test now passes. |
| Mobile setup did not wait for authentication failure and omitted the registration-mode transition for its synthetic secondary account. | Fixed the integration test flow; setup and support phases now pass. No production-source change was required for these test defects. |
| Full browser/mobile scope, financial reconciliation, 1,000-player load, V14 backup/restore, legal approval, and production deployment evidence remain incomplete. | Release blocker; do not ship until evidence and owner sign-off are recorded. |
