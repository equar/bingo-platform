create index idx_audit_log_created_id
    on audit_log_entries (created_at desc, id desc);
