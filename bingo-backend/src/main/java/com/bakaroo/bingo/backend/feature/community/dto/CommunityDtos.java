package com.equar.bingo.backend.feature.community.dto;

import com.equar.bingo.backend.feature.game.dto.PlayerGameSessionResponse;
import com.equar.bingo.backend.feature.game.dto.PlayerRoomResponse;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class CommunityDtos {
  private CommunityDtos() {}

  public record CreatePrivateGame(
      @NotNull Long templateRoomId,
      @NotBlank @Size(max = 80) String name,
      @NotNull Instant scheduledStartAt) {}

  public record JoinPrivateGame(
      @NotBlank @Pattern(regexp = "[A-Za-z2-9\\s-]{12,24}") String code) {}

  public record PrivateGame(
      Long roomId,
      Long sessionId,
      String name,
      String state,
      boolean host,
      boolean active,
      int memberCount,
      int capacity,
      Instant inviteExpiresAt,
      Instant scheduledStartAt) {}

  public record PrivateGameCreated(PrivateGame game) {}

  public record Invite(String code, Instant expiresAt) {}

  public record PrivateGameDetails(
      PrivateGame game, PlayerRoomResponse room, PlayerGameSessionResponse session) {}

  public record Challenge(
      String code,
      String metric,
      int target,
      int progress,
      int points,
      boolean completed,
      boolean claimed,
      int ruleVersion) {}

  public record DailyChallenges(
      LocalDate businessDate,
      String timezone,
      Instant resetsAt,
      int totalPoints,
      List<Challenge> challenges) {}

  public record ChallengeClaimRequest(@NotNull LocalDate businessDate) {}

  public record ChallengeClaim(String code, LocalDate businessDate, int points, boolean claimed) {}

  public enum LeaderboardPeriod {
    DAILY,
    WEEKLY,
    ALL_TIME
  }

  public record LeaderboardProfile(
      @Size(min = 3, max = 24) @Pattern(regexp = "[\\p{L}\\p{N} _-]+") String alias,
      boolean visible) {}

  public record LeaderboardEntry(long rank, String alias, long points, boolean me) {}

  public record Leaderboard(
      LeaderboardPeriod period,
      String timezone,
      LocalDate startsOn,
      LocalDate endsBefore,
      long totalPlayers,
      int page,
      int size,
      List<LeaderboardEntry> entries,
      Long myRank,
      long myPoints,
      LeaderboardProfile profile) {}

  public record Moderation(@NotBlank @Size(max = 300) String reason) {}

  public record CommunitySummary(
      long privateGames,
      long dailyClaims,
      long dailyPoints,
      long visiblePlayers,
      String timezone) {}
}
