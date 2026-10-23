alter table bingo_sessions
    add constraint uk_bingo_sessions_room_schedule unique (room_id, scheduled_start_at);
