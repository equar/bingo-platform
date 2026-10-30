alter table bingo_rooms add column private_room boolean not null default false;
alter table bingo_rooms drop index uk_bingo_rooms_name;
alter table bingo_rooms add column public_name varchar(120) generated always as
    (case when private_room=false then name else null end) stored;
alter table bingo_rooms add constraint uk_bingo_rooms_name unique(public_name);
create index idx_rooms_visibility_active on bingo_rooms(private_room, active);

create table private_games (
    room_id bigint not null primary key,
    session_id bigint not null,
    host_id bigint not null,
    invite_hash char(64) not null,
    invite_expires_at timestamp(6) not null,
    created_at timestamp(6) not null,
    constraint fk_private_game_room foreign key (room_id) references bingo_rooms(id),
    constraint fk_private_game_session foreign key (session_id) references bingo_sessions(id),
    constraint fk_private_game_host foreign key (host_id) references users(id),
    constraint uk_private_game_invite unique(invite_hash),
    constraint uk_private_game_session unique(session_id)
);
create index idx_private_game_host on private_games(host_id, created_at);
create table private_game_members (
    room_id bigint not null,
    player_id bigint not null,
    joined_at timestamp(6) not null,
    primary key(room_id, player_id),
    constraint fk_private_member_game foreign key (room_id) references private_games(room_id),
    constraint fk_private_member_player foreign key (player_id) references users(id)
);
create index idx_private_member_player on private_game_members(player_id, room_id);

create table player_challenge_claims (
    id bigint not null auto_increment primary key,
    player_id bigint not null,
    business_date date not null,
    challenge_code varchar(40) not null,
    rule_version integer not null,
    points integer not null,
    claimed_at timestamp(6) not null,
    constraint fk_challenge_player foreign key (player_id) references users(id),
    constraint uk_player_challenge_day unique(player_id, business_date, challenge_code),
    constraint ck_challenge_positive_points check(points > 0 and points <= 100)
);
create index idx_challenge_leaderboard on player_challenge_claims(business_date, player_id, points);
create table leaderboard_profiles (
    player_id bigint not null primary key,
    public_alias varchar(24) not null,
    visible boolean not null default false,
    updated_at timestamp(6) not null,
    constraint fk_leaderboard_player foreign key (player_id) references users(id),
    constraint uk_leaderboard_alias unique(public_alias)
);
