package com.bakaroo.bingo.backend.feature.community.controller;

import static com.bakaroo.bingo.backend.feature.community.dto.CommunityDtos.*;

import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.community.service.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('PLAYER')")
@Validated
@Tag(name = "Player community")
public class CommunityController {
  private final PrivateGameService games;
  private final PlayerProgressService progress;

  public CommunityController(PrivateGameService games, PlayerProgressService progress) {
    this.games = games;
    this.progress = progress;
  }

  @GetMapping("/private-games")
  public List<PrivateGame> mine(@AuthenticationPrincipal BingoUserPrincipal p) {
    return games.mine(p);
  }

  @PostMapping("/private-games")
  public ResponseEntity<PrivateGameCreated> create(
      @AuthenticationPrincipal BingoUserPrincipal p,
      @Valid @RequestBody CreatePrivateGame request,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 120) String key) {
    var result = games.create(p, request, key);
    return ResponseEntity.status(result.statusCode()).body(result.body());
  }

  @PostMapping("/private-games/join")
  public ResponseEntity<PrivateGame> join(
      @AuthenticationPrincipal BingoUserPrincipal p,
      @Valid @RequestBody JoinPrivateGame request,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 120) String key) {
    var result = games.join(p, request, key);
    return ResponseEntity.status(result.statusCode()).body(result.body());
  }

  @GetMapping("/private-games/{id}")
  public PrivateGameDetails details(
      @PathVariable Long id, @AuthenticationPrincipal BingoUserPrincipal p) {
    return games.details(id, p);
  }

  @PostMapping("/private-games/{id}/invite")
  public Invite rotate(@PathVariable Long id, @AuthenticationPrincipal BingoUserPrincipal p) {
    return games.rotate(id, p);
  }

  @PostMapping("/private-games/{id}/start")
  public PrivateGame start(@PathVariable Long id, @AuthenticationPrincipal BingoUserPrincipal p) {
    return games.start(id, p);
  }

  @PostMapping("/private-games/{id}/cancel")
  public PrivateGame cancel(@PathVariable Long id, @AuthenticationPrincipal BingoUserPrincipal p) {
    return games.cancel(id, p);
  }

  @GetMapping("/challenges/daily")
  public DailyChallenges daily(@AuthenticationPrincipal BingoUserPrincipal p) {
    return progress.daily(p);
  }

  @PostMapping("/challenges/{code}/claim")
  public ResponseEntity<ChallengeClaim> claim(
      @PathVariable String code,
      @AuthenticationPrincipal BingoUserPrincipal p,
      @Valid @RequestBody ChallengeClaimRequest request,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 120) String key) {
    var result = progress.claim(code, request, p, key);
    return ResponseEntity.status(result.statusCode()).body(result.body());
  }

  @GetMapping("/leaderboard")
  public Leaderboard leaderboard(
      @AuthenticationPrincipal BingoUserPrincipal p,
      @RequestParam(defaultValue = "WEEKLY") LeaderboardPeriod period,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
      @RequestParam(defaultValue = "25") @Min(1) @Max(50) int size) {
    return progress.leaderboard(p, period, page, size);
  }

  @PutMapping("/leaderboard/profile")
  public LeaderboardProfile profile(
      @AuthenticationPrincipal BingoUserPrincipal p,
      @Valid @RequestBody LeaderboardProfile request) {
    return progress.profile(p, request);
  }
}
