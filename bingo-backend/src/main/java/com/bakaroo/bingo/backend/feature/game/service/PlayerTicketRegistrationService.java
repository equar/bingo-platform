package com.bakaroo.bingo.backend.feature.game.service;

import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import com.bakaroo.bingo.backend.common.exception.NotFoundException;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.game.dto.PlayerTicketRegistrationRequest;
import com.bakaroo.bingo.backend.feature.game.dto.PlayerTicketRegistrationResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerTicketRegistrationService {
    private static final TypeReference<List<Long>> TICKET_IDS = new TypeReference<>() { };
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PlayerTicketRegistrationService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PlayerTicketRegistrationResponse get(Long sessionId, BingoUserPrincipal principal) {
        requireSession(sessionId);
        List<String> rows = jdbc.query("""
                select ticket_ids_json from player_session_ticket_selections
                where session_id = ? and user_id = ?
                """, (rs, row) -> rs.getString(1), sessionId, principal.getUser().getId());
        if (rows.isEmpty()) return new PlayerTicketRegistrationResponse(false, List.of());
        try {
            return new PlayerTicketRegistrationResponse(true, mapper.readValue(rows.getFirst(), TICKET_IDS));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read ticket board registration", exception);
        }
    }

    @Transactional
    public PlayerTicketRegistrationResponse save(Long sessionId, BingoUserPrincipal principal,
            PlayerTicketRegistrationRequest request) {
        requireSession(sessionId);
        List<Long> ids = request.ticketIds().stream().distinct().sorted().toList();
        if (ids.size() != request.ticketIds().size()) {
            throw new BadRequestException("DUPLICATE_TICKET_REGISTRATION", "Ticket IDs must be unique");
        }
        if (!ids.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
            List<Object> parameters = new java.util.ArrayList<>();
            parameters.add(sessionId);
            parameters.add(principal.getUser().getId());
            parameters.addAll(ids);
            Integer owned = jdbc.queryForObject("""
                    select count(*) from bingo_tickets
                    where session_id = ? and owner_id = ? and id in (%s)
                    """.formatted(placeholders), Integer.class, parameters.toArray());
            if (owned == null || owned != ids.size()) {
                throw new BadRequestException("INVALID_TICKET_REGISTRATION",
                        "Every registered ticket must belong to this player and session");
            }
        }
        try {
            String json = mapper.writeValueAsString(ids);
            jdbc.update("""
                    insert into player_session_ticket_selections (session_id, user_id, ticket_ids_json, updated_at)
                    values (?, ?, ?, ?)
                    on duplicate key update ticket_ids_json = values(ticket_ids_json), updated_at = values(updated_at)
                    """, sessionId, principal.getUser().getId(), json, Timestamp.from(Instant.now()));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to save ticket board registration", exception);
        }
        return new PlayerTicketRegistrationResponse(true, ids);
    }

    private void requireSession(Long sessionId) {
        if (jdbc.queryForObject("select count(*) from bingo_sessions where id = ?", Integer.class, sessionId) == 0) {
            throw new NotFoundException("SESSION_NOT_FOUND", "Game session not found");
        }
    }
}
