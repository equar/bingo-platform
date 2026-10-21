# Phase 6 Security Validation

**Date:** 2026-10-21
**Result:** **NOT CLEARED FOR RELEASE**

This is a bounded validation record for Phase 6, not a penetration test or a complete security audit. No secrets, passwords, PINs, or bearer tokens were added to the reports or emitted as test evidence.

## Executed security-relevant checks

| Check | Status | Evidence |
|---|---|---|
| Agent ticket-sale endpoint rejects an anonymous caller | PASS | The HTTP regression test received 401 for a sale without an access token. |
| Sequential same-key agent sale replay does not duplicate ticket/float debit | PASS | The normal HTTP flow replayed the same ticket and confirmed one ticket plus expected float movement. |
| Insufficient agent float does not create/debit a sale | PASS | The request returned 4xx; ticket and commission counts and float balance remained unchanged. |
| Existing WebSocket JWT channel-interceptor unit checks | PASS | The backend full-suite run included 2 passing `GameWebSocketJwtChannelInterceptorTest` cases. This is unit-level evidence only. |
| Real authenticated STOMP session and protected subscription | FAIL | The real client connection closed before receiving `CONNECTED`. Protected-channel behavior was therefore not established at the broker boundary. |
| Concurrent idempotency and financial integrity | FAIL | Concurrent same-actor/key agent sale produced HTTP 500 from a duplicate key in `idempotency_records`. |
| Production security scans / external penetration testing | BLOCKED | Not part of the executed tests; no external assessment evidence is available in this Phase 6 run. |
| Login throttling/lockout, actuator authorization, JWT secret startup policy, and secret scanning | BLOCKED | Those earlier-phase controls were not re-exercised as dedicated Phase 6 gates in this run. Do not treat historic remediation claims as current independent verification. |

## Important security findings

### Concurrent idempotency failure

Two simultaneous authenticated agent sale requests using the same idempotency actor/key do not both receive the prior successful result. One returns 500 while MySQL rejects a duplicate row under `uk_idempotency_records_actor_key`. This blocks release until the backend safely resolves the concurrent insert/replay race and tests confirm no duplicate financial movement.

Reproduction:

```powershell
Set-Location .\bingo-backend
mvn -Dtest=AgentTicketSaleHttpIntegrationTest#concurrentDuplicateSaleReturnsTheSameResultWithoutADuplicateDebit test
```

### Real-time authentication remains unverified

The integration test authenticates through the application and starts a real STOMP client, but the WebSocket closes before STOMP `CONNECTED`. The available evidence does not identify whether the cause is origin, handshake, security configuration, token handling, or another issue. Do not infer a root cause from the disconnect alone.

Rerun after diagnosis:

```powershell
Set-Location .\bingo-backend
mvn -Dtest=AuthenticatedStompIntegrationTest test
```

## Credential handling and E2E constraints

- Admin E2E credentials were not available in the environment.
- Playwright discovery was performed with non-secret placeholders only; no browser scenario was executed with those values.
- Existing services on ports 3306, 8080, and 5173 were not assumed to be test-owned and were not used for financial flows.
- No tokens, passwords, or PINs were written to logs/reports or included in command output.
- The Playwright configuration now retains trace, screenshot, and video artifacts on test failure. This was configuration-only verification; no browser failure artifacts were generated.

## Required security release gates

1. Resolve and rerun the concurrent sale idempotency test, including persisted wallet/float/ledger checks.
2. Establish real STOMP authentication, prove invalid/expired token rejection, channel authorization, reconnect, replay, and ordering.
3. Run dedicated Phase 1 security regression tests on the release candidate for JWT configuration, auth throttling, Actuator access, and credential handling.
4. Complete an independent security review and deployment configuration review before any GO decision.

**Decision:** NO-GO until the FAIL findings are fixed and all blocked mandatory gates have passing evidence.
