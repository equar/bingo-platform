# Open Release Blockers

**Updated:** 2026-10-22  
**Decision:** **NO-GO**

| # | Blocker | Status / evidence required |
|---|---|---|
| 1 | V14 backup/restore | The prior disposable database restore covered V13. Rehearse backup/restore with the V14 activation migration and verify schema/data after restore. Production retention, RPO/RTO, and the operator runbook also need approval. |
| 2 | Full financial reconciliation | Focused sale, payout, commission, replay, and insufficient-float invariants passed. A complete before/after reconciliation across deposits, approvals, settlements, transfers, winnings, and ledger buckets has not been performed. |
| 3 | Admin Playwright E2E | PARTIAL: `sessions-admin.spec.ts` passed (1 test, exit code 0) against isolated services. The remaining four discovered tests still require execution on the release candidate. Traces/screenshots/video are disabled by default to avoid credential-bearing artifacts. |
| 4 | Player and agent device E2E | PARTIAL: Android player setup/deposit/registration and support journeys passed against the disposable backend. Each phase run exited 0 and reported 5 passed/0 failed/0 skipped, with only the selected journey active and four phase cases returning early. Purchase/settlement/claim, agent-device operations, and iOS remain unverified; iOS requires a supported macOS/Xcode runner. |
| 5 | Load and concurrency target | No `k6` executable, reviewed load script, or isolated load target. Measure 1,000 concurrent players, the 10-ticket limit, latency/throughput/errors, and financial invariants. |
| 6 | Legal content | Public Terms and Privacy pages identify themselves as drafts. Obtain approved final legal content before public release. |
| 7 | Fastlane supply-chain reproducibility | All four store-release workflows pin Fastlane 2.241.1. Lock the transitive gem dependency set and validate installation/build steps in CI before store releases. |
| 8 | Production TLS/database configuration | `application-prod.yml` requires `sslMode=VERIFY_IDENTITY` and `ADMIN_ACCOUNT_ACTIVATION_URL`. Confirm trusted CA configuration, production endpoint, and activation URL with operators. |
| 9 | Production deployment validation | CloudFront association, production backup/recovery controls, SMTP delivery, trusted-proxy ranges, secret provisioning/rotation, and operational monitoring were not validated against the live deployment. |
| 10 | Release-candidate CI and external security evidence | No CI run on the exact release candidate, external penetration/security assessment, or current secret-scan evidence was produced in this execution. |

The detailed gate matrix and rerun steps are in [release-checklist.md](./release-checklist.md), [production-readiness-report.md](./production-readiness-report.md), [security-validation.md](./security-validation.md), [load-test-results.md](./load-test-results.md), and [financial-reconciliation.md](./financial-reconciliation.md).

**No production-readiness GO is granted until every applicable blocker has verifiable evidence and owner sign-off.**
