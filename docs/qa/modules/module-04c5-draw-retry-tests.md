# Module 04C-5 — Draw Retry Integration Tests

Disposition: **NOT RUN — blocked by the database isolation/authentication gate**

Only the dedicated disposable Maven schema `bingo_qa_maven_test` may be used for these destructive integration tests. It is not provisioned. No existing Android/Admin fixtures were changed, and no API idempotency contract was implemented.

## Required focused cases

The existing `DrawRetrySafetyIntegrationTest` is a starting point, but it is not a completed R01–R10 suite:

| Case | Current state / required assertion |
| --- | --- |
| R01–R03 | Existing retry class exercises an authorized bodyless draw, server result, and matching audit. Must be rerun and assert status, selected number, audit payload, operation ID, and persisted timestamp. |
| R04 | Existing class checks an immediate retry is HTTP 409 `DRAW_NOT_AVAILABLE` and count remains one. |
| R05 | Existing class sleeps for the 10-second interval before retry. This is too slow/flaky for the requested deterministic test-time control; refactor around a controlled `Clock`/time source or a directly injectable service time without changing production API behavior. The HTTP path currently obtains `Instant.now()` and no shared clock abstraction was found. |
| R06 | Existing class checks two competing service calls at the same instant persist one draw/audit. Complete this with distinct eligible time handling and assert DB number/sequence uniqueness. |
| R07 | Add/retain assertions that rejected terminal/not-due attempts leave draw and audit counts unchanged. |
| R08 | Existing 04C evidence reports role denials; include focused role checks in the isolated suite and assert no persistence. |
| R09–R10 | Simulate a lost first response, then show a retry at a later due interval is a new sequence/operation rather than replaying the first result. Document the endpoint as non-idempotent. A retry during the interval is rejected; an interval-later retry is not guaranteed to return the original result. |

The Admin UI's pending-state guard does not define network-retry semantics. No request ID, replay store, or approved idempotency contract exists. Product/API approval remains required before any future contract change.

## Execution record

04C-4 selected this class alone, but MySQL rejected the configured `bingo_test` account before the test class initialized. Surefire recorded 2 errors and no test body ran. Module 04C-5 has not attempted it again because a valid dedicated schema/account is not available. Focused lifecycle/draw tests and the full Maven suite were also not run. This report contains no pass count for R01–R10.

See [database isolation gate](module-04c5-database-isolation.md) and [prior attempt evidence](../evidence/module-04c4-test-attempt.txt).
