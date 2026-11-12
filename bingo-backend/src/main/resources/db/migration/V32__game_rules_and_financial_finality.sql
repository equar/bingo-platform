alter table bingo_sessions
    add column rules_version varchar(30) not null default 'LEGACY',
    add column minimum_house_margin_bps int null,
    add column session_cost_reserve decimal(19,2) null,
    add column claim_window_seconds bigint null;

-- Refuse to migrate silently if historical duplicate economic awards exist.
-- Operators must reconcile those awards rather than delete or rewrite them.
alter table bingo_prize_entitlements
    add constraint uk_prize_ticket_pattern unique (ticket_id, prize_type);
