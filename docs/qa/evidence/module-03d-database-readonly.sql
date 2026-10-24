USE bingo_qa;

SELECT 'TOTALS' AS section,
       (SELECT COUNT(*) FROM users) AS users,
       (SELECT COUNT(*) FROM bingo_agents) AS agents,
       (SELECT COUNT(*) FROM wallets) AS wallets,
       (SELECT COUNT(*) FROM signup_reward_policies) AS policies,
       (SELECT COUNT(*) FROM signup_reward_grants) AS grants,
       (SELECT COUNT(*) FROM wallet_transactions) AS wallet_transactions;

SELECT 'POLICY' AS section, id, IF(enabled, 'TRUE', 'FALSE') AS enabled, reward_amount, currency,
       effective_from, effective_until, changed_by_user_id, created_at
FROM signup_reward_policies
ORDER BY id;

SELECT 'PLAYER' AS section, u.id, u.display_name, u.normalized_phone, u.status,
       GROUP_CONCAT(ur.role ORDER BY ur.role SEPARATOR ',') AS roles,
       w.currency, w.available_balance, w.promotional_balance,
       (SELECT COUNT(*) FROM signup_reward_grants g WHERE g.player_id = u.id) AS grants,
       (SELECT COUNT(*) FROM wallet_transactions wt WHERE wt.wallet_id = w.id) AS transactions
FROM users u
JOIN user_roles ur ON ur.user_id = u.id
LEFT JOIN wallets w ON w.owner_id = u.id
WHERE u.id IN (178, 179)
   OR u.normalized_phone = '251930400001'
GROUP BY u.id, u.display_name, u.normalized_phone, u.status,
         w.currency, w.available_balance, w.promotional_balance
ORDER BY u.id;

SELECT 'PLAYER_REWARD_TRANSACTION' AS section, u.id AS player_id,
       wt.id AS transaction_id, wt.transaction_type, wt.amount, wt.currency,
       wt.promotional_balance_before, wt.promotional_balance_after,
       wt.created_at
FROM users u
JOIN wallets w ON w.owner_id = u.id
JOIN wallet_transactions wt ON wt.wallet_id = w.id
WHERE u.id IN (178, 179)
   OR u.normalized_phone = '251930400001'
ORDER BY u.id, wt.id;

SELECT 'REWARD_GRANTS' AS section, COUNT(*) AS total,
       COALESCE(SUM(player_id IN (178, 179)), 0) AS retained_03a_grants,
       COALESCE(SUM(player_id = (SELECT id FROM users WHERE normalized_phone = '251930400001')), 0) AS m03d_grants
FROM signup_reward_grants;

SELECT 'PLAYER_AUDIT' AS section, id, actor_user_id, target_id, action, created_at
FROM audit_log_entries
WHERE target_id = (SELECT id FROM users WHERE normalized_phone = '251930400001')
ORDER BY id;

SELECT 'ADMIN_LOGIN_AUDIT' AS section, id, actor_user_id, target_id, action, created_at
FROM audit_log_entries
WHERE actor_user_id = 1 AND action = 'LOGIN'
ORDER BY id DESC
LIMIT 5;

SELECT 'REWARD_JOURNAL_TABLES' AS section, table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name LIKE '%journal%'
ORDER BY table_name;

SELECT 'LATEST_SCHEMA_VERSION' AS section, version
FROM flyway_schema_history
WHERE success = 1
ORDER BY installed_rank DESC
LIMIT 1;
