alter table signup_reward_policies
    add column threshold_amount decimal(19,2) null;

alter table users
    add column referral_code varchar(24) null,
    add column referred_by_user_id bigint null,
    add constraint fk_users_referred_by foreign key (referred_by_user_id) references users (id) on delete set null;

update users set referral_code = concat('BINGO', id) where referral_code is null;
alter table users modify referral_code varchar(24) not null;
create unique index uk_users_referral_code on users (referral_code);
create index idx_users_referred_by on users (referred_by_user_id);

alter table signup_reward_grants
    add column trigger_reference_key varchar(80) null;
-- V35 guaranteed one grant per campaign/player. Preserve that idempotency key
-- so existing signup/manual grants still block a retry after this migration.
update signup_reward_grants
set trigger_reference_key = concat('PLAYER-', player_id)
where trigger_reference_key is null;
alter table signup_reward_grants modify trigger_reference_key varchar(80) not null,
    add constraint uk_signup_reward_grants_campaign_event unique (policy_id, trigger_reference_key);

alter table signup_reward_grants
    drop index uk_signup_reward_grants_campaign_player,
    drop index uk_signup_reward_grants_signup_player;
