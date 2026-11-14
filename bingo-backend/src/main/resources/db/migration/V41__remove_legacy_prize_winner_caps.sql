update bingo_rooms room
join (
    select source.id,
           json_arrayagg(json_remove(prize.item, '$.maxWinners') order by prize.ordinality) as prizes
    from bingo_rooms source
    cross join json_table(
        source.configured_prizes_json,
        '$[*]' columns (
            ordinality for ordinality,
            item json path '$'
        )
    ) prize
    group by source.id
) normalized on normalized.id = room.id
set room.configured_prizes_json = normalized.prizes;

update bingo_sessions session
join (
    select source.id,
           json_arrayagg(json_remove(prize.item, '$.maxWinners') order by prize.ordinality) as prizes
    from bingo_sessions source
    cross join json_table(
        source.prizes_snapshot_json,
        '$[*]' columns (
            ordinality for ordinality,
            item json path '$'
        )
    ) prize
    group by source.id
) normalized on normalized.id = session.id
set session.prizes_snapshot_json = normalized.prizes;
