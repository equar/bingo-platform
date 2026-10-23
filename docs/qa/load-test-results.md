# Load and Concurrency Test Results

**Last checked:** 2026-10-22
**Overall status:** **BLOCKED; no capacity claim is supported**

> The latest follow-up in [qa-execution-plan-and-results.md](./qa-execution-plan-and-results.md) did not run a load test. No load run or capacity measurement has been performed; the 1,000-player setting remains unverified.

## Results

| Scenario / metric | Status | Result |
|---|---|---|
| 1,000 concurrent players | BLOCKED | Not run. No load harness/script and no isolated load environment were available. |
| 10-ticket-per-player limit under concurrency | BLOCKED | Not measured under load. A configured maximum is not proof of enforcement under concurrent requests. |
| Concurrent ticket purchases | PASS (focused case) | The targeted real-HTTP same-actor/key duplicate-sale regression now passes: both requests receive the same result, with one ticket, one float debit, and one commission accrual. This does not substitute for load testing many concurrent players. See G3 in [production-readiness-report.md](./production-readiness-report.md). |
| Concurrent deposits, approvals, settlements, and transfers | BLOCKED | Not run against an isolated backend/database. |
| Draw broadcast latency and ordering under load | BLOCKED | Authenticated real STOMP delivery, ordering, reconnect, and replay now pass the focused integration test; no load latency was measured. |
| Throughput, failure rate, and database contention | BLOCKED | No load run; no metrics exist. |
| Overselling, duplicate credits, lost updates, or negative balances at target capacity | BLOCKED | No capacity run. The focused same-key agent-sale test now verifies no duplicate ticket, float debit, or commission accrual; other invariants at target capacity remain unmeasured. |

## Environment and safety

- Docker 28.3.3 and Testcontainers MySQL 8.0.36 were available for integration tests.
- `k6` was not installed or discoverable on `PATH`, and no repository load script was present.
- Ports 3306 and 8080 were already in use by processes not started for this task. They were not used for load or money-flow testing.
- No disposable production-like load environment, test accounts, or approved test dataset were supplied.
- No throughput, latency, capacity, or database-contention numbers are reported because no load test was run.

## Required setup and rerun

Provision a dedicated, disposable backend/database deployment with isolated test accounts and telemetry. Add and review a load script that provisions valid players, exercises the real purchase/deposit/approval/settlement/transfer APIs, applies idempotency keys, checks the 10-ticket limit, and validates financial invariants. Do not aim the test at a shared or production service.

After `k6` and the reviewed script are available, the planned command is:

```powershell
k6 run --vus 1000 --duration 10m bingo-backend/tests/load/bingo-platform.js
```

That path is a prerequisite target, not an existing script in this repository. The run remains BLOCKED until the script and isolated environment exist. Record p50/p95/p99 latency, throughput, errors, draw delivery latency, database pool/lock metrics, and before/after ledger invariants with the eventual results.

The focused regression test is independently rerunnable now:

```powershell
Set-Location .\bingo-backend
mvn -Dtest=AgentTicketSaleHttpIntegrationTest#concurrentDuplicateSaleReturnsTheSameResultWithoutADuplicateDebit test
```

## Capacity conclusion

**Measured capacity: none.** The configured 1,000-player session capacity is not validated. No GO decision should be based on configuration alone.
