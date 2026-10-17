create table signup_reward_policies (
    id bigint not null auto_increment primary key,
    enabled bit(1) not null,
    reward_amount decimal(19, 2) not null,
    currency varchar(3) not null,
    effective_from timestamp(6) not null,
    effective_until timestamp(6) null,
    changed_by_user_id bigint null,
    reason varchar(500) null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint fk_signup_reward_policies_changed_by foreign key (changed_by_user_id) references users (id)
);

create index idx_signup_reward_policies_created_at on signup_reward_policies (created_at desc);

create table signup_reward_grants (
    id bigint not null auto_increment primary key,
    player_id bigint not null,
    policy_id bigint not null,
    operation_id varchar(36) not null,
    reward_amount decimal(19, 2) not null,
    currency varchar(3) not null,
    awarded_at timestamp(6) not null,
    constraint uk_signup_reward_grants_player unique (player_id),
    constraint uk_signup_reward_grants_operation unique (operation_id),
    constraint fk_signup_reward_grants_player foreign key (player_id) references users (id),
    constraint fk_signup_reward_grants_policy foreign key (policy_id) references signup_reward_policies (id)
);

create index idx_signup_reward_grants_awarded_at on signup_reward_grants (awarded_at desc);

create table wallet_transfer_policies (
    id bigint not null auto_increment primary key,
    enabled bit(1) not null,
    max_single_transfer_amount decimal(19, 2) not null,
    daily_outgoing_limit_amount decimal(19, 2) not null,
    currency varchar(3) not null,
    effective_from timestamp(6) not null,
    effective_until timestamp(6) null,
    changed_by_user_id bigint null,
    reason varchar(500) null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint fk_wallet_transfer_policies_changed_by foreign key (changed_by_user_id) references users (id)
);

create index idx_wallet_transfer_policies_created_at on wallet_transfer_policies (created_at desc);

create table wallet_transfers (
    id bigint not null auto_increment primary key,
    operation_id varchar(36) not null,
    sender_id bigint not null,
    recipient_id bigint not null,
    sender_phone_snapshot varchar(32) not null,
    recipient_phone_snapshot varchar(32) not null,
    amount decimal(19, 2) not null,
    currency varchar(3) not null,
    note varchar(500),
    status varchar(20) not null,
    sender_balance_before decimal(19, 2) not null,
    sender_balance_after decimal(19, 2) not null,
    recipient_balance_before decimal(19, 2) not null,
    recipient_balance_after decimal(19, 2) not null,
    created_at timestamp(6) not null,
    completed_at timestamp(6) null,
    failure_reason varchar(500) null,
    constraint uk_wallet_transfers_operation unique (operation_id),
    constraint fk_wallet_transfers_sender foreign key (sender_id) references users (id),
    constraint fk_wallet_transfers_recipient foreign key (recipient_id) references users (id)
);

create index idx_wallet_transfers_sender_created_at on wallet_transfers (sender_id, created_at desc);
create index idx_wallet_transfers_recipient_created_at on wallet_transfers (recipient_id, created_at desc);
create index idx_wallet_transfers_status on wallet_transfers (status);