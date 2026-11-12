alter table users
    add column password_reset_email_status varchar(20) not null default 'NOT_REQUESTED',
    add column password_reset_email_status_updated_at timestamp(6) null;
