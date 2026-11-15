package com.bakaroo.bingo.backend.feature.playerapp.controller;

import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.playerapp.dto.PlayerAnnouncementRequest;
import com.bakaroo.bingo.backend.feature.playerapp.dto.PlayerAnnouncementResponse;
import com.bakaroo.bingo.backend.feature.playerapp.dto.PlayerBannerImageResponse;
import com.bakaroo.bingo.backend.feature.playerapp.dto.PlayerAppSettingsRequest;
import com.bakaroo.bingo.backend.feature.playerapp.dto.PlayerAppSettingsResponse;
import com.bakaroo.bingo.backend.feature.playerapp.service.PlayerAppContentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/player-app")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPlayerAppController {
    private final PlayerAppContentService service;

    public AdminPlayerAppController(PlayerAppContentService service) {
        this.service = service;
    }

    @GetMapping("/content")
    public PlayerAppSettingsResponse content() {
        return service.getSettings();
    }

    @PutMapping("/content")
    public PlayerAppSettingsResponse updateContent(@Valid @RequestBody PlayerAppSettingsRequest request,
            @AuthenticationPrincipal BingoUserPrincipal principal) {
        return service.updateSettings(request, principal);
    }

    @GetMapping("/messages")
    public List<PlayerAnnouncementResponse> messages() {
        return service.listAnnouncements(true, null);
    }

    @GetMapping("/banners")
    public List<PlayerAnnouncementResponse> banners() {
        return service.listBannerSlides(true);
    }

    @PostMapping(value = "/messages/{id}/image", consumes = "multipart/form-data")
    public PlayerAnnouncementResponse uploadBannerImage(@PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal BingoUserPrincipal principal) throws java.io.IOException {
        return service.uploadBannerImage(id, file.getBytes(), file.getContentType(), principal);
    }

    @PostMapping("/messages")
    public PlayerAnnouncementResponse createMessage(@Valid @RequestBody PlayerAnnouncementRequest request,
            @AuthenticationPrincipal BingoUserPrincipal principal) {
        return service.createAnnouncement(request, principal);
    }

    @PutMapping("/messages/{id}")
    public PlayerAnnouncementResponse updateMessage(@PathVariable Long id,
            @Valid @RequestBody PlayerAnnouncementRequest request,
            @AuthenticationPrincipal BingoUserPrincipal principal) {
        return service.updateAnnouncement(id, request, principal);
    }

    @DeleteMapping("/messages/{id}")
    public void deleteMessage(@PathVariable Long id) {
        service.deleteAnnouncement(id);
    }
}
