alter table idempotency_records
    drop primary key,
    add column id bigint not null auto_increment primary key first,
    add column actor_id bigint null after id,
    add unique key uk_idempotency_records_actor_key (actor_id, idempotency_key);

alter table payout_verification_records
    add column operation_id varchar(36) not null unique after disbursed_amount;