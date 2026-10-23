# Module 02I — Safe transfer fixtures and policy verification

**Disposition: policy and fixture assessment complete; persistent positive transfer scenarios remain BLOCKED. Module 02 remains INCOMPLETE.** This work is limited to wallet transfer policy enforcement and safe fixture feasibility. No persistent records, policy values, wallet balances, or production posting settings were changed.

## Existing evidence and supported lifecycle

The transfer flow is player-to-player: a PLAYER initiates a transfer to another PLAYER by phone number. The backend creates a completed transfer and paired debit/credit wallet ledger transactions in one transaction, guarded by wallet row locks and idempotency. The admin transfer list/detail are read-only. Transfer policy history is versioned; updating it requires ADMIN, is audit intercepted, and records the actor/reason. There is no transfer-specific maker-checker approval or eligible-user cohort in the current policy model: policy settings are global enabled/date/currency/amount-limit values.

The five retained `bingo_qa.wallet_transfer_policies` versions all remain disabled, ETB, expired, and set to 0.00 for both max-single and daily limits. Existing persistent QA has no transfer or linked transfer-ledger rows. The policies may not be enabled or date-extended without explicit Product/Finance/QA policy approval.

| Policy ID | Purpose/scope | Enabled | Currency | Max single | Daily outgoing | Stored effective window | Eligible users / required roles |
| --- | --- | --- | --- | ---: | ---: | --- | --- |
| 1 | Version of global player transfer controls | No | ETB | 0.00 | 0.00 | 1999-12-31 21:00:00 to 2000-01-01 21:00:00 | Any PLAYER may initiate/receive if policy permits; ADMIN required to read/change admin policy settings |
| 2 | Version of global player transfer controls | No | ETB | 0.00 | 0.00 | 1999-12-31 21:00:00 to 2000-01-01 21:00:00 | Same global scope/roles |
| 3 | Version of global player transfer controls | No | ETB | 0.00 | 0.00 | 1999-12-31 21:00:00 to 2000-01-01 21:00:00 | Same global scope/roles |
| 4 | Version of global player transfer controls | No | ETB | 0.00 | 0.00 | 1999-12-31 21:00:00 to 2000-01-01 21:00:00 | Same global scope/roles |
| 5 | Version of global player transfer controls | No | ETB | 0.00 | 0.00 | 1999-12-31 21:00:00 to 2000-01-01 21:00:00 | Same global scope/roles |

The effective-window values above are the stored database timestamps, all long expired. The `enabled=false` state independently rejects each version. Policy versions do not identify a QA-only player cohort or funding class.

## Policy inspection and defects fixed

The transfer service already rejected disabled policies and amounts above the maximum; thus a zero max-single amount rejects all positive transfer attempts. Before this work, it did not enforce `effective_from` or `effective_until`. Also, policies sharing a `created_at` timestamp could be selected nondeterministically. This mattered because a newer enabled/expired version could be masked by an older disabled version, preventing reliable policy evaluation.

The service now rejects a policy that is not yet effective or whose end time has arrived (`TRANSFER_POLICY_INACTIVE`). “Latest” policy retrieval and history now sort by creation time and descending ID as a deterministic tie-break. The focused integration regression covers disabled, expired-enabled, and zero-max policies and confirms no transfer/ledger row or balance movement results. Daily-limit zero semantics still need an explicit Product/Finance decision: the current service treats zero as no daily cap; positive values are enforced. This work did not change that interpretation or limits.

| Policy property | Current implementation / finding |
| --- | --- |
| Purpose | Global controls for player wallet transfers |
| Enabled and effective window | Both now enforced at transfer time; `effective_until` is exclusive |
| Single amount | Positive amount must not exceed configured maximum; zero rejects positive transfers |
| Daily outgoing limit | Positive value is applied over a rolling 24-hour lookback; zero currently means no cap and needs owner confirmation |
| Currency and roles | Currency must match configured and wallet currency; sender/recipient must be players; admin settings require ADMIN |
| QA versus production | No transfer-specific QA-only enablement/funding mechanism is approved or configured. Existing QA deposit/adjustment simulations are separate guarded workflows; Agent #3 float is unrelated and prohibited as a player funding source. Production posting remains disabled. |
| Audit | Admin policy changes record actor/reason; transfer failures are audited by the transfer service audit aspect. Policy denial creates no transfer or ledger posting. |

## Fixture feasibility and required approvals

No approved safe persistent fixture path exists in the inspected requirements and retained QA state. A QA-only synthetic deposit workflow does exist and has prior evidence, but it requires a matched synthetic statement plus separate verifier/approver actions; the requirements do not authorize repurposing that workflow specifically to seed player-to-player transfer tests. Adjustments likewise have separate policy/accounting conditions. The separate agent float cannot fund a player wallet. No active QA-only transfer policy or written approval for the required transfer limits was found, so the conditional fixture criteria are not met.

Therefore no synthetic persistent player or transfer was created. No existing player wallet was altered. Module 02H’s positive browser detail, matching search/filter, refresh, and multi-row pagination remain **BLOCKED** (pagination was **NOT RUN**). They are not counted as PASS based on isolated test fixtures.

To unblock persistent positive E2E, Product and Finance must approve in writing: (1) QA-only synthetic funding source and eligible synthetic players; (2) a transfer policy version with enabled state, ETB/date window, single and daily limits, and expiry; (3) transfer daily-limit zero behavior; and (4) accounting and audit expectations for synthetic transfers. The policy owner must specify whether existing admin single-admin settings are acceptable for QA. No approval or configuration was inferred from prior deposit, adjustment, or agent-float workflows.

## Executed checks

| Check | Result |
| --- | --- |
| Existing Module 02H Chromium transfer admin run | 4/4 PASS: empty listing, invalid ID controlled 404, player denial, agent phone+PIN denial. Positive result/detail/refresh and persistent pagination remain blocked/unrun as above. Existing evidence reused; no redundant rerun. |
| Existing backend transfer regression | 1/1 PASS in 02H: idempotency, insufficient funds/failure audit, paired ledger entries, concurrent overspend, details/history and authorization. |
| Module 02I transfer integration regression | **WalletIntegrationTest 11/11 PASS** against `bingo_qa_test`; disabled, expired-enabled, and zero-max rejection returned expected errors; invalid recipient was rejected; sender/recipient history returned only the caller's debit/credit; existing coverage verifies idempotency, insufficient funds/failure audit, balanced paired ledger entries, concurrency, and admin access. |
| Full backend regression | **131/131 PASS**, zero failures/errors/skips; includes migration/OpenAPI coverage, all against guarded local `bingo_qa_test`. |
| Chromium admin rerun | **4/4 PASS** on QA ports 5174/8081. Listing/empty search/status/refresh, controlled invalid-ID response, and player/agent denials passed. The spec's SELECT-only snapshot comparisons found no changes to transfers, transfer ledger rows, wallet balances, or transfer policy settings. |
| Persistent DB verification | Chromium's read-only `bingo_qa` checks found no transfer or linked ledger rows and the five unchanged disabled/expired/zero-limit policy versions. No persistent mutation attempted. |
| Frontend tests/lint/build | Existing 02H results reused: 82/82 unit tests; lint and production build passed. No frontend code changed in 02I. |
| Android | NOT RUN; no mobile behavior or onboarding/login changed. |

The earlier Chromium run exercises the allowed 5174 QA admin and 8081 backend. It did not use admin port 5173. Integration database access is guarded to local MySQL 8.0.36 `bingo_qa_test`; no H2, Docker or Testcontainers were used.

Detailed Module 02I execution summary: [test results](../evidence/module-02i-test-results.txt) and [read-only database reconciliation](../evidence/module-02i-database-reconciliation.txt).

## Module 02 closure recommendation

Keep Module 02 **INCOMPLETE**. The 02H retained-fixture details, positive browser search/filter, and persistent pagination still have mandatory blocked/unrun coverage, alongside unrelated Module 02 gaps recorded in the master matrix. Do not enable transfer policy or production financial posting until the listed approvals exist. No Module 03 or gameplay work was started.
