alter table player_announcements
    add column show_in_carousel boolean not null default false,
    add column display_order int not null default 0;

create index idx_player_announcements_carousel
    on player_announcements(published, show_in_carousel, display_order, id);
