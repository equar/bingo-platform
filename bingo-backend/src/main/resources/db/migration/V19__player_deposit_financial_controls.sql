alter table deposit_claims
    modify column status varchar(40) not null,
    add column verified_by_user_id bigint null,
    add column verified_at timestamp(6) null,
    add constraint fk_deposit_claim_verified_by foreign key (verified_by_user_id) references users (id);

alter table float_top_up_verification_attempts
    modify column request_id bigint null,
    add column deposit_claim_id bigint null,
    add constraint fk_bank_verification_deposit_claim foreign key (deposit_claim_id) references deposit_claims (id),
    add constraint ck_bank_verification_subject check ((request_id is not null) <> (deposit_claim_id is not null));

create index ix_bank_verification_deposit_claim
    on float_top_up_verification_attempts (deposit_claim_id, created_at);

create table deposit_accounting_journals (
    id bigint not null auto_increment,
    deposit_claim_id bigint not null,
    posting_reference varchar(80) not null,
    currency varchar(3) not null,
    simulated boolean not null,
    verifier_user_id bigint not null,
    approver_user_id bigint not null,
    created_at timestamp(6) not null,
    primary key (id),
    constraint fk_deposit_journal_claim foreign key (deposit_claim_id) references deposit_claims (id),
    constraint fk_deposit_journal_verifier foreign key (verifier_user_id) references users (id),
    constraint fk_deposit_journal_approver foreign key (approver_user_id) references users (id),
    constraint uk_deposit_journal_claim unique (deposit_claim_id),
    constraint uk_deposit_journal_reference unique (posting_reference),
    constraint ck_deposit_journal_currency check (currency = 'ETB'),
    constraint ck_deposit_journal_separation check (verifier_user_id <> approver_user_id)
) engine=InnoDB;

create table deposit_accounting_journal_lines (
    id bigint not null auto_increment,
    journal_id bigint not null,
    line_number int not null,
    account_code varchar(80) not null,
    debit_amount decimal(19,2) not null default 0.00,
    credit_amount decimal(19,2) not null default 0.00,
    currency varchar(3) not null,
    primary key (id),
    constraint fk_deposit_journal_line_journal foreign key (journal_id) references deposit_accounting_journals (id),
    constraint uk_deposit_journal_line_number unique (journal_id, line_number),
    constraint ck_deposit_journal_line_side check (
        (debit_amount > 0 and credit_amount = 0) or (credit_amount > 0 and debit_amount = 0)
    ),
    constraint ck_deposit_journal_line_currency check (currency = 'ETB')
) engine=InnoDB;

create table wallet_adjustment_requests (
    id bigint not null auto_increment,
    player_id bigint not null,
    maker_user_id bigint not null,
    amount decimal(19,2) not null,
    currency varchar(3) not null,
    reason varchar(350) not null,
    supporting_reference varchar(120) not null,
    status varchar(20) not null,
    operation_id varchar(36) not null,
    checker_user_id bigint null,
    review_note varchar(500) null,
    submitted_at timestamp(6) not null,
    reviewed_at timestamp(6) null,
    primary key (id),
    constraint fk_wallet_adjustment_player foreign key (player_id) references users (id),
    constraint fk_wallet_adjustment_maker foreign key (maker_user_id) references users (id),
    constraint fk_wallet_adjustment_checker foreign key (checker_user_id) references users (id),
    constraint uk_wallet_adjustment_operation unique (operation_id),
    constraint ck_wallet_adjustment_amount check (amount <> 0),
    constraint ck_wallet_adjustment_currency check (currency = 'ETB'),
    constraint ck_wallet_adjustment_separation check (checker_user_id is null or checker_user_id <> maker_user_id)
) engine=InnoDB;

create index ix_wallet_adjustment_player_status
    on wallet_adjustment_requests (player_id, status, submitted_at);
create index ix_wallet_adjustment_status
    on wallet_adjustment_requests (status, submitted_at);

create table wallet_adjustment_accounting_journals (
    id bigint not null auto_increment,
    adjustment_request_id bigint not null,
    posting_reference varchar(80) not null,
    currency varchar(3) not null,
    simulated boolean not null,
    maker_user_id bigint not null,
    checker_user_id bigint not null,
    created_at timestamp(6) not null,
    primary key (id),
    constraint fk_adjustment_journal_request foreign key (adjustment_request_id) references wallet_adjustment_requests (id),
    constraint fk_adjustment_journal_maker foreign key (maker_user_id) references users (id),
    constraint fk_adjustment_journal_checker foreign key (checker_user_id) references users (id),
    constraint uk_adjustment_journal_request unique (adjustment_request_id),
    constraint uk_adjustment_journal_reference unique (posting_reference),
    constraint ck_adjustment_journal_currency check (currency = 'ETB'),
    constraint ck_adjustment_journal_separation check (maker_user_id <> checker_user_id)
) engine=InnoDB;

create table wallet_adjustment_accounting_journal_lines (
    id bigint not null auto_increment,
    journal_id bigint not null,
    line_number int not null,
    account_code varchar(80) not null,
    debit_amount decimal(19,2) not null default 0.00,
    credit_amount decimal(19,2) not null default 0.00,
    currency varchar(3) not null,
    primary key (id),
    constraint fk_adjustment_journal_line_journal foreign key (journal_id)
        references wallet_adjustment_accounting_journals (id),
    constraint uk_adjustment_journal_line_number unique (journal_id, line_number),
    constraint ck_adjustment_journal_line_side check (
        (debit_amount > 0 and credit_amount = 0) or (credit_amount > 0 and debit_amount = 0)
    ),
    constraint ck_adjustment_journal_line_currency check (currency = 'ETB')
) engine=InnoDB;
