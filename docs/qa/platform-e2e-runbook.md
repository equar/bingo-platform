# Guarded Real E2E Runbook

Functional completion requires the actual applicable client UI, Spring Boot
API, and authorized isolated MySQL state. Unit, widget, analyzer, mocked API,
and static checks are supplemental; they do not prove a real cross-system
scenario.

**Latest recorded run:** `QA_PLATFORM_20261103_1806` stopped before backend
startup because checked-out source requires Flyway V23 while local
`bingo_qa_test` is V22. No migration or database write was attempted. See the
[current scenario matrix](platform-e2e-scenario-matrix.md) and
[run report](evidence/platform-e2e-runs/QA_PLATFORM_20261103_1806/run-report.md).

## Database boundaries and safety

| Target | Permitted purpose | Restrictions |
|---|---|---|
| `bingo` | Local development only | Not functional QA or destructive integration tests. |
| `bingo_qa` | Persistent historical QA data | Read-only comparisons only; preserve existing records. |
| `bingo_qa_test` | Local guarded Android/Admin functional E2E | Verify exact local host, port, schema, runtime account, grants, and Flyway version. Player ticket purchases use the normal production path. Never reset or clean it. |
| `bingo_qa_maven_test` | Destructive Maven integration tests | Dedicated disposable schema/account only. Provisioning is not verified; do not run the suite until the guard and exact target are verified. |

## Ignored local runner configuration

Keep test credentials only in Git-ignored local files:

- Backend connection: `bingo-backend/.env.test`.
- Player runner: `bingo-app/e2e-secrets.json`, with
  `BINGO_QA_PLAYER_001_PHONE` and `BINGO_QA_PLAYER_001_PIN`.
- Agent runner: `bingo-agent/e2e-secrets.json`, with
  `BINGO_QA_APPROVED_AGENT_PHONE`, `BINGO_QA_APPROVED_AGENT_PIN`,
  `BINGO_QA_PENDING_AGENT_PHONE`, and `BINGO_QA_PENDING_AGENT_PIN`.
- Admin runner: `bingo-admin/.env.qa`, with `BINGO_E2E_ADMIN_EMAIL` and
  `BINGO_E2E_ADMIN_PASSWORD`. The guarded Admin script expects those two values
  in the current PowerShell process; it does not load `.env.qa` itself.

To load only the Admin variables without echoing their values:

```powershell
foreach ($line in Get-Content .\bingo-admin\.env.qa) {
    if ($line -match '^\s*(BINGO_E2E_ADMIN_EMAIL|BINGO_E2E_ADMIN_PASSWORD)=(.*)\s*$') {
        Set-Item -Path "Env:\$($matches[1])" -Value $matches[2].Trim().Trim('"').Trim("'")
    }
}
```

The tracked `bingo-app/e2e-secrets.example.json` uses legacy
`BINGO_E2E_PRIMARY_*` names and does not satisfy the guarded Player runner's
current `BINGO_QA_PLAYER_001_*` configuration; do not copy it unchanged.
`bingo-admin/.env.example` contains placeholders only. Never put credential
values in command arguments, output, evidence, or this registry.

Use the normal Windows profile that owns the Android SDK and emulator. Before
any runner:

1. Verify the source and worktree:

   ```powershell
   git rev-parse HEAD
   git -C bingo-backend status --short
   Import-Module .\scripts\qa\AndroidTestBackendPolicy.psm1 -Force
   Get-AndroidTestBackendSourceFingerprint -RepositoryRoot (Get-Location).Path
   ```

2. Use the existing guarded launcher; do not start a backend manually:

   ```powershell
   .\scripts\qa\Start-AndroidTestBackend.ps1
   ```

   It validates the fixed `127.0.0.1:3306/bingo_qa_test` target, authenticated
   identity/grants, Flyway version, source fingerprint, and `android-test`
   settings. It must refuse startup on a source/database migration mismatch.
   Do not migrate, clean, weaken guards, or change schemas to get past it.

3. Proceed only when fresh evidence confirms the exact runtime identity,
   expected local account scope, current Flyway version, and isolated backend
   on `127.0.0.1:8082`. Confirm scheduler configuration and the runtime database
   identity. Player ticket purchases use the same path as production.

4. Capture read-only before/after state for the authorized scenario. Use only
   approved existing accounts/fixtures; retain run-owned records. Never print
   credentials or delete/reset historical records.

### Local identity exceptions

The owner-approved local functional-QA policy accepts `ALL PRIVILEGES` only on
the local `bingo_qa_test` schema; do not change that grant or allow cross-schema
access. The machine-local MySQL `root` exception is limited to the guarded
`android-test` backend on `127.0.0.1:3306/bingo_qa_test`, with secure local
password entry. It does not authorize Maven tests, another schema, persistent
QA, a verification clone, shared services, or production. Verify the effective
catalog and authenticated account at runtime; a profile name alone is not
proof of isolation.

The separate Maven profile uses `BINGO_MAVEN_MYSQL_*` and
`bingo_qa_maven_test`. Do not load `BINGO_TEST_MYSQL_*` for Maven. The Maven
suite contains broad fixture cleanup and `A00FlywayUpgradeRecoveryIntegrationTest`
calls Flyway `clean()`, so never aim it at `bingo`, `bingo_qa`, or
`bingo_qa_test`. See [backend integration-test safeguards](../../bingo-backend/README.md).

## Guarded client procedures

### Agent Android authentication

After the backend identity file is fresh, run:

```powershell
.\scripts\qa\Invoke-AgentAndroidAuthenticationE2E.ps1 -EnvironmentName 'android-test'
```

The launcher verifies the runtime database identity and requires the exact
approved and pending Agent fixtures. It does not create accounts or enable
financial posting. Missing fixtures are a BLOCKED result, not a reason to use
different accounts or write to persistent `bingo_qa`.

### Player Android

After the same preflight, run:

```powershell
.\scripts\qa\Invoke-AndroidPlayerFunctionalE2E.ps1 -UseLocalSyntheticFixtures
```

This uses the real Flutter app on `emulator-5554`. The guarded runner can create
a uniquely named OPEN session through the normal Admin API and retain it for
the scenario. It does not insert a ticket, purchase, debit a wallet, create a
claim, or settle a prize. Fixture credentials belong only in ignored local
configuration.

### Admin Chromium

Run the scenario-specific guarded runner from normal VS Code PowerShell, for
example:

```powershell
.\scripts\qa\Invoke-AdminSessionSchedulingE2E.ps1
```

Load the two Admin values as shown in [local runner configuration](#ignored-local-runner-configuration);
do not put them on a command line or in logs. The runner starts its QA-mode
Vite server, uses real Chromium and the isolated backend, and reconciles the
created/cancelled session and audit records. Do not use an unguarded general
Playwright suite against a live database.

### Backend integration tests

Follow the [backend README](../../bingo-backend/README.md) for database-free
commands and the separately guarded Maven schema. If the disposable target or
account is missing or uncertain, do not run database-backed Maven tests.

## Prohibited financial and unsafe fixture scenarios

Player ticket purchases are permitted in the current QA schema and use the
normal production API path. Do not run deposits, wallet credits/adjustments,
transfers, Agent float funding, prize settlement/payout, external payments, or
other synthetic financial posting without a separately provisioned and
explicitly approved financial test environment, approved fixtures,
idempotency/reconciliation controls, and read-only before/after checks.

Do not create a claim ticket by purchase or Agent sale: those paths debit wallet
or float and may accrue commission. Any future nonfinancial claim fixture must
meet the [fixture constraints and approvals](qa-blockers-and-approvals.md#nonfinancial-claim-fixture-constraints).
Never inject draw numbers or fabricate draw history.

The legacy [`scripts/run-local-e2e.ps1`](../../scripts/run-local-e2e.ps1)
defaults to the development backend, has no schema/account preflight, and
contains financial workflows. Do not use it against development, persistent
QA, shared, or production data, or describe a test-harness run as device E2E.

## Supplemental checks and completion

Safe database-free checks may be run independently:

```powershell
Push-Location bingo-admin; npm test; npm run lint; npm run build; Pop-Location
Push-Location bingo-agent; flutter test; flutter analyze; Pop-Location
Push-Location bingo-app; flutter test; flutter analyze; Pop-Location
```

Report mocked/unit/widget/analyzer results as supplemental. A real E2E **PASS**
requires the applicable actual UI action, real API outcome, expected MySQL
persistence, authorization/negative behavior, and relevant before/after
invariants. Use **BLOCKED** for unmet environment, approval, isolation, or
fixture gates and **NOT RUN** when a scenario was not executed. A release
assessment remains NO-GO while critical scenarios are blocked or unrun.

See the [current scenario matrix](platform-e2e-scenario-matrix.md) for stable
cross-client IDs and the latest run status. Historical module results are not
promoted to current PASS.
