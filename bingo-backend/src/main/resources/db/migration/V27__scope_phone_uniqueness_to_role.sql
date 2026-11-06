alter table users drop index uk_users_normalized_phone;

alter table user_roles add column normalized_phone varchar(32) null;

update user_roles ur
join users u on u.id = ur.user_id
set ur.normalized_phone = u.normalized_phone;

alter table user_roles
    add constraint uk_user_roles_role_phone unique (role, normalized_phone);

create trigger bi_user_roles_sync_phone
before insert on user_roles
for each row
set new.normalized_phone = (select normalized_phone from users where id = new.user_id);

create trigger bu_user_roles_sync_phone
before update on user_roles
for each row
set new.normalized_phone = (select normalized_phone from users where id = new.user_id);

create trigger au_users_sync_role_phones
after update on users
for each row
update user_roles set normalized_phone = new.normalized_phone where user_id = new.id;
