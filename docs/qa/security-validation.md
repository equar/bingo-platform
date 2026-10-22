# Phase 6 Security Validation

**Date:** 2026-10-22
**Result:** **NOT CLEARED FOR RELEASE**

> The broader independent security and release review is recorded in [final-independent-qa-review.md](./final-independent-qa-review.md). Follow-up D4 trusted-proxy throttling and D5 issued-JWT Actuator authorization tests passed; overall release is still NO-GO.

## D4 follow-up status

After the reviewed commit, authentication attempt buckets were changed to count failures rather than successful logins and to resolve `X-Real-IP` only when the request peer matches configured trusted proxy addresses. The checked-in Nginx proxy overwrites the forwarding header with the connected client address. The focused MySQL-backed command passed 11 tests: 7 authentication integration tests, 1 throttle integration test, and 3 proxy-resolver tests. A clean backend build from a temporary copy passed 85 tests and completed packaging against isolated MySQL/Mailpit containers. Confirm actual deployment proxy addresses before release.

This is a bounded validation record for Phase 6, not a penetration test or a complete security audit. No secrets, passwords, PINs, or bearer tokens were added to the reports or emitted as test evidence.

## Executed security-relevant checks

| Check | Status | Evidence |
|---|---|---|
| Agent ticket-sale endpoint rejects an anonymous caller | PASS | The HTTP regression test received 401 for a sale without an access token. |
| Sequential same-key agent sale replay does not duplicate ticket/float debit | PASS | The normal HTTP flow replayed the same ticket and confirmed one ticket plus expected float movement. |
| Insufficient agent float does not create/debit a sale | PASS | The request returned 4xx; ticket and commission counts and float balance remained unchanged. |
| Existing WebSocket JWT channel-interceptor unit checks | PASS | The backend full-suite run included 2 passing `GameWebSocketJwtChannelInterceptorTest` cases. This is unit-level evidence only. |
| Real authenticated STOMP session, subscription, draw replay, and invalid-token denial | PASS | Real CONNECT, subscription, draw sequence, replay after reconnect, and malformed-token rejection pass. Bearer JWT authentication and the endpoint origin allowlist remain active. |
| Concurrent idempotency and financial integrity | PASS | Concurrent same-actor/key agent sale requests replay the same result; the test verifies one ticket, one float debit, and one commission accrual. |
| D4 failure-based throttling and trusted-proxy address resolution | PASS | Focused MySQL-backed auth command passed 11 tests, including network/account lockout, recovery, database bucket contention, and configured proxy behavior. |
| D5 issued PLAYER/AGENT/ADMIN JWT access to Actuator | PASS | The focused auth integration run verified issued-role bearer tokens against protected Actuator routes. |
| Admin temporary-password change, expiry, and reuse | PASS | The DB-backed forced-change/expiry tests and actual Mailpit SMTP delivery/login/reuse flow passed in the full backend test phase. |
| Production security scans / external penetration testing | BLOCKED | Not part of the executed tests; no external assessment evidence is available in this Phase 6 run. |
| JWT secret startup policy | PASS | The clean 85-test backend build included missing/weak/strong JWT secret configuration tests and completed packaging. |
| Secret scanning and external security review | BLOCKED | No current secret scan or independent penetration/security assessment was performed for this follow-up. |

## Resolved failures

### Concurrent idempotency race

The original failure was caused by relying on a process-local `ReentrantLock`. It did not coordinate multiple backend instances, and it was released when `IdempotencyService.execute()` returned—before the surrounding transactional service method committed. A concurrent request could therefore observe no committed idempotency row and collide on the unique constraint.

The fix now applies a database pessimistic lock to the actor row and performs the idempotency record lookup as a locking/current read inside a transaction. The lock remains held through the enclosing transaction commit. The focused regression and full suite both pass:

```powershell
Set-Location .\bingo-backend
mvn -Dtest=AgentTicketSaleHttpIntegrationTest#concurrentDuplicateSaleReturnsTheSameResultWithoutADuplicateDebit test
```

### STOMP bearer authentication

The server's default STOMP CSRF channel interceptor required an HTTP-session CSRF token on CONNECT, while the platform's clients authenticate with a bearer JWT in the STOMP CONNECT frame. The WebSocket security configuration now substitutes the CSRF channel interceptor while keeping `GameWebSocketJwtChannelInterceptor` mandatory and retaining configured origin restrictions. The real integration test verifies valid authentication, draw delivery/order/replay, and rejection of a malformed JWT. It also uses the application's configured Jackson mapper so the test client can deserialize the `Instant` in draw events.

Verification:

```powershell
Set-Location .\bingo-backend
mvn -Dtest=AuthenticatedStompIntegrationTest test
mvn clean verify
```

## Credential handling and E2E constraints

- Admin E2E credentials were not available in the environment.
- Playwright discovery was performed with non-secret placeholders only; no browser scenario was executed with those values.
- Existing services on ports 3306, 8080, and 5173 were not assumed to be test-owned and were not used for financial flows.
- No tokens, passwords, or PINs were written to logs/reports or included in command output.
- The Playwright configuration now retains trace, screenshot, and video artifacts on test failure. This was configuration-only verification; no browser failure artifacts were generated.

## Required security release gates

1. Retain the clean backend verification and G6 Mailpit/MySQL evidence on the release candidate; verify production secret/proxy configuration with the deployment owner.
2. Complete an independent security review and deployment configuration review before any GO decision.

**Decision:** The two Phase 6 security-relevant failures are resolved and verified. Overall production release remains NO-GO until the blocked mandatory gates and independent security review have passing evidence.
