package com.equar.bingo.backend.feature.community.service;

import com.equar.bingo.backend.common.exception.NotFoundException;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.game.repository.GameSessionRepository;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameAccessService {
  private final JdbcTemplate jdbc;
  private final GameSessionRepository sessions;

  public GameAccessService(JdbcTemplate jdbc, GameSessionRepository sessions) {
    this.jdbc = jdbc;
    this.sessions = sessions;
  }

  @Transactional(readOnly = true)
  public void requireRoomAccess(Long roomId) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    BingoUserPrincipal principal =
        auth != null && auth.getPrincipal() instanceof BingoUserPrincipal p ? p : null;
    requireRoomAccess(roomId, principal);
  }

  @Transactional(readOnly = true)
  public void requireRoomAccess(Long roomId, BingoUserPrincipal principal) {
    Boolean restricted =
        jdbc.queryForObject(
            "select private_room from bingo_rooms where id=?", Boolean.class, roomId);
    if (!Boolean.TRUE.equals(restricted)) return;
    if (principal != null
        && principal.isEnabled()
        && principal.getUser().getRoles().contains(UserRole.ADMIN)) return;
    if (principal == null
        || !principal.isEnabled()
        || !principal.getUser().getRoles().contains(UserRole.PLAYER)
        || !isMember(roomId, principal.getUser().getId())) {
      throw new NotFoundException("ROOM_NOT_FOUND", "Room not found");
    }
  }

  public boolean isMember(Long roomId, Long playerId) {
    return jdbc.queryForObject(
            "select count(*) from private_game_members where room_id=? and player_id=?",
            Long.class,
            roomId,
            playerId)
        > 0;
  }

  @Transactional(readOnly = true)
  public void requireSessionAccess(Long sessionId, BingoUserPrincipal principal) {
    var session =
        sessions
            .findById(sessionId)
            .orElseThrow(
                () -> new NotFoundException("SESSION_NOT_FOUND", "Game session not found"));
    requireRoomAccess(session.getRoom().getId(), principal);
  }
}
