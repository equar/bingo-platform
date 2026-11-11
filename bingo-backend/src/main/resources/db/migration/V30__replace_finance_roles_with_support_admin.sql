insert ignore into user_roles (user_id, role, normalized_phone)
select user_id, 'SUPPORT', normalized_phone from user_roles where role = 'FINANCE_VERIFIER';

insert ignore into user_roles (user_id, role, normalized_phone)
select user_id, 'ADMIN', normalized_phone from user_roles where role = 'FINANCE_APPROVER';

delete from user_roles where role in ('FINANCE_VERIFIER', 'FINANCE_APPROVER');
