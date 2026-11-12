alter table signup_reward_policies
    add column name varchar(120) not null default 'Signup reward',
    add column trigger_type varchar(20) not null default 'SIGNUP';

alter table signup_reward_grants
    drop foreign key fk_signup_reward_grants_player,
    drop index uk_signup_reward_grants_player,
    add index idx_signup_reward_grants_player (player_id),
    add column trigger_type varchar(20) not null default 'SIGNUP',
    add column awarded_by_user_id bigint null,
    add constraint uk_signup_reward_grants_campaign_player unique (policy_id, player_id),
    add column signup_player_id bigint generated always as (case when trigger_type = 'SIGNUP' then player_id else null end) stored,
    add constraint uk_signup_reward_grants_signup_player unique (signup_player_id),
    add constraint fk_signup_reward_grants_player_user foreign key (player_id) references users (id),
    add constraint fk_signup_reward_grants_awarded_by foreign key (awarded_by_user_id) references users (id) on delete set null;

create index idx_signup_reward_policies_trigger_created on signup_reward_policies (trigger_type, created_at desc);
