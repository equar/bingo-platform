create table float_receiving_bank_accounts (
    id bigint not null auto_increment,
    bank_name varchar(120) not null,
    account_number_ciphertext varchar(512) not null,
    masked_account_number varchar(32) not null,
    account_holder_name varchar(120) not null,
    currency varchar(3) not null,
    active boolean not null default true,
    created_by_user_id bigint not null,
    updated_by_user_id bigint not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    primary key (id),
    constraint fk_float_bank_accounts_created_by foreign key (created_by_user_id) references users (id),
    constraint fk_float_bank_accounts_updated_by foreign key (updated_by_user_id) references users (id),
    constraint ck_float_bank_accounts_currency check (currency = 'ETB')
) engine=InnoDB;

alter table agent_float_top_up_requests
    add column receiving_bank_account_id bigint null,
    add column bank_name_snapshot varchar(120) null,
    add column masked_account_snapshot varchar(32) null,
    add column account_holder_snapshot varchar(120) null,
    add column transfer_reference varchar(120) null,
    add column supporting_evidence_note varchar(500) null,
    add constraint fk_float_top_up_bank_account foreign key (receiving_bank_account_id)
        references float_receiving_bank_accounts (id);

create table float_bank_statement_entries (
    id bigint not null auto_increment,
    receiving_bank_account_id bigint not null,
    transaction_reference varchar(120) not null,
    amount decimal(19,2) not null,
    currency varchar(3) not null,
    transaction_date date not null,
    settlement_state varchar(30) not null,
    source varchar(80) not null,
    synthetic boolean not null,
    recorded_by_user_id bigint not null,
    recorded_at timestamp(6) not null,
    primary key (id),
    constraint fk_float_statement_bank foreign key (receiving_bank_account_id)
        references float_receiving_bank_accounts (id),
    constraint fk_float_statement_recorder foreign key (recorded_by_user_id) references users (id),
    constraint uk_float_statement_reference unique (receiving_bank_account_id, transaction_reference),
    constraint ck_float_statement_amount check (amount > 0),
    constraint ck_float_statement_currency check (currency = 'ETB')
) engine=InnoDB;

create table float_top_up_verification_attempts (
    id bigint not null auto_increment,
    request_id bigint not null,
    statement_entry_id bigint null,
    reviewer_user_id bigint not null,
    outcome varchar(40) not null,
    exception_reason varchar(500) null,
    evidence_source varchar(80) not null,
    created_at timestamp(6) not null,
    primary key (id),
    constraint fk_float_verification_request foreign key (request_id)
        references agent_float_top_up_requests (id),
    constraint fk_float_verification_statement foreign key (statement_entry_id)
        references float_bank_statement_entries (id),
    constraint fk_float_verification_reviewer foreign key (reviewer_user_id) references users (id),
    constraint uk_float_verification_statement unique (statement_entry_id)
) engine=InnoDB;

create table float_accounting_journals (
    id bigint not null auto_increment,
    request_id bigint not null,
    posting_reference varchar(80) not null,
    currency varchar(3) not null,
    simulated boolean not null,
    approver_user_id bigint not null,
    verifier_user_id bigint not null,
    created_at timestamp(6) not null,
    primary key (id),
    constraint fk_float_journal_request foreign key (request_id)
        references agent_float_top_up_requests (id),
    constraint fk_float_journal_approver foreign key (approver_user_id) references users (id),
    constraint fk_float_journal_verifier foreign key (verifier_user_id) references users (id),
    constraint uk_float_journal_request unique (request_id),
    constraint uk_float_journal_posting_reference unique (posting_reference),
    constraint ck_float_journal_currency check (currency = 'ETB')
) engine=InnoDB;

create table float_accounting_journal_lines (
    id bigint not null auto_increment,
    journal_id bigint not null,
    line_number int not null,
    account_code varchar(80) not null,
    debit_amount decimal(19,2) not null default 0.00,
    credit_amount decimal(19,2) not null default 0.00,
    currency varchar(3) not null,
    primary key (id),
    constraint fk_float_journal_line_journal foreign key (journal_id)
        references float_accounting_journals (id),
    constraint uk_float_journal_line_number unique (journal_id, line_number),
    constraint ck_float_journal_line_side check (
        (debit_amount > 0 and credit_amount = 0) or (credit_amount > 0 and debit_amount = 0)
    ),
    constraint ck_float_journal_line_currency check (currency = 'ETB')
) engine=InnoDB;

create index ix_float_statement_daily on float_bank_statement_entries (transaction_date, receiving_bank_account_id);
create index ix_float_verification_request on float_top_up_verification_attempts (request_id, created_at);
create index ix_float_journal_created on float_accounting_journals (created_at, id);
