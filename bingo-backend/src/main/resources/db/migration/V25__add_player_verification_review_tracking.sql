alter table users
    add column player_verification_status varchar(30) not null default 'NOT_REVIEWED',
    add column player_verification_reason varchar(40),
    add column player_verification_reviewed_by_user_id bigint,
    add column player_verification_reviewed_at timestamp(6),
    add constraint fk_users_player_verification_reviewer
        foreign key (player_verification_reviewed_by_user_id) references users (id);

create index idx_users_player_verification_status
    on users (player_verification_status, id);

create table player_verification_reviews (
    id bigint not null auto_increment primary key,
    player_id bigint not null,
    reviewer_user_id bigint not null,
    status varchar(30) not null,
    reason varchar(40) not null,
    reviewed_at timestamp(6) not null,
    constraint fk_player_verification_reviews_player
        foreign key (player_id) references users (id),
    constraint fk_player_verification_reviews_reviewer
        foreign key (reviewer_user_id) references users (id)
);

create index idx_player_verification_reviews_player_date
    on player_verification_reviews (player_id, reviewed_at desc, id desc);
