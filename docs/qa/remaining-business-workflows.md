# Remaining Business Workflows

**Audit snapshot:** 2026-10-25. This is a static synthesis of current source and retained QA reports. It does not authorize new fixtures, database operations, financial posting, or product behavior.

The 39-domain inventory and highest-evidence assignments are in [master-business-domain-inventory](master-business-domain-inventory.md) and [master-e2e-coverage-matrix](master-e2e-coverage-matrix.md). The matrix counts evidence; this report identifies end-to-end workflow seams still open.

## Player workflows

| Workflow | Current state | Remaining work |
| --- | --- | --- |
| Registration, sign-in, profile and session recovery | Scoped player journeys have Android and database reconciliation. Profile editing and server-side logout/revocation are not established. | Product definition for profile fields; security decision on token revocation; extend authenticated lifecycle E2E after those decisions. |
| Wallet, rewards, transfers and deposits | Read/history, disabled reward behavior and selected synthetic finance paths exist. Real funding and production posting are disabled. Transfer policies/details and positive persistent fixtures remain incomplete. | Product/Finance/accounting approval for reward, transfer and deposit rules, funding evidence, reversals and reconciliation; isolated fixtures only after approval. |
| Game discovery and ticket purchase | Lobby/preview is verified; backend purchase generates cards and debits wallet, but player UI purchase is unavailable. | Define ticket cutoff, cancellation/refund and accounting; approved isolated funding path; complete Android purchase and reconciliation. |
| Live play and claims | Live draws/history/recovery have scoped Android/Admin evidence. Claim unit/security checks exist, but a valid owned-ticket real DB/Android/Admin claim journey is blocked. | Resolve DBA isolation/least privilege and approved synthetic ticket fixture, then run claim scenarios in the matrix. Draw retry semantics need Product/API decision. |
| Prize and payout | Claim can create an entitlement; settlement is fail-closed. Agent cash payout verification code exists; no player withdrawal or external disbursement path was found. | Product/Finance prize formula, payout evidence and approval; implement/review financial ledger and reversal before enabling settlement. |
| Support and notification | Support CRUD and Admin handling have scoped Chromium/MySQL evidence. Broad player/agent notification delivery is absent or unverified. | Define dispute/SLA scope, Android support lifecycle, notification channels, retry and delivery evidence. |

## Agent workflows

Onboarding, approval, authentication, float synthetic funding and account controls have retained scoped E2E evidence. Ticket sales still debit float and accrue commission, so they cannot be used as a nonfinancial claim fixture. Agent commission reconciliation, ticket sale device/browser journeys, payout lifecycle, offline/restart behavior and production bank funding remain incomplete or approval-bound.

## Admin and operational workflows

Admin identity/role and support scopes have stronger evidence than financial, claim and report workflows. Remaining work includes persistent wallet-transfer detail/policy fixtures, schedule semantics, claim review E2E, prize/settlement policy, metric reconciliation, complete audit UI reconciliation, workflow/deployment execution, backup/restore, alerting and incident recovery drills.

## Public website and release workflows

The public site is static Astro content and CI checks; no browser E2E was run in this audit. App distribution workflows are present in source, but successful release artifacts, staged rollout, signing review, production approval and rollback evidence were not established.

## Cross-system acceptance principle

For each workflow, preserve real authorization and business validation, assert the relevant database rows and audit trail, reconcile balances/journals where applicable, verify retries and failure behavior, and confirm unrelated records remain unchanged. Do not treat component tests or a successful UI click as a full workflow proof.

## Evidence limits

Counts are file/domain inventories, not fresh test results. Historical evidence remains in its module reports. This audit ran no database-backed or application tests and changed no business records.
