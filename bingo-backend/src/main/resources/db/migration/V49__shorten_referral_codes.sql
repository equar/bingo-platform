update users
set referral_code = upper(lpad(conv(id, 10, 36), 6, '0'));

alter table users
    modify referral_code varchar(6) not null;
