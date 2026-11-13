create table player_app_settings (
    id int not null primary key,
    banner_title_en varchar(160),
    banner_title_am varchar(160),
    banner_body_en varchar(1000),
    banner_body_am varchar(1000),
    banner_image_url varchar(1000),
    terms_url varchar(1000),
    telegram_url varchar(500),
    tiktok_url varchar(500),
    support_phone_numbers varchar(500),
    updated_by_user_id bigint,
    updated_at timestamp(6) not null,
    constraint fk_player_app_settings_actor foreign key (updated_by_user_id) references users(id)
);

insert into player_app_settings (id, updated_at) values (1, current_timestamp(6));

create table player_announcements (
    id bigint not null auto_increment primary key,
    title_en varchar(160) not null,
    title_am varchar(160),
    body_en varchar(2000) not null,
    body_am varchar(2000),
    published boolean not null default false,
    created_by_user_id bigint not null,
    updated_by_user_id bigint not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint fk_player_announcement_creator foreign key (created_by_user_id) references users(id),
    constraint fk_player_announcement_updater foreign key (updated_by_user_id) references users(id)
);

create index idx_player_announcements_published on player_announcements(published, updated_at desc);

create table player_announcement_reads (
    announcement_id bigint not null,
    user_id bigint not null,
    read_at timestamp(6) not null,
    primary key (announcement_id, user_id),
    constraint fk_player_announcement_read_announcement foreign key (announcement_id) references player_announcements(id) on delete cascade,
    constraint fk_player_announcement_read_user foreign key (user_id) references users(id)
);
