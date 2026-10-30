package com.equar.bingo.backend.feature.community.controller;

import static com.equar.bingo.backend.feature.community.dto.CommunityDtos.*;

import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.community.service.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/community")
@PreAuthorize("hasRole('ADMIN')")
public class CommunityAdminController {
  private final PrivateGameService games;
  private final PlayerProgressService progress;

  public CommunityAdminController(PrivateGameService games, PlayerProgressService progress) {
    this.games = games;
    this.progress = progress;
  }

  @GetMapping("/summary")
  public CommunitySummary summary() {
    return progress.summary();
  }

  @GetMapping("/private-games")
  public List<PrivateGame> rooms() {
    return games.adminGames();
  }

  @PostMapping("/private-games/{id}/disable")
  public void disable(
      @PathVariable Long id,
      @AuthenticationPrincipal BingoUserPrincipal p,
      @Valid @RequestBody Moderation request) {
    games.disable(id, p, request);
  }
}
