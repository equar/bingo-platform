-- V7 introduced nullable actors for compatibility with legacy records. All
-- current callers use authenticated User IDs; enforce that shared identity.
-- Keep both changes in one InnoDB ALTER so invalid legacy data fails safely
-- without leaving only one of the constraints installed.
alter table idempotency_records
    modify column actor_id bigint not null,
    add constraint fk_idempotency_records_actor
        foreign key (actor_id) references users (id);
