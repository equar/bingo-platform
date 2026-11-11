set @activation_email_status_column_exists = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 'users'
      and column_name = 'activation_email_status'
);

set @activation_email_status_ddl = if(
    @activation_email_status_column_exists = 0,
    'alter table users add column activation_email_status varchar(20) not null default ''UNKNOWN''',
    'select 1'
);

prepare activation_email_status_stmt from @activation_email_status_ddl;
execute activation_email_status_stmt;
deallocate prepare activation_email_status_stmt;

update users
set activation_email_status = 'NOT_SENT'
where status <> 'PENDING';
