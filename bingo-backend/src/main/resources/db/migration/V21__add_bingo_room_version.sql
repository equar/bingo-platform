alter table bingo_rooms
    add column version bigint not null default 0;
