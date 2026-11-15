create table player_session_ticket_selections (
    session_id bigint not null,
    user_id bigint not null,
    ticket_ids_json json not null,
    updated_at timestamp(6) not null,
    primary key (session_id, user_id),
    constraint fk_ticket_selection_session foreign key (session_id) references bingo_sessions(id) on delete cascade,
    constraint fk_ticket_selection_user foreign key (user_id) references users(id) on delete cascade
);
