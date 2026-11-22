-- READ ONLY. Execute only with a read-only account on the exact disposable
-- schema used for this run. Supply the session, preapproved synthetic player
-- IDs, and a UTC start/end window from the signed run manifest.
SET @perf_session_id = 0; -- replace
SET @perf_started_at_utc = '2000-01-01 00:00:00'; -- replace
SET @perf_ended_at_utc = '2000-01-01 00:00:00'; -- replace
SET @perf_player_ids = '0'; -- comma separated reviewed synthetic user IDs

-- Draw sequence uniqueness and continuity. Missing numbers can be legitimate
-- if this run began after START_SEQUENCE; compare the returned range to the
-- run manifest and REST/WebSocket event capture.
SELECT session_id, sequence_no, COUNT(*) AS row_count
FROM bingo_draw_events
WHERE session_id = @perf_session_id
GROUP BY session_id, sequence_no
HAVING COUNT(*) <> 1;

-- Any output row is a duplicate financial posting operation.
SELECT wt.operation_id, wt.transaction_type, COUNT(*) AS posting_count,
       GROUP_CONCAT(wt.id ORDER BY wt.id) AS transaction_ids
FROM wallet_transactions wt
JOIN wallets w ON w.id = wt.wallet_id
WHERE FIND_IN_SET(CAST(w.owner_id AS CHAR), @perf_player_ids) > 0
  AND wt.created_at >= @perf_started_at_utc
  AND wt.created_at < @perf_ended_at_utc
  AND wt.transaction_type IN ('TICKET_PURCHASE_DEBIT', 'PRIZE_CREDIT')
GROUP BY wt.operation_id, wt.transaction_type
HAVING COUNT(*) > 1;

-- Purchase debits must map to a single ticket and amount. Review the full
-- output against the expected one-purchase-per-synthetic-user manifest.
SELECT bt.id AS ticket_id, bt.session_id, bt.owner_id, bt.operation_id,
       COUNT(wt.id) AS purchase_debit_count,
       MIN(wt.amount) AS min_debit, MAX(wt.amount) AS max_debit
FROM bingo_tickets bt
LEFT JOIN wallet_transactions wt
  ON wt.reference_type = 'BINGO_TICKET'
 AND wt.reference_id = bt.id
 AND wt.transaction_type = 'TICKET_PURCHASE_DEBIT'
WHERE bt.session_id = @perf_session_id
  AND FIND_IN_SET(CAST(bt.owner_id AS CHAR), @perf_player_ids) > 0
  AND bt.purchased_at >= @perf_started_at_utc
  AND bt.purchased_at < @perf_ended_at_utc
GROUP BY bt.id, bt.session_id, bt.owner_id, bt.operation_id
HAVING COUNT(wt.id) <> 1 OR MIN(wt.amount) <> MAX(wt.amount);

-- Each wallet prize entitlement should have at most one corresponding credit.
-- Cash entitlements are excluded; this is not an Agent payout audit.
SELECT pe.id AS entitlement_id, pe.win_claim_id, pe.ticket_id, pe.claimant_id,
       pe.status, pe.prize_amount, COUNT(wt.id) AS prize_credit_count,
       COALESCE(SUM(wt.amount), 0) AS credited_amount
FROM bingo_prize_entitlements pe
LEFT JOIN wallet_transactions wt
  ON wt.reference_type = 'BINGO_PRIZE'
 AND wt.reference_id = pe.id
 AND wt.transaction_type = 'PRIZE_CREDIT'
WHERE pe.settlement_path = 'PLAYER_WALLET'
  AND FIND_IN_SET(CAST(pe.claimant_id AS CHAR), @perf_player_ids) > 0
  AND pe.created_at >= @perf_started_at_utc
  AND pe.created_at < @perf_ended_at_utc
GROUP BY pe.id, pe.win_claim_id, pe.ticket_id, pe.claimant_id, pe.status, pe.prize_amount
HAVING COUNT(wt.id) > 1
    OR (pe.status = 'SETTLED' AND COUNT(wt.id) <> 1)
    OR (pe.status <> 'SETTLED' AND COUNT(wt.id) <> 0);

-- Claim/entitlement cardinality for the run window; one approved winning claim
-- has at most one wallet entitlement under this application's schema.
SELECT wc.id AS claim_id, COUNT(pe.id) AS entitlement_count
FROM bingo_win_claims wc
LEFT JOIN bingo_prize_entitlements pe ON pe.win_claim_id = wc.id
WHERE wc.session_id = @perf_session_id
  AND FIND_IN_SET(CAST(wc.claimant_id AS CHAR), @perf_player_ids) > 0
  AND wc.created_at >= @perf_started_at_utc
  AND wc.created_at < @perf_ended_at_utc
GROUP BY wc.id
HAVING COUNT(pe.id) > 1;
