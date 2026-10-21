create table authentication_attempt_buckets (
    bucket_key char(64) not null primary key,
    attempt_count int not null,
    window_started_at timestamp(6) not null,
    locked_until timestamp(6),
    last_attempt_at timestamp(6) not null
);
