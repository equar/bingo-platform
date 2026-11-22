# Player multi-ticket purchase behavior

## Current behavior

The quantity selector allows 1–10 tickets. The Flutter controller sends one `POST /api/v1/sessions/{sessionId}/tickets` request per ticket, sequentially, with a distinct idempotency key for each request. Each successful response confirms a single ticket and debit. If a later request fails, earlier tickets remain purchased and charged; the loop stops and does not roll them back. The previous UI only reported the confirmed count and error, without listing ticket IDs or charging total.

Before each request, the Player stores that request's idempotency key in SharedPreferences under an account and session scoped key. A confirmed response removes it; a definitive rejection removes it; a transport timeout or server error retains it and blocks new purchases. After refresh, the controller restores this key as uncertain. The Player must explicitly resolve the outcome, which refreshes ticket ownership and retries the exact same single-ticket request with its original key. This can return the original ticket without a second debit, or create it once if the original request did not commit. No new key is generated until that uncertain request is resolved. The selected batch size and earlier confirmed tickets are not persisted as a resumable batch: after refresh the user resolves only the pending single ticket, and chooses any remaining quantity again.

The backend wraps each purchase in its own transaction. It locks the game session and wallet, checks sale/session/capacity/player limits and currency, debits cash/promotional balances, creates the ticket and card allocation, writes the debit ledger transaction, and stores the idempotent result. A failure rolls back that individual transaction. There is no per-request refund path because ticket issuance and debit commit or roll back together. Existing MySQL coverage includes insufficient-funds rollback, same-key replay, capacity contention, and concurrent different-session purchases serializing against a player's wallet.

## Why this remains a sequence of individual purchases

An atomic batch endpoint would change compatibility and accounting semantics: the current API and idempotency record represent one ticket, while each purchase locks both session and wallet and writes one ticket plus one debit. A batch would need to make all card allocations, wallet postings, limits/capacity checks, transaction records, and one batch idempotency result commit or roll back together, and define replay/partial failure behavior for existing clients. There is no batch transaction contract or refund/compensation behavior in the current service. Therefore this change keeps the supported 1–10 quantity and sequential per-ticket transactions; it does not introduce a batch endpoint.

## Player outcome reporting

For a single in-session attempt the Player reports confirmed count, confirmed ticket IDs, confirmed debit total, remaining quantity, and any error. A timed-out request is reported as unconfirmed, not failed. Its original key remains saved, and the user must use the existing explicit status/retry action before starting another purchase. Confirmed earlier tickets are not described as rolled back. Amount reporting uses minor currency units derived from the server-provided ticket price.
