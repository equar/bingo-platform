alter table wallets
    add column promotional_balance decimal(19, 2) not null default 0.00;

alter table wallet_transactions
    add column transferable_balance_before decimal(19, 2) null;

alter table wallet_transactions
    add column transferable_balance_after decimal(19, 2) null;

alter table wallet_transactions
    add column promotional_balance_before decimal(19, 2) null;

alter table wallet_transactions
    add column promotional_balance_after decimal(19, 2) null;

alter table wallet_transactions
    add column metadata_json longtext null;

alter table bingo_tickets
    add column wallet_funding_snapshot_json longtext null;