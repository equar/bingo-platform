USE bingo_qa;

-- Read-only reconciliation. This file contains no INSERT, UPDATE, DELETE or DDL.
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

SELECT 'PRESERVED_PLAYER_WALLETS' AS section, u.id, u.display_name, u.status,
       w.currency, w.available_balance, w.promotional_balance
FROM users u
LEFT JOIN wallets w ON w.owner_id = u.id
WHERE u.id IN (178, 179, 180, 181)
ORDER BY u.id, w.id;

SELECT 'AGENT_3_FLOAT' AS section, af.id, af.agent_id,
       af.available_balance, af.clearing_balance,
       (SELECT COUNT(*) FROM agent_float_transactions t WHERE t.agent_float_id = af.id) AS transactions
FROM agent_floats af
WHERE af.agent_id = 3;
