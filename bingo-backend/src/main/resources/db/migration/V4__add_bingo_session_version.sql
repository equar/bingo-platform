alter table bingo_sessions
    add column version bigint not null default 0;