-- Executed read-only against bingo_qa after Module 03A Android runs.
-- No credentials, PINs, hashes, or access tokens are selected.

SELECT 'KNOWN_USER', u.id, u.normalized_phone, u.status,
       GROUP_CONCAT(r.role ORDER BY r.role), COALESCE(w.currency, '-'),
       COALESCE(w.available_balance, 0), COALESCE(w.promotional_balance, 0)
FROM users u
LEFT JOIN user_roles r ON r.user_id = u.id
LEFT JOIN wallets w ON w.owner_id = u.id
WHERE u.id BETWEEN 1 AND 12
GROUP BY u.id, w.currency, w.available_balance, w.promotional_balance
ORDER BY u.id;

SELECT 'MODULE03A_PLAYER', u.id, u.display_name, u.normalized_phone, u.status,
       GROUP_CONCAT(r.role ORDER BY r.role), u.created_at,
       w.currency, w.available_balance, w.promotional_balance
FROM users u
JOIN user_roles r ON r.user_id = u.id
LEFT JOIN wallets w ON w.owner_id = u.id
WHERE u.normalized_phone IN ('251930300001', '251930300002')
GROUP BY u.id, w.currency, w.available_balance, w.promotional_balance
ORDER BY u.id;

SELECT 'MODULE03A_GRANTS', COUNT(*)
FROM signup_reward_grants g JOIN users u ON u.id = g.player_id
WHERE u.normalized_phone IN ('251930300001', '251930300002');

SELECT 'MODULE03A_TRANSACTIONS', COUNT(*)
FROM wallet_transactions t
JOIN wallets w ON w.id = t.wallet_id
JOIN users u ON u.id = w.owner_id
WHERE u.normalized_phone IN ('251930300001', '251930300002');

SELECT 'MODULE03A_AUDIT', u.normalized_phone, COUNT(*)
FROM audit_log_entries a JOIN users u ON u.id = a.target_id
WHERE u.normalized_phone IN ('251930300001', '251930300002') AND a.action = 'REGISTER'
GROUP BY u.normalized_phone;

SELECT 'TOTALS', (SELECT COUNT(*) FROM users), (SELECT COUNT(*) FROM bingo_agents),
       (SELECT COUNT(*) FROM wallets), (SELECT COUNT(*) FROM signup_reward_grants),
       (SELECT COUNT(*) FROM wallet_transactions);

SELECT 'FIRST_FIVE', u.id, u.normalized_phone, u.status, GROUP_CONCAT(r.role ORDER BY r.role)
FROM users u JOIN user_roles r ON r.user_id = u.id
WHERE u.id BETWEEN 1 AND 5
GROUP BY u.id
ORDER BY u.id;

SELECT 'QA_POLICY', id, enabled, reward_amount, currency, effective_from, effective_until
FROM signup_reward_policies ORDER BY id;

SELECT 'FLYWAY', version
FROM flyway_schema_history
WHERE success = 1
ORDER BY installed_rank DESC
LIMIT 1;
