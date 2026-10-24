# Module 03A — Concurrent duplicate registration defect

## Reproduction

Two simultaneous real-MySQL integration requests used the same normalized phone. The unique database constraint prevented a second user, but the losing request surfaced as HTTP 500. The test report captured statuses `[201, 500]` before the fix.

## Root cause

`AuthService.register` performed an existence check before insert. Two concurrent transactions could both observe that the phone was unused. The MySQL unique key then rejected one insert, and the generic data-integrity exception handler returned HTTP 500.

## Fix

`AuthService.register` now translates only the SQLState `23000` violation for `uk_users_normalized_phone` into `PHONE_ALREADY_REGISTERED` / HTTP 409. Other database integrity failures still propagate as server errors. The database unique constraint remains the concurrency guard.

## Regression result

`AuthSecurityIntegrationTest.concurrentDuplicatePlayerRegistrationCreatesOnlyOneAccount` passes against real MySQL 8.0.36 in `bingo_qa_test`: one response is 201, one is 409, and SQL assertions confirm exactly one user and one wallet. The full backend suite then passed 131/131.

## Related input validation repair

The original phone normalizer silently removed arbitrary non-digit characters, so a value containing letters could collapse into a valid phone. The backend now accepts only digits, one optional leading `+`, and the existing separator characters; the Flutter validator applies the same accepted formats. The MySQL integration test and actual Android UI reject malformed characters.
