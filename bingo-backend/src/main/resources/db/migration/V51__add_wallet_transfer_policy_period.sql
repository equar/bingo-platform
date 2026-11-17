alter table wallet_transfer_policies
    change column daily_outgoing_limit_amount period_outgoing_limit_amount decimal(19, 2) not null,
    add column limit_period varchar(16) not null default 'DAILY';
