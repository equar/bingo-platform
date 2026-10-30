package com.equar.bingo.backend.feature.community.repository;

import static com.equar.bingo.backend.feature.community.dto.CommunityDtos.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CommunityRepository {
  private final JdbcTemplate jdbc;

  public CommunityRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void lockPlayer(Long id) {
    jdbc.queryForObject("select id from users where id=? for update", Long.class, id);
  }

  public record PrivateRow(Long roomId, Long sessionId, Long hostId, Instant expiresAt) {}

  private PrivateRow privateRow(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new PrivateRow(
        rs.getLong("room_id"),
        rs.getLong("session_id"),
        rs.getLong("host_id"),
        rs.getTimestamp("invite_expires_at").toInstant());
  }

  public Optional<PrivateRow> privateGame(Long roomId, boolean lock) {
    return jdbc
        .query(
            "select * from private_games where room_id=?" + (lock ? " for update" : ""),
            (rs, n) -> privateRow(rs),
            roomId)
        .stream()
        .findFirst();
  }

  public Optional<Long> invitedRoom(String hash) {
    return jdbc
        .query(
            "select room_id from private_games where invite_hash=?", (rs, n) -> rs.getLong(1), hash)
        .stream()
        .findFirst();
  }

  public String inviteHash(Long roomId) {
    return jdbc.queryForObject(
        "select invite_hash from private_games where room_id=?", String.class, roomId);
  }

  public void createPrivate(
      Long room, Long session, Long host, String hash, Instant expires, Instant now) {
    jdbc.update(
        "insert into"
            + " private_games(room_id,session_id,host_id,invite_hash,invite_expires_at,created_at)"
            + " values(?,?,?,?,?,?)",
        room,
        session,
        host,
        hash,
        Timestamp.from(expires),
        Timestamp.from(now));
    addMember(room, host, now);
  }

  public boolean member(Long room, Long user) {
    return jdbc.queryForObject(
            "select count(*) from private_game_members where room_id=? and player_id=?",
            Long.class,
            room,
            user)
        > 0;
  }

  public void addMember(Long room, Long user, Instant now) {
    jdbc.update(
        "insert into private_game_members(room_id,player_id,joined_at) values(?,?,?)",
        room,
        user,
        Timestamp.from(now));
  }

  public int members(Long room) {
    return jdbc.queryForObject(
        "select count(*) from private_game_members where room_id=?", Integer.class, room);
  }

  public long openPrivateCount(Long host) {
    return jdbc.queryForObject(
        "select count(*) from private_games p join bingo_sessions s on s.id=p.session_id join"
            + " bingo_rooms r on r.id=p.room_id where p.host_id=? and s.state in"
            + " ('OPEN','DRAFT','STARTED','DRAWING') and r.active=true",
        Long.class,
        host);
  }

  public List<Long> myRooms(Long user) {
    return jdbc.query(
        "select p.room_id from private_games p join private_game_members m on m.room_id=p.room_id"
            + " where m.player_id=? order by p.created_at desc limit 100",
        (rs, n) -> rs.getLong(1),
        user);
  }

  public List<Long> adminRooms() {
    return jdbc.query(
        "select room_id from private_games order by created_at desc limit 100",
        (rs, n) -> rs.getLong(1));
  }

  public void rotate(Long room, String hash, Instant expires) {
    jdbc.update(
        "update private_games set invite_hash=?,invite_expires_at=? where room_id=?",
        hash,
        Timestamp.from(expires),
        room);
  }

  public int ticketedPlayers(Long session) {
    return jdbc.queryForObject(
        "select count(distinct owner_id) from bingo_tickets where session_id=?",
        Integer.class,
        session);
  }

  public long ticketCount(Long session) {
    return jdbc.queryForObject(
        "select count(*) from bingo_tickets where session_id=?", Long.class, session);
  }

  public record Progress(int sessions, int rooms, int wins) {}

  public Progress progress(Long player, Instant from, Instant until) {
    Map<String, Object> counts =
        jdbc.queryForMap(
            """
            select count(distinct s.id) as games, count(distinct s.room_id) as rooms
            from bingo_tickets t join bingo_sessions s on s.id=t.session_id
            join bingo_rooms r on r.id=s.room_id
            where t.owner_id=? and r.private_room=false and s.state in ('CLOSED','SETTLED')
              and s.started_at is not null and s.ended_at>=? and s.ended_at<?
            """,
            player,
            Timestamp.from(from),
            Timestamp.from(until));
    int wins =
        jdbc.queryForObject(
            """
            select count(distinct w.session_id) from bingo_win_claims w
            join bingo_sessions s on s.id=w.session_id join bingo_rooms r on r.id=s.room_id
            where w.claimant_id=? and r.private_room=false and w.status='APPROVED'
              and w.server_validation_passed=true and s.state in ('CLOSED','SETTLED')
              and s.ended_at>=? and s.ended_at<?
            """,
            Integer.class,
            player,
            Timestamp.from(from),
            Timestamp.from(until));
    return new Progress(
        ((Number) counts.get("games")).intValue(), ((Number) counts.get("rooms")).intValue(), wins);
  }

  public Map<String, Integer> claimed(Long player, LocalDate day) {
    Map<String, Integer> result = new HashMap<>();
    jdbc.query(
        "select challenge_code,points from player_challenge_claims where player_id=? and"
            + " business_date=?",
        rs -> {
          result.put(rs.getString(1), rs.getInt(2));
        },
        player,
        day);
    return result;
  }

  public void claim(Long player, LocalDate day, String code, int points, Instant now) {
    jdbc.update(
        "insert into"
            + " player_challenge_claims(player_id,business_date,challenge_code,rule_version,points,claimed_at)"
            + " values(?,?,?,1,?,?)",
        player,
        day,
        code,
        points,
        Timestamp.from(now));
  }

  public long totalPoints(Long player) {
    return jdbc.queryForObject(
        "select coalesce(sum(points),0) from player_challenge_claims where player_id=?",
        Long.class,
        player);
  }

  public LeaderboardProfile profile(Long player) {
    return jdbc
        .query(
            "select public_alias,visible from leaderboard_profiles where player_id=?",
            (rs, n) -> new LeaderboardProfile(rs.getString(1), rs.getBoolean(2)),
            player)
        .stream()
        .findFirst()
        .orElse(new LeaderboardProfile(null, false));
  }

  public void saveProfile(Long player, LeaderboardProfile profile, Instant now) {
    if (jdbc.queryForObject(
            "select count(*) from leaderboard_profiles where player_id=?", Integer.class, player)
        > 0) {
      jdbc.update(
          "update leaderboard_profiles set public_alias=?,visible=?,updated_at=? where player_id=?",
          profile.alias(),
          profile.visible(),
          Timestamp.from(now),
          player);
    } else {
      jdbc.update(
          "insert into leaderboard_profiles(player_id,public_alias,visible,updated_at)"
              + " values(?,?,?,?)",
          player,
          profile.alias(),
          profile.visible(),
          Timestamp.from(now));
    }
  }

  // RANK gives shared ranks for equal points; alias/player ID make page ordering deterministic.
  private static final String RANKED =
      """
      with scores as (
        select p.player_id,p.public_alias,sum(c.points) as points
        from leaderboard_profiles p join users u on u.id=p.player_id
        join player_challenge_claims c on c.player_id=p.player_id
        where p.visible=true and u.status='ACTIVE' and c.business_date>=? and c.business_date<?
        group by p.player_id,p.public_alias
      ), ranked as (select *,rank() over(order by points desc) as position from scores)
      """;

  public List<LeaderboardEntry> ranks(
      LocalDate from, LocalDate until, Long player, int page, int size) {
    return jdbc.query(
        RANKED
            + "select * from ranked order by points desc,public_alias asc,player_id asc limit ?"
            + " offset ?",
        (rs, n) ->
            new LeaderboardEntry(
                rs.getLong("position"),
                rs.getString("public_alias"),
                rs.getLong("points"),
                rs.getLong("player_id") == player.longValue()),
        from,
        until,
        size,
        page * size);
  }

  public Long rank(LocalDate from, LocalDate until, Long player) {
    return jdbc
        .query(
            RANKED + "select position from ranked where player_id=?",
            (rs, n) -> rs.getLong(1),
            from,
            until,
            player)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public long rankedCount(LocalDate from, LocalDate until) {
    return jdbc.queryForObject(RANKED + "select count(*) from ranked", Long.class, from, until);
  }

  public long periodPoints(Long player, LocalDate from, LocalDate until) {
    return jdbc.queryForObject(
        "select coalesce(sum(points),0) from player_challenge_claims where player_id=? and"
            + " business_date>=? and business_date<?",
        Long.class,
        player,
        from,
        until);
  }

  public CommunitySummary summary(LocalDate day, String zone) {
    return new CommunitySummary(
        jdbc.queryForObject("select count(*) from private_games", Long.class),
        jdbc.queryForObject(
            "select count(*) from player_challenge_claims where business_date=?", Long.class, day),
        jdbc.queryForObject(
            "select coalesce(sum(points),0) from player_challenge_claims where business_date=?",
            Long.class,
            day),
        jdbc.queryForObject(
            "select count(*) from leaderboard_profiles where visible=true", Long.class),
        zone);
  }
}
