USE bingo_qa;

SELECT 'TOTALS',
       (SELECT COUNT(*) FROM users),
       (SELECT COUNT(*) FROM bingo_agents),
       (SELECT COUNT(*) FROM wallets),
       (SELECT COUNT(*) FROM signup_reward_grants),
       (SELECT COUNT(*) FROM wallet_transactions),
       (SELECT COUNT(*) FROM authentication_attempt_buckets),
       (SELECT COUNT(*) FROM authentication_attempt_buckets WHERE locked_until > UTC_TIMESTAMP());

SELECT 'KNOWN_USER', u.id, u.normalized_phone, u.status,
       GROUP_CONCAT(ur.role ORDER BY ur.role SEPARATOR ',')
FROM users u
LEFT JOIN user_roles ur ON ur.user_id = u.id
WHERE u.id BETWEEN 1 AND 12 OR u.id IN (178, 179)
GROUP BY u.id, u.normalized_phone, u.status
ORDER BY u.id;

SELECT 'MODULE03A_PLAYER', u.id, u.display_name, u.normalized_phone, u.status,
       w.currency, w.available_balance, w.promotional_balance,
       (SELECT COUNT(*) FROM signup_reward_grants g WHERE g.player_id = u.id) AS grants,
       (SELECT COUNT(*) FROM wallet_transactions wt WHERE wt.wallet_id = w.id) AS wallet_transactions
FROM users u
JOIN wallets w ON w.owner_id = u.id
WHERE u.id IN (178, 179)
ORDER BY u.id;

SELECT 'SYNTHETIC_LOCKOUT_IDENTITIES',
       (SELECT COUNT(*) FROM users
        WHERE normalized_phone IN ('251930399999', '251930399998', '251930399997', '251930399996')) AS created_users,
       (SELECT COUNT(*) FROM wallets w JOIN users u ON u.id = w.owner_id
        WHERE u.normalized_phone IN ('251930399999', '251930399998', '251930399997', '251930399996')) AS created_wallets;

SELECT 'LOGIN_AUDIT', id, actor_user_id, target_id, created_at, metadata_json
FROM audit_log_entries
WHERE action = 'LOGIN' AND target_id IN (178, 179)
ORDER BY id DESC;

SELECT 'FLYWAY', version
FROM flyway_schema_history
WHERE success = 1
ORDER BY installed_rank DESC
LIMIT 1;
