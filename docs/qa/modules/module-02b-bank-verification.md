# Module 02B bank verification

## Production control

An agent's transfer reference, note, or uploaded artifact is an unverified claim. It cannot set a request to VERIFIED and cannot authorize a float credit. A production verification requires a trusted bank statement/integration record matched on the configured receiving account, bank transaction reference, ETB amount, currency, and transaction date, plus the finance reviewer's identity and timestamp.

The current application has no bank integration or trusted bank statement import. Production verification therefore remains disabled. The QA-only workflow accepts separately entered, conspicuously synthetic statement rows when the QA simulation flag is enabled on the `qa` profile and the connected catalog is `bingo_qa` or `bingo_qa_test`. These rows test matching and ledger controls; they are not evidence of real receipt.

## Verification checks

- Active receiving account matches the request's immutable submission snapshot.
- Statement transaction reference is unique per receiving account.
- Currency is ETB and the independently recorded amount equals the requested amount exactly.
- The agent remains active and approved at approval time.
- Missing, wrong-account, duplicate-reference, amount-mismatch, reversed/unsettled, and conflicting-evidence outcomes create a verification exception and no credit.
- Every attempt is retained with outcome, reason, evidence source, reviewer, and timestamp. Exceptions may be resolved only by a later valid independent record.
- Approval requires a successful verification and a different active authorized user. Verification-only actors cannot post.

Uploaded agent receipts remain optional supporting claims and are never accepted as a bank statement row.
