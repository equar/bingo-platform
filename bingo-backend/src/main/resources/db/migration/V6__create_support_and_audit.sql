create table support_tickets (
    id bigint not null auto_increment primary key,
    ticket_number varchar(36) not null unique,
    requester_user_id bigint not null,
    subject varchar(180) not null,
    description text not null,
    category varchar(30) not null,
    priority varchar(20) not null,
    status varchar(30) not null,
    linked_entity_type varchar(80),
    linked_entity_id bigint,
    linked_entity_reference varchar(120),
    assigned_to_user_id bigint,
    resolution_summary varchar(1000),
    operation_id varchar(36) not null unique,
    resolved_at timestamp(6),
    closed_at timestamp(6),
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint fk_support_ticket_requester foreign key (requester_user_id) references users(id),
    constraint fk_support_ticket_assignee foreign key (assigned_to_user_id) references users(id)
);

create table support_messages (
    id bigint not null auto_increment primary key,
    ticket_id bigint not null,
    author_user_id bigint not null,
    body text not null,
    operation_id varchar(36) not null unique,
    created_at timestamp(6) not null,
    constraint fk_support_message_ticket foreign key (ticket_id) references support_tickets(id),
    constraint fk_support_message_author foreign key (author_user_id) references users(id)
);

create table internal_notes (
    id bigint not null auto_increment primary key,
    ticket_id bigint not null,
    author_user_id bigint not null,
    body text not null,
    operation_id varchar(36) not null unique,
    created_at timestamp(6) not null,
    constraint fk_internal_note_ticket foreign key (ticket_id) references support_tickets(id),
    constraint fk_internal_note_author foreign key (author_user_id) references users(id)
);

create table audit_log_entries (
    id bigint not null auto_increment primary key,
    actor_user_id bigint,
    action varchar(80) not null,
    target_type varchar(80) not null,
    target_id bigint,
    reason varchar(500),
    operation_id varchar(36) not null unique,
    metadata_json text,
    created_at timestamp(6) not null,
    constraint fk_audit_log_actor foreign key (actor_user_id) references users(id)
);

create index idx_support_tickets_status_created on support_tickets(status, created_at desc);
create index idx_support_tickets_requester_created on support_tickets(requester_user_id, created_at desc);
create index idx_support_messages_ticket_created on support_messages(ticket_id, created_at asc);
create index idx_internal_notes_ticket_created on internal_notes(ticket_id, created_at asc);
create index idx_audit_logs_action_created on audit_log_entries(action, created_at desc);