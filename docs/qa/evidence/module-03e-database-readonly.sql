USE bingo_qa;

-- This script contains SELECT statements only. Do not add DML to this reconciliation.
SELECT 'TOTALS' AS section,
       (SELECT COUNT(*) FROM users) AS users,
       (SELECT COUNT(*) FROM bingo_agents) AS agents,
       (SELECT COUNT(*) FROM wallets) AS wallets,
       (SELECT COUNT(*) FROM signup_reward_policies) AS signup_policies,
       (SELECT COUNT(*) FROM signup_reward_grants) AS signup_grants,
       (SELECT COUNT(*) FROM wallet_transactions) AS wallet_transactions,
       (SELECT COUNT(*) FROM deposit_claims) AS deposit_claims,
       (SELECT COUNT(*) FROM deposit_accounting_journals) AS deposit_journals,
       (SELECT COUNT(*) FROM float_accounting_journals) AS float_journals;

SELECT 'PLAYER_RECONCILIATION' AS section, u.id, u.display_name, u.normalized_phone,
       u.status, GROUP_CONCAT(ur.role ORDER BY ur.role SEPARATOR ',') AS roles,
       w.currency, w.available_balance, w.promotional_balance,
       (SELECT COUNT(*) FROM signup_reward_grants g WHERE g.player_id = u.id) AS grants,
       (SELECT COUNT(*) FROM wallet_transactions wt WHERE wt.wallet_id = w.id) AS transactions
FROM users u
JOIN user_roles ur ON ur.user_id = u.id
LEFT JOIN wallets w ON w.owner_id = u.id
WHERE u.id IN (6, 178, 179, 180)
   OR u.normalized_phone = '251930400002'
GROUP BY u.id, u.display_name, u.normalized_phone, u.status,
         w.currency, w.available_balance, w.promotional_balance
ORDER BY u.id;

SELECT 'TARGET_PLAYER_AUDIT' AS section, a.id, a.actor_user_id, a.target_id,
       a.action, a.created_at
FROM audit_log_entries a
JOIN users u ON u.id = a.target_id
WHERE u.normalized_phone = '251930400002'
ORDER BY a.id;

SELECT 'TARGET_PROFILE_UPDATE_AUDITS' AS section, COUNT(*) AS count
FROM audit_log_entries a
JOIN users u ON u.id = a.target_id
WHERE u.normalized_phone = '251930400002'
  AND a.action IN ('UPDATE_PROFILE', 'UPDATE_USER');

SELECT 'GLOBAL_PROFILE_UPDATE_AUDITS' AS section, COUNT(*) AS count
FROM audit_log_entries
WHERE action = 'UPDATE_PROFILE';

SELECT 'RECENT_ADMIN_LOGIN_AUDIT' AS section, id, actor_user_id, target_id, action, created_at
FROM audit_log_entries
WHERE actor_user_id = 1 AND action = 'LOGIN'
ORDER BY id DESC
LIMIT 5;

SELECT 'SIGNUP_POLICIES' AS section, id, IF(enabled, 'TRUE', 'FALSE') AS enabled, reward_amount, currency,
       effective_from, effective_until, changed_by_user_id, created_at
FROM signup_reward_policies
ORDER BY id;

SELECT 'SIGNUP_GRANTS' AS section, COUNT(*) AS total,
       COALESCE(SUM(player_id IN (178, 179, 180)), 0) AS prior_03a_03d_players,
       COALESCE(SUM(player_id = (SELECT id FROM users WHERE normalized_phone = '251930400002')), 0) AS m03e_player
FROM signup_reward_grants;

SELECT 'TARGET_WALLET_TRANSACTIONS' AS section, wt.id, u.id AS player_id,
       wt.transaction_type, wt.amount, wt.currency, wt.promotional_balance_before,
       wt.promotional_balance_after, wt.created_at
FROM users u
JOIN wallets w ON w.owner_id = u.id
JOIN wallet_transactions wt ON wt.wallet_id = w.id
WHERE u.id IN (6, 178, 179, 180)
   OR u.normalized_phone = '251930400002'
ORDER BY u.id, wt.id;

SELECT 'AGENT_3_FLOAT_AND_JOURNALS' AS section,
       af.agent_id, af.currency, af.available_balance, af.clearing_balance,
       (SELECT COUNT(*) FROM agent_float_transactions aft WHERE aft.agent_float_id = af.id) AS float_transactions,
       (SELECT COUNT(*) FROM float_accounting_journals faj
        JOIN agent_float_top_up_requests ar ON ar.id = faj.request_id
        WHERE ar.agent_id = af.agent_id) AS journals
FROM agent_floats af
WHERE af.agent_id = 3;

SELECT 'HISTORICAL_DEPOSIT_CLAIMS' AS section, id, player_id, status,
       submitted_cash_amount, calculated_credit_amount, currency
FROM deposit_claims
ORDER BY id;

SELECT 'DEPOSIT_JOURNAL_TOTALS' AS section, COUNT(*) AS journals,
       (SELECT COUNT(*) FROM deposit_accounting_journal_lines) AS journal_lines
FROM deposit_accounting_journals;

SELECT 'REWARD_JOURNAL_TABLES' AS section, table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name LIKE '%signup%journal%'
ORDER BY table_name;

SELECT 'SCHEMA_VERSION' AS section, version
FROM flyway_schema_history
WHERE success = 1
ORDER BY installed_rank DESC
LIMIT 1;
