USE bingo_qa;

-- Module 04B-2 persistent QA reconciliation. All statements are SELECT-only.
START TRANSACTION READ ONLY;

SELECT 'SCHEMA_VERSION' AS section, version, description
FROM flyway_schema_history
WHERE success = 1
ORDER BY installed_rank DESC
LIMIT 1;

SELECT 'GAME_TOTALS' AS section,
       (SELECT COUNT(*) FROM bingo_rooms) AS rooms,
       (SELECT COUNT(*) FROM bingo_sessions) AS sessions,
       (SELECT COUNT(*) FROM bingo_tickets) AS tickets,
       (SELECT COUNT(*) FROM bingo_draw_events) AS draws,
       (SELECT COUNT(*) FROM bingo_win_claims) AS claims,
       (SELECT COUNT(*) FROM bingo_prize_entitlements) AS entitlements,
       (SELECT COUNT(*) FROM wallet_transactions) AS wallet_transactions;

SELECT 'ROOMS' AS section, id, name, active, ticket_price, currency,
       session_capacity, timezone, schedule_rule
FROM bingo_rooms
ORDER BY id;

SELECT 'SESSIONS' AS section, s.id, s.room_id, r.name AS room_name,
       s.session_name, s.state, s.scheduled_start_at,
       s.ticket_price_snapshot, s.currency_snapshot,
       s.session_capacity_snapshot,
       (SELECT COUNT(*) FROM bingo_tickets t WHERE t.session_id = s.id) AS ticket_count,
       (SELECT COUNT(*) FROM bingo_draw_events d WHERE d.session_id = s.id) AS draw_count
FROM bingo_sessions s
JOIN bingo_rooms r ON r.id = s.room_id
ORDER BY s.room_id, s.scheduled_start_at, s.id;

SELECT 'ALL_WALLETS' AS section, w.id, w.owner_id, u.display_name,
       u.status, w.currency, w.available_balance, w.promotional_balance
FROM wallets w
JOIN users u ON u.id = w.owner_id
ORDER BY w.owner_id, w.id;

SELECT 'WALLET_TRANSACTION_TYPES' AS section, transaction_type, COUNT(*) AS count
FROM wallet_transactions
GROUP BY transaction_type
ORDER BY transaction_type;

SELECT 'JOURNAL_COUNTS' AS section,
       (SELECT COUNT(*) FROM deposit_accounting_journals) AS deposit_journals,
       (SELECT COUNT(*) FROM deposit_accounting_journal_lines) AS deposit_lines,
       (SELECT COUNT(*) FROM wallet_adjustment_accounting_journals) AS adjustment_journals,
       (SELECT COUNT(*) FROM wallet_adjustment_accounting_journal_lines) AS adjustment_lines,
       (SELECT COUNT(*) FROM float_accounting_journals) AS float_journals,
       (SELECT COUNT(*) FROM float_accounting_journal_lines) AS float_lines;

SELECT 'TICKET_SALE_JOURNAL_TABLES' AS section, table_name
FROM information_schema.tables
WHERE table_schema = 'bingo_qa'
  AND table_name LIKE '%ticket%journal%'
ORDER BY table_name;

SELECT 'AGENT_3_FLOAT' AS section,
       available_balance,
       clearing_balance,
       (SELECT COUNT(*) FROM agent_float_transactions t
        WHERE t.agent_float_id = af.id) AS transactions
FROM agent_floats af
WHERE af.agent_id = 3;

COMMIT;
