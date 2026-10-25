# QA Blockers and Required Approvals

**Audit snapshot:** 2026-10-25. Derived from current module reports; no new approval is granted by this document.

| Area | Current blocker | Required decision / evidence | Affected scope |
| --- | --- | --- | --- |
| MySQL provisioning | Dedicated local `bingo_qa_maven_test` schema/account and current grants are not verified; historical C5B had no admin login. | Authorized DBA access; approved scoped provisioning and rotation; successful authentication plus cross-schema denial checks. | 04C, 04D, local DB integration. |
| Android/Admin DB least privilege | 04D-4 read-only review found `ALL PRIVILEGES` on `bingo_qa_test.*`; persistent-schema denials were observed. | DBA/security approval and narrower runtime grants, followed by read-only verification. | Android/Admin E2E backend startup. |
| Claim ticket fixture | No approved nonfinancial fixture writer exists. Purchase debits wallet; agent sale uses float and commission. | Product, Security and DBA approval of 04D-5B design; explicit local-QA opt-in, isolated schema/account and provenance requirements. | 04D claim E2E. |
| Claim eligibility | CLOSED behavior and some lifecycle semantics are unresolved; no valid owned winning ticket fixture. | Product decision for CLOSED/CANCELLED, eligibility timing and claim outcomes. | 04D and 02E. |
| Draw retry | Retry across due intervals is not idempotent; no accepted client request-id contract. | Product/API contract and focused isolated concurrency/retry verification. | 04C. |
| Ticket purchase/cancellation | Player purchase UI is absent; cutoff, ticket-bearing cancellation/refund and accounting are unresolved. | Product and Finance/accounting policy; safe isolated funding and reconciliation plan. | 04B, 02E. |
| Transfers and rewards | Persistent positive fixtures and policy limits/zero semantics lack approval. | Product/Finance approval, accounting/reversal expectations and synthetic isolated fixture path. | 02D, 02H, 02I. |
| Funding and settlement | Production credits, deposits, float funding and prize settlement are guarded/disabled; no external payment settlement is approved. | Finance/accounting, risk and operational approval plus implementation and reconciliation evidence. | 02, 04D, production. |
| Scheduling/prize policy | Time zone, DST, recurrence, overlap, prize formula and multi-winner behavior are undefined. | Product/Finance decisions with documented examples and acceptance criteria. | 02E, 04D. |
| Operations/release | Production monitoring/SLO, backup/restore drill, incident recovery, workflow release and rollback evidence absent. | Operations/security/release owners provide controls and executed evidence. | D35/D36 and production readiness. |

## Completion state

The master inventory and coverage matrix are static audit artifacts. Module 02 remains INCOMPLETE; 04B, 04C and 04D remain PARTIAL; 04E is NOT STARTED. Module 05A may be marked complete for inventory and analysis only. No blocker in this list authorizes live financial posting or production changes.
