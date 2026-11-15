package com.bakaroo.bingo.backend.feature.playerapp.service;

import com.bakaroo.bingo.backend.common.exception.NotFoundException;
import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.playerapp.dto.PlayerAnnouncementRequest;
import com.bakaroo.bingo.backend.feature.playerapp.dto.PlayerAnnouncementResponse;
import com.bakaroo.bingo.backend.feature.playerapp.dto.PlayerBannerImageResponse;
import com.bakaroo.bingo.backend.feature.playerapp.dto.PlayerAppSettingsRequest;
import com.bakaroo.bingo.backend.feature.playerapp.dto.PlayerAppSettingsResponse;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
                select a.id, a.title_en, a.title_am, a.body_en, a.body_am, a.image_url, a.published,
                       a.show_in_carousel, a.display_order, (a.image_data is not null) as has_image,
                       false as is_read, a.created_at, a.updated_at
                from player_announcements a order by a.updated_at desc, a.id desc
                """ : """
                select a.id, a.title_en, a.title_am, a.body_en, a.body_am, a.image_url, a.published,
                       a.show_in_carousel, a.display_order, (a.image_data is not null) as has_image,
                       (r.announcement_id is not null) as is_read, a.created_at, a.updated_at
                from player_announcements a
                left join player_announcement_reads r on r.announcement_id = a.id and r.user_id = ?
                where a.published = true order by a.created_at desc, a.id desc
                """;
        return admin
                ? jdbc.query(sql, (rs, row) -> mapAnnouncement(rs))
                : jdbc.query(sql, (rs, row) -> mapAnnouncement(rs), userId);
    }

    @Transactional(readOnly = true)
    public List<PlayerAnnouncementResponse> listBannerSlides(boolean admin) {
        String sql = admin ? """
                select id, title_en, title_am, body_en, body_am, image_url, published,
                       show_in_carousel, display_order, (image_data is not null) as has_image,
                       false as is_read, created_at, updated_at
                from player_announcements where show_in_carousel = true
                order by display_order asc, id asc
                """ : """
                select id, title_en, title_am, body_en, body_am, image_url, published,
                       show_in_carousel, display_order, (image_data is not null) as has_image,
                       false as is_read, created_at, updated_at
                from player_announcements where published = true and show_in_carousel = true
                order by display_order asc, id asc
                """;
        return jdbc.query(sql, (rs, row) -> mapAnnouncement(rs));
    }

    @Transactional
    public PlayerAnnouncementResponse uploadBannerImage(Long id, byte[] bytes, String contentType,
            BingoUserPrincipal principal) {
        if (bytes == null || bytes.length == 0 || bytes.length > 5 * 1024 * 1024) {
            throw new BadRequestException("INVALID_BANNER_IMAGE", "Banner images must be between 1 byte and 5 MB");
        }
        if (!List.of("image/jpeg", "image/png", "image/webp").contains(contentType)
                || !matchesImageSignature(bytes, contentType)) {
            throw new BadRequestException("INVALID_BANNER_IMAGE_TYPE", "Use a JPEG, PNG, or WebP image");
        }
        int updated = jdbc.update("""
                update player_announcements set image_url = null, image_data = ?, image_mime_type = ?,
                    updated_by_user_id = ?, updated_at = ? where id = ? and show_in_carousel = true
                """, bytes, contentType, principal.getUser().getId(), Timestamp.from(Instant.now()), id);
        if (updated == 0) throw new NotFoundException("BANNER_SLIDE_NOT_FOUND", "Carousel slide not found");
        return announcement(id, false);
    }

    @Transactional(readOnly = true)
    public PlayerBannerImageResponse getBannerImage(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select image_data, image_mime_type from player_announcements
                where id = ? and published = true and show_in_carousel = true
                """, id);
        if (rows.isEmpty() || rows.getFirst().get("image_data") == null) {
            throw new NotFoundException("BANNER_IMAGE_NOT_FOUND", "Carousel image not found");
        }
        return new PlayerBannerImageResponse((byte[]) rows.getFirst().get("image_data"),
                rows.getFirst().get("image_mime_type").toString());
    }

    @Transactional
    public PlayerAnnouncementResponse createAnnouncement(PlayerAnnouncementRequest request,
            BingoUserPrincipal principal) {
        validateUrl(request.imageUrl(), "imageUrl");
        Instant now = Instant.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                insert into player_announcements
                    (title_en, title_am, body_en, body_am, image_url, published, show_in_carousel,
                     display_order, created_by_user_id,
                     updated_by_user_id, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new String[] { "id" });
            statement.setString(1, request.titleEn().trim());
            statement.setString(2, trim(request.titleAm()));
            statement.setString(3, request.bodyEn().trim());
            statement.setString(4, trim(request.bodyAm()));
            statement.setString(5, trim(request.imageUrl()));
            statement.setBoolean(6, request.published());
            statement.setBoolean(7, request.showInCarousel());
            statement.setInt(8, Math.max(0, request.displayOrder()));
            statement.setLong(9, principal.getUser().getId());
            statement.setLong(10, principal.getUser().getId());
            statement.setTimestamp(11, Timestamp.from(now));
            statement.setTimestamp(12, Timestamp.from(now));
            return statement;
        }, keyHolder);
        Long id = keyHolder.getKey().longValue();
        return announcement(id, false);
    }

    @Transactional
    public PlayerAnnouncementResponse updateAnnouncement(Long id, PlayerAnnouncementRequest request,
            BingoUserPrincipal principal) {
        validateUrl(request.imageUrl(), "imageUrl");
        int updated = jdbc.update("""
                update player_announcements set title_en = ?, title_am = ?, body_en = ?, body_am = ?, image_url = ?,
                    image_data = case when ? is null then image_data else null end,
                    published = ?, show_in_carousel = ?, display_order = ?, updated_by_user_id = ?,
                    updated_at = ? where id = ?
                """, request.titleEn().trim(), trim(request.titleAm()), request.bodyEn().trim(),
                trim(request.bodyAm()), trim(request.imageUrl()), trim(request.imageUrl()),
                request.published(), request.showInCarousel(),
                Math.max(0, request.displayOrder()), principal.getUser().getId(), Timestamp.from(Instant.now()), id);
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
                select id, title_en, title_am, body_en, body_am, image_url, published,
                       show_in_carousel, display_order, (image_data is not null) as has_image,
                       created_at, updated_at
                from player_announcements where id = ?
                """, rs -> {
            if (!rs.next()) throw new NotFoundException("ANNOUNCEMENT_NOT_FOUND", "Announcement not found");
            return new PlayerAnnouncementResponse(rs.getLong("id"), rs.getString("title_en"),
                    rs.getString("title_am"), rs.getString("body_en"), rs.getString("body_am"), bannerImageUrl(rs),
                    rs.getBoolean("published"), rs.getBoolean("show_in_carousel"), rs.getInt("display_order"),
                    isRead, instant(rs, "created_at"), instant(rs, "updated_at"));
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
                rs.getString("title_am"), rs.getString("body_en"), rs.getString("body_am"), bannerImageUrl(rs),
                rs.getBoolean("published"), rs.getBoolean("show_in_carousel"), rs.getInt("display_order"),
                rs.getBoolean("is_read"), instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private static String bannerImageUrl(ResultSet rs) throws SQLException {
        if (rs.getBoolean("has_image")) {
            return "/api/v1/player-app/banners/" + rs.getLong("id") + "/image";
        }
        return rs.getString("image_url");
    }

    private static boolean matchesImageSignature(byte[] bytes, String contentType) {
        if (contentType == null || bytes.length < 12) return false;
        return switch (contentType) {
            case "image/jpeg" -> (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff;
            case "image/png" -> (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e
                    && bytes[3] == 0x47 && bytes[4] == 0x0d && bytes[5] == 0x0a
                    && bytes[6] == 0x1a && bytes[7] == 0x0a;
            case "image/webp" -> bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
                    && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E'
                    && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
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
