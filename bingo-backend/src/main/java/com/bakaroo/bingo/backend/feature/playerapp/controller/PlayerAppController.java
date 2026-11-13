package com.bakaroo.bingo.backend.feature.playerapp.controller;

import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.playerapp.dto.PlayerAnnouncementResponse;
import com.bakaroo.bingo.backend.feature.playerapp.dto.PlayerAppSettingsResponse;
import com.bakaroo.bingo.backend.feature.playerapp.service.PlayerAppContentService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/player-app")
@PreAuthorize("hasRole('PLAYER')")
public class PlayerAppController {
    private final PlayerAppContentService service;

    public PlayerAppController(PlayerAppContentService service) {
        this.service = service;
    }

    @GetMapping("/content")
    public PlayerAppSettingsResponse content() {
        return service.getSettings();
    }

    @GetMapping("/messages")
    public List<PlayerAnnouncementResponse> messages(@AuthenticationPrincipal BingoUserPrincipal principal) {
        return service.listAnnouncements(false, principal.getUser().getId());
    }

    @PostMapping("/messages/{id}/read")
    public void markRead(@PathVariable Long id, @AuthenticationPrincipal BingoUserPrincipal principal) {
        service.markAnnouncementRead(id, principal.getUser().getId());
    }
}
