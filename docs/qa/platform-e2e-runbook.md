# Bingo Platform Real E2E Runbook

This runbook uses the repository's guarded runners for actual Admin Chromium, Player Android, Agent Android, Spring Boot, and local MySQL workflows. Component tests and mocked API checks are supplemental only. Do not substitute them for scenarios marked as real E2E.

## Safety gates

1. Work only in the normal Windows user profile that owns the Android SDK and emulator.
2. Verify the current backend source revision and worktree before starting:

   ```powershell
   git rev-parse HEAD
   git -C bingo-backend status --short
   Import-Module .\scripts\qa\AndroidTestBackendPolicy.psm1 -Force
   Get-AndroidTestBackendSourceFingerprint -RepositoryRoot (Get-Location).Path
   ```

3. Run the existing guarded preflight; do not start a backend manually:

   ```powershell
   .\scripts\qa\Start-AndroidTestBackend.ps1
   ```

   It validates the local `bingo_qa_test` target, authenticated MySQL identity and grants, Flyway version, source fingerprint, and the fixed `android-test` launch settings. It refuses to start when database and source Flyway versions differ. Do not run Flyway migrate/clean, change guards, or point the backend at another schema to get past this gate.
4. Proceed only after fresh identity evidence confirms `127.0.0.1:3306/bingo_qa_test`, the expected schema-scoped account, a successful matching Flyway version, and an isolated backend on `127.0.0.1:8082`. Confirm scheduler and financial posting/simulation flags are disabled.
5. Use only ignored local credentials and existing approved fixtures. Never print or retain secrets. Capture database snapshots before/after the allowed scenario. Leave retained records untouched; do not reset schemas or delete fixtures.

## Ordered execution

### A. Environment and isolation preflight

Run the guarded backend launcher above. If it refuses startup, stop all backend-dependent scenarios and record the precise gate. The source at this run's `HEAD` requires Flyway V23 while the local Android/Admin schema was V22; this task did not migrate it.

### B. Read-only authentication and client smoke tests

After the backend identity file is fresh and health is `UP`, run only the guarded clients:

```powershell
.\scripts\qa\Invoke-AgentAndroidAuthenticationE2E.ps1 -EnvironmentName 'android-test'
```

The Agent runner checks the approved ACTIVE and PENDING fixtures, exercises the Agent UI and process restart, and records sanitized results. It does not create Agent fixtures.

```powershell
.\scripts\qa\Invoke-AndroidPlayerFunctionalE2E.ps1 -UseLocalSyntheticFixtures
```

This runner uses the ignored local Player/Admin fixture configuration, creates one uniquely named OPEN session through the normal Admin API, retains it, and reconciles it. It does not create a ticket, purchase, or financial posting. Run it only when retaining a QA session is authorized.

For Admin Chromium session scheduling, set credentials in the current PowerShell process from the ignored local `bingo-admin/.env.qa` file without echoing them, then run:

```powershell
.\scripts\qa\Invoke-AdminSessionSchedulingE2E.ps1
Remove-Item Env:\BINGO_E2E_ADMIN_EMAIL, Env:\BINGO_E2E_ADMIN_PASSWORD -ErrorAction SilentlyContinue
```

That runner starts its own QA-mode Vite server on port 5174, runs the specific real Chromium scenario, and reconciles the newly created/cancelled session and audit records. Do not use an unguarded general Playwright suite against a live database.

### C. Authorized nonfinancial workflows

Use the scenario-specific guarded runner and fixture approval for each workflow. Check the script's documented mutation scope before execution. Preserve all run-tagged records and reconcile expected API/UI state, audit rows, and unchanged wallets/transactions.

### D. Game lifecycle and cross-client scenarios

Use the actual Player/Agent Android apps and Admin Chromium against the same verified backend. Verify server state and MySQL invariants; a success toast alone is insufficient. Run duplicate, reconnect, role-denial, and cross-client cases only where an existing guarded runner and approved fixture exist.

### E. Financial, concurrency, and fault scenarios

Do not run financial posting, settlement, ticket purchases, prize credits, float transfers, or external payment flows in the current QA schema. These require a separately provisioned and explicitly authorized financial test environment with approved fixtures, idempotency/reconciliation controls, and read-only before/after checks. No runner in this repository grants that authorization.

For controlled latency/offline/malformed-response tests, use a named fault-injection setup that keeps the real backend in the flow; label the injected fault. Do not add retries to financial mutations.

### F. Regression and supplemental checks

Run database-free tests independently of E2E:

```powershell
Push-Location bingo-admin; npm test; npm run lint; npm run build; Pop-Location
Push-Location bingo-agent; flutter test; flutter analyze; Pop-Location
Push-Location bingo-app; flutter test; flutter analyze; Pop-Location
```

Mocked Android/component tests must be reported as mocked. Do not label them as backend, MySQL, or cross-client E2E. Maven integration tests require the separate guarded `bingo_qa_maven_test` database/account; do not run them against `bingo_qa_test`.

## Completion criteria

A scenario is PASS only when its required real UI action, real API outcome, expected MySQL persistence, authorization/negative behavior, and relevant before/after invariants are all evidenced. Use BLOCKED for an unmet safety, environment, approval, or fixture gate and NOT RUN for unexecuted performance or unsupported test work. A release assessment remains NOT READY while critical scenarios are blocked or unrun.

See the [current scenario matrix](platform-e2e-scenario-matrix.md) and [2026-11-03 run report](evidence/platform-e2e-runs/QA_PLATFORM_20261103_1806/run-report.md).
