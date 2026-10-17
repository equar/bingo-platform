alter table idempotency_records
    add column id bigint not null auto_increment first;

alter table idempotency_records
    drop primary key;

alter table idempotency_records
    add primary key (id);

alter table idempotency_records
    add column actor_id bigint null;

alter table idempotency_records
    add constraint uk_idempotency_records_actor_key unique (actor_id, idempotency_key);

alter table payout_verification_records
    add column operation_id varchar(36) not null;

alter table payout_verification_records
    add constraint uk_payout_verification_records_operation_id unique (operation_id);