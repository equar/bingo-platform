USE bingo_qa;

SELECT 'TOTALS',
       (SELECT COUNT(*) FROM users),
       (SELECT COUNT(*) FROM bingo_agents),
       (SELECT COUNT(*) FROM wallets),
       (SELECT COUNT(*) FROM signup_reward_grants),
       (SELECT COUNT(*) FROM wallet_transactions);

SELECT 'PLAYER', u.id, u.display_name, u.normalized_phone, u.status,
       GROUP_CONCAT(ur.role ORDER BY ur.role SEPARATOR ',') AS roles,
       w.currency, w.available_balance, w.promotional_balance,
       (SELECT COUNT(*) FROM signup_reward_grants g WHERE g.player_id = u.id) AS grants,
       (SELECT COUNT(*) FROM wallet_transactions wt WHERE wt.wallet_id = w.id) AS transactions
FROM users u
JOIN user_roles ur ON ur.user_id = u.id
LEFT JOIN wallets w ON w.owner_id = u.id
WHERE u.id IN (178, 179)
GROUP BY u.id, u.display_name, u.normalized_phone, u.status,
         w.currency, w.available_balance, w.promotional_balance
ORDER BY u.id;

SELECT 'PROFILE_UPDATE_AUDIT_ROWS', COUNT(*)
FROM audit_log_entries
WHERE target_id IN (178, 179) AND action IN ('UPDATE_PROFILE', 'UPDATE_USER');

SELECT 'LATEST_PLAYER_AUTH_AUDIT', id, actor_user_id, target_id, action, created_at
FROM audit_log_entries
WHERE target_id = 178 AND action IN ('LOGIN', 'LOGIN_FAILED')
ORDER BY id DESC
LIMIT 5;

SELECT 'FLYWAY_LATEST_SUCCESS', version
FROM flyway_schema_history
WHERE success = 1
ORDER BY installed_rank DESC
LIMIT 1;
