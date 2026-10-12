alter table users add column email varchar(255) null;
create unique index uk_users_email on users (email);