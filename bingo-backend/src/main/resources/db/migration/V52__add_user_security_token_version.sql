alter table users
    add column security_token_version bigint not null default 0;
