-- Support performs both the bank-reference verification and the resulting wallet credit.
-- Admins can correct a support rejection only after independently repeating the verification.
set @drop_deposit_journal_separation = (
    select if(count(*) > 0,
        'alter table deposit_accounting_journals drop check ck_deposit_journal_separation',
        'select 1')
    from information_schema.check_constraints
    where constraint_schema = database()
      and constraint_name = 'ck_deposit_journal_separation'
);
prepare drop_deposit_journal_separation from @drop_deposit_journal_separation;
execute drop_deposit_journal_separation;
deallocate prepare drop_deposit_journal_separation;
