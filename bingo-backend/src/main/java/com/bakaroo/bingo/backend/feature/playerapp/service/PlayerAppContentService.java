package com.bakaroo.bingo.backend.feature.playerapp.service;

import com.bakaroo.bingo.backend.common.exception.NotFoundException;
import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.playerapp.dto.PlayerAnnouncementRequest;
import com.bakaroo.bingo.backend.feature.playerapp.dto.PlayerAnnouncementResponse;
import com.bakaroo.bingo.backend.feature.playerapp.dto.PlayerAppSettingsRequest;
import com.bakaroo.bingo.backend.feature.playerapp.dto.PlayerAppSettingsResponse;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerAppContentService {
    private final JdbcTemplate jdbc;

    public PlayerAppContentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PlayerAppSettingsResponse getSettings() {
        return jdbc.queryForObject("""
                select banner_title_en, banner_title_am, banner_body_en, banner_body_am,
                       banner_image_url, terms_url, telegram_url, tiktok_url,
                       support_phone_numbers, updated_at
                from player_app_settings where id = 1
                """, (rs, row) -> mapSettings(rs));
    }

    @Transactional
    public PlayerAppSettingsResponse updateSettings(PlayerAppSettingsRequest request,
            BingoUserPrincipal principal) {
        validateUrl(request.bannerImageUrl(), "bannerImageUrl");
        validateUrl(request.termsUrl(), "termsUrl");
        validateUrl(request.telegramUrl(), "telegramUrl");
        validateUrl(request.tiktokUrl(), "tiktokUrl");
        jdbc.update("""
                update player_app_settings set banner_title_en = ?, banner_title_am = ?,
                    banner_body_en = ?, banner_body_am = ?, banner_image_url = ?, terms_url = ?,
                    telegram_url = ?, tiktok_url = ?, support_phone_numbers = ?,
                    updated_by_user_id = ?, updated_at = ? where id = 1
                """, request.bannerTitleEn(), request.bannerTitleAm(), request.bannerBodyEn(),
                request.bannerBodyAm(), trim(request.bannerImageUrl()), trim(request.termsUrl()),
                trim(request.telegramUrl()), trim(request.tiktokUrl()), trim(request.supportPhoneNumbers()),
                principal.getUser().getId(), Timestamp.from(Instant.now()));
        return getSettings();
    }

    @Transactional(readOnly = true)
    public List<PlayerAnnouncementResponse> listAnnouncements(boolean admin, Long userId) {
        String sql = admin ? """
                select a.id, a.title_en, a.title_am, a.body_en, a.body_am, a.published,
                       false as is_read, a.created_at, a.updated_at
                from player_announcements a order by a.updated_at desc, a.id desc
                """ : """
                select a.id, a.title_en, a.title_am, a.body_en, a.body_am, a.published,
                       (r.announcement_id is not null) as is_read, a.created_at, a.updated_at
                from player_announcements a
                left join player_announcement_reads r on r.announcement_id = a.id and r.user_id = ?
                where a.published = true order by a.created_at desc, a.id desc
                """;
        return admin
                ? jdbc.query(sql, (rs, row) -> mapAnnouncement(rs))
                : jdbc.query(sql, (rs, row) -> mapAnnouncement(rs), userId);
    }

    @Transactional
    public PlayerAnnouncementResponse createAnnouncement(PlayerAnnouncementRequest request,
            BingoUserPrincipal principal) {
        Instant now = Instant.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                insert into player_announcements
                    (title_en, title_am, body_en, body_am, published, created_by_user_id,
                     updated_by_user_id, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] { "id" });
            statement.setString(1, request.titleEn().trim());
            statement.setString(2, trim(request.titleAm()));
            statement.setString(3, request.bodyEn().trim());
            statement.setString(4, trim(request.bodyAm()));
            statement.setBoolean(5, request.published());
            statement.setLong(6, principal.getUser().getId());
            statement.setLong(7, principal.getUser().getId());
            statement.setTimestamp(8, Timestamp.from(now));
            statement.setTimestamp(9, Timestamp.from(now));
            return statement;
        }, keyHolder);
        Long id = keyHolder.getKey().longValue();
        return announcement(id, false);
    }

    @Transactional
    public PlayerAnnouncementResponse updateAnnouncement(Long id, PlayerAnnouncementRequest request,
            BingoUserPrincipal principal) {
        int updated = jdbc.update("""
                update player_announcements set title_en = ?, title_am = ?, body_en = ?, body_am = ?,
                    published = ?, updated_by_user_id = ?, updated_at = ? where id = ?
                """, request.titleEn().trim(), trim(request.titleAm()), request.bodyEn().trim(),
                trim(request.bodyAm()), request.published(), principal.getUser().getId(), Timestamp.from(Instant.now()), id);
        if (updated == 0) throw new NotFoundException("ANNOUNCEMENT_NOT_FOUND", "Announcement not found");
        return announcement(id, false);
    }

    @Transactional
    public void deleteAnnouncement(Long id) {
        if (jdbc.update("delete from player_announcements where id = ?", id) == 0) {
            throw new NotFoundException("ANNOUNCEMENT_NOT_FOUND", "Announcement not found");
        }
    }

    @Transactional
    public void markAnnouncementRead(Long id, Long userId) {
        if (jdbc.queryForObject("select count(*) from player_announcements where id = ? and published = true",
                Integer.class, id) == 0) {
            throw new NotFoundException("ANNOUNCEMENT_NOT_FOUND", "Announcement not found");
        }
        jdbc.update("""
                insert into player_announcement_reads (announcement_id, user_id, read_at)
                values (?, ?, ?) on duplicate key update read_at = values(read_at)
                """, id, userId, Timestamp.from(Instant.now()));
    }

    private PlayerAnnouncementResponse announcement(Long id, boolean isRead) {
        return jdbc.query("""
                select id, title_en, title_am, body_en, body_am, published, created_at, updated_at
                from player_announcements where id = ?
                """, rs -> {
            if (!rs.next()) throw new NotFoundException("ANNOUNCEMENT_NOT_FOUND", "Announcement not found");
            return new PlayerAnnouncementResponse(rs.getLong("id"), rs.getString("title_en"),
                    rs.getString("title_am"), rs.getString("body_en"), rs.getString("body_am"),
                    rs.getBoolean("published"), isRead, instant(rs, "created_at"), instant(rs, "updated_at"));
        }, id);
    }

    private static PlayerAppSettingsResponse mapSettings(ResultSet rs) throws SQLException {
        return new PlayerAppSettingsResponse(rs.getString("banner_title_en"), rs.getString("banner_title_am"),
                rs.getString("banner_body_en"), rs.getString("banner_body_am"), rs.getString("banner_image_url"),
                rs.getString("terms_url"), rs.getString("telegram_url"), rs.getString("tiktok_url"),
                rs.getString("support_phone_numbers"), instant(rs, "updated_at"));
    }

    private static PlayerAnnouncementResponse mapAnnouncement(ResultSet rs) throws SQLException {
        return new PlayerAnnouncementResponse(rs.getLong("id"), rs.getString("title_en"),
                rs.getString("title_am"), rs.getString("body_en"), rs.getString("body_am"),
                rs.getBoolean("published"), rs.getBoolean("is_read"), instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validateUrl(String value, String field) {
        if (value == null || value.isBlank()) return;
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("INVALID_PLAYER_APP_URL", field + " must be an absolute HTTPS or HTTP URL");
        }
        if (!uri.isAbsolute() || uri.getHost() == null ||
                !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new BadRequestException("INVALID_PLAYER_APP_URL", field + " must be an absolute HTTPS or HTTP URL");
        }
    }
}
