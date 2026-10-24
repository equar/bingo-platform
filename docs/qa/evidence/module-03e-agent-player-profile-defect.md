# Module 03E defect — agent access to player profile endpoint

Execution date: 2026-10-24. The issue was reproduced in the isolated MySQL `bingo_qa_test` integration suite; no persistent QA data was modified.

## Reproduction

The existing cross-role test issued an AGENT JWT and requested `GET /api/v1/me`. Before the fix, the response was HTTP 200 (expected 403). Both `/api/v1/me` and its `/api/v1/auth/me` alias were covered by a broad authenticated/public auth matcher and did not require the PLAYER role.

The regression was added to `AuthSecurityIntegrationTest.playerAndAgentTokensCannotCrossRoleBoundaries`. The pre-fix test failed with `Status expected:<403> but was:<200>`.

## Repair and verification

An initial strict `ROLE_PLAYER` matcher denied a retained finance-role self-profile request that existing tests intentionally allow; the focused suite caught this regression (expected 200, got 403). The final rule explicitly allows PLAYER, ADMIN, SUPPORT, FINANCE_VERIFIER, and FINANCE_APPROVER, and denies AGENT for both profile routes. Updated tests require AGENT access to return 403, anonymous and malformed JWT access to return 401, and existing player and finance self-profile access to remain successful. Focused and full backend results after repair are in [Module 03E backend results](module-03e-backend-results.txt).
