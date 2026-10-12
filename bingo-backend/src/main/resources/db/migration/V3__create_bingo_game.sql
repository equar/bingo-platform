create table bingo_rooms (
    id bigint not null auto_increment primary key,
    name varchar(120) not null,
    description varchar(500),
    ticket_price decimal(19,2) not null,
    currency varchar(3) not null,
    timezone varchar(80) not null,
    schedule_rule varchar(500) not null,
    session_capacity int not null,
    max_tickets_per_player int not null,
    ticket_sales_close_on_session_start boolean not null,
    allow_multiple_winners_per_prize boolean not null,
    active boolean not null,
    configured_patterns_json text not null,
    configured_prizes_json text not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint uk_bingo_rooms_name unique (name)
);

create table bingo_sessions (
    id bigint not null auto_increment primary key,
    room_id bigint not null,
    session_name varchar(120) not null,
    state varchar(30) not null,
    scheduled_start_at timestamp(6) not null,
    started_at timestamp(6),
    ended_at timestamp(6),
    ticket_price_snapshot decimal(19,2) not null,
    currency_snapshot varchar(3) not null,
    session_capacity_snapshot int not null,
    max_tickets_per_player_snapshot int not null,
    ticket_sales_close_on_session_start boolean not null,
    allow_multiple_winners_per_prize boolean not null,
    patterns_snapshot_json text not null,
    prizes_snapshot_json text not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint fk_bingo_sessions_room foreign key (room_id) references bingo_rooms (id),
    constraint uk_bingo_sessions_room_start unique (room_id, scheduled_start_at)
);

create index idx_bingo_sessions_room_state on bingo_sessions(room_id, state);

create table bingo_tickets (
    id bigint not null auto_increment primary key,
    session_id bigint not null,
    owner_id bigint not null,
    public_ticket_code varchar(24) not null,
    operation_id varchar(36) not null,
    grid_json text not null,
    purchased_at timestamp(6) not null,
    constraint fk_bingo_tickets_session foreign key (session_id) references bingo_sessions (id),
    constraint fk_bingo_tickets_owner foreign key (owner_id) references users (id),
    constraint uk_bingo_tickets_code unique (public_ticket_code),
    constraint uk_bingo_tickets_operation unique (operation_id)
);

create index idx_bingo_tickets_session_owner on bingo_tickets(session_id, owner_id);

create table bingo_draw_events (
    id bigint not null auto_increment primary key,
    session_id bigint not null,
    sequence_no int not null,
    number_drawn int not null,
    operation_id varchar(36) not null,
    drawn_by_user_id bigint,
    drawn_at timestamp(6) not null,
    constraint fk_bingo_draw_events_session foreign key (session_id) references bingo_sessions (id),
    constraint fk_bingo_draw_events_drawn_by foreign key (drawn_by_user_id) references users (id),
    constraint uk_bingo_draw_events_session_sequence unique (session_id, sequence_no),
    constraint uk_bingo_draw_events_session_number unique (session_id, number_drawn),
    constraint uk_bingo_draw_events_operation unique (operation_id)
);

create table bingo_win_claims (
    id bigint not null auto_increment primary key,
    ticket_id bigint not null,
    session_id bigint not null,
    claimant_id bigint not null,
    claimed_pattern varchar(20) not null,
    status varchar(30) not null,
    server_validation_passed boolean not null,
    server_validation_code varchar(80),
    server_validation_message varchar(500),
    draw_sequence_snapshot int not null,
    called_numbers_snapshot_json text not null,
    evidence_json text not null,
    operation_id varchar(36) not null,
    review_note varchar(500),
    reviewed_by_user_id bigint,
    reviewed_at timestamp(6),
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint fk_bingo_win_claims_ticket foreign key (ticket_id) references bingo_tickets (id),
    constraint fk_bingo_win_claims_session foreign key (session_id) references bingo_sessions (id),
    constraint fk_bingo_win_claims_claimant foreign key (claimant_id) references users (id),
    constraint fk_bingo_win_claims_reviewed_by foreign key (reviewed_by_user_id) references users (id),
    constraint uk_bingo_win_claims_operation unique (operation_id),
    constraint uk_bingo_win_claims_ticket_pattern_sequence unique (ticket_id, claimed_pattern, draw_sequence_snapshot)
);

create table bingo_prize_entitlements (
    id bigint not null auto_increment primary key,
    win_claim_id bigint not null,
    session_id bigint not null,
    ticket_id bigint not null,
    claimant_id bigint not null,
    prize_type varchar(20) not null,
    settlement_path varchar(20) not null,
    status varchar(30) not null,
    prize_amount decimal(19,2) not null,
    currency varchar(3) not null,
    operation_id varchar(36) not null,
    created_at timestamp(6) not null,
    settled_at timestamp(6),
    settled_by_user_id bigint,
    settlement_reference varchar(120),
    settlement_note varchar(500),
    constraint fk_bingo_prize_entitlements_claim foreign key (win_claim_id) references bingo_win_claims (id),
    constraint fk_bingo_prize_entitlements_session foreign key (session_id) references bingo_sessions (id),
    constraint fk_bingo_prize_entitlements_ticket foreign key (ticket_id) references bingo_tickets (id),
    constraint fk_bingo_prize_entitlements_claimant foreign key (claimant_id) references users (id),
    constraint fk_bingo_prize_entitlements_settled_by foreign key (settled_by_user_id) references users (id),
    constraint uk_bingo_prize_entitlements_claim unique (win_claim_id),
    constraint uk_bingo_prize_entitlements_operation unique (operation_id)
);