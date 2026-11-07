package com.bakaroo.bingo.backend.feature.admin.service;

import com.bakaroo.bingo.backend.common.exception.NotFoundException;
import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import com.bakaroo.bingo.backend.feature.admin.dto.*;
import com.bakaroo.bingo.backend.feature.agent.entity.Agent;
import com.bakaroo.bingo.backend.feature.agent.entity.AgentStatus;
import com.bakaroo.bingo.backend.feature.agent.repository.AgentFloatRepository;
import com.bakaroo.bingo.backend.feature.agent.repository.AgentFloatTransactionRepository;
import com.bakaroo.bingo.backend.feature.agent.repository.AgentRepository;
import com.bakaroo.bingo.backend.feature.agent.repository.FloatTopUpRequestRepository;
import com.bakaroo.bingo.backend.feature.agent.repository.PayoutVerificationRecordRepository;
import com.bakaroo.bingo.backend.feature.audit.repository.AuditLogRepository;
import com.bakaroo.bingo.backend.feature.audit.dto.AuditLogDetailResponse;
import com.bakaroo.bingo.backend.feature.config.BusinessProperties;
import com.bakaroo.bingo.backend.feature.game.entity.GameSession;
import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;
import com.bakaroo.bingo.backend.feature.game.repository.DrawEventRepository;
import com.bakaroo.bingo.backend.feature.game.repository.GameSessionRepository;
import com.bakaroo.bingo.backend.feature.game.repository.PrizeEntitlementRepository;
import com.bakaroo.bingo.backend.feature.game.repository.TicketRepository;
import com.bakaroo.bingo.backend.feature.game.repository.WinClaimRepository;
import com.bakaroo.bingo.backend.feature.support.entity.SupportTicketStatus;
import com.bakaroo.bingo.backend.feature.support.entity.SupportLinkedEntityType;
import com.bakaroo.bingo.backend.feature.support.repository.SupportTicketRepository;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.bakaroo.bingo.backend.feature.wallet.entity.DepositClaimStatus;
import com.bakaroo.bingo.backend.feature.wallet.repository.DepositClaimRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminReadService {

        private final UserRepository userRepository;
        private final WalletRepository walletRepository;
        private final WalletTransactionRepository walletTransactionRepository;
        private final DepositClaimRepository depositClaimRepository;
        private final TicketRepository ticketRepository;
        private final DrawEventRepository drawEventRepository;
        private final GameSessionRepository gameSessionRepository;
        private final AgentRepository agentRepository;
        private final AgentFloatRepository agentFloatRepository;
        private final AgentFloatTransactionRepository agentFloatTransactionRepository;
        private final FloatTopUpRequestRepository floatTopUpRequestRepository;
        private final PayoutVerificationRecordRepository payoutVerificationRecordRepository;
        private final WinClaimRepository winClaimRepository;
        private final PrizeEntitlementRepository prizeEntitlementRepository;
        private final SupportTicketRepository supportTicketRepository;
        private final AuditLogRepository auditLogRepository;
        private final BusinessProperties businessProperties;

        public AdminReadService(UserRepository userRepository,
                        WalletRepository walletRepository,
                        WalletTransactionRepository walletTransactionRepository,
                        DepositClaimRepository depositClaimRepository,
                        TicketRepository ticketRepository,
                        DrawEventRepository drawEventRepository,
                        GameSessionRepository gameSessionRepository,
                        AgentRepository agentRepository,
                        AgentFloatRepository agentFloatRepository,
                        AgentFloatTransactionRepository agentFloatTransactionRepository,
                        FloatTopUpRequestRepository floatTopUpRequestRepository,
                        PayoutVerificationRecordRepository payoutVerificationRecordRepository,
                        WinClaimRepository winClaimRepository,
                        PrizeEntitlementRepository prizeEntitlementRepository,
                        SupportTicketRepository supportTicketRepository,
                        AuditLogRepository auditLogRepository,
                        BusinessProperties businessProperties) {
                this.userRepository = userRepository;
                this.walletRepository = walletRepository;
                this.walletTransactionRepository = walletTransactionRepository;
                this.depositClaimRepository = depositClaimRepository;
                this.ticketRepository = ticketRepository;
                this.drawEventRepository = drawEventRepository;
                this.gameSessionRepository = gameSessionRepository;
                this.agentRepository = agentRepository;
                this.agentFloatRepository = agentFloatRepository;
                this.agentFloatTransactionRepository = agentFloatTransactionRepository;
                this.floatTopUpRequestRepository = floatTopUpRequestRepository;
                this.payoutVerificationRecordRepository = payoutVerificationRecordRepository;
                this.winClaimRepository = winClaimRepository;
                this.prizeEntitlementRepository = prizeEntitlementRepository;
                this.supportTicketRepository = supportTicketRepository;
                this.auditLogRepository = auditLogRepository;
                this.businessProperties = businessProperties;
        }

        public AdminDashboardMetricsResponse dashboardMetrics() {
                BigDecimal playerBalance = walletRepository.findAll().stream()
                                .map(wallet -> wallet.getTotalBalance())
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal agentAvailable = agentFloatRepository.findAll().stream()
                                .map(floatRow -> floatRow.getAvailableBalance())
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal agentClearing = agentFloatRepository.findAll().stream()
                                .map(floatRow -> floatRow.getClearingBalance())
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                return new AdminDashboardMetricsResponse(Instant.now(), businessProperties.zoneId().getId(),
                                userRepository.findAll().stream()
                                                .filter(user -> user.hasRole(UserRole.PLAYER) && user.isActive()).count(),
                                agentRepository.countByStatus(
                                                com.bakaroo.bingo.backend.feature.agent.entity.AgentStatus.APPROVED),
                                supportTicketRepository.findAll().stream()
                                                .filter(ticket -> ticket.getStatus() == SupportTicketStatus.OPEN
                                                                || ticket.getStatus() == SupportTicketStatus.IN_PROGRESS
                                                                || ticket.getStatus() == SupportTicketStatus.WAITING_FOR_CUSTOMER)
                                                .count(),
                                depositClaimRepository.findByStatus(DepositClaimStatus.PENDING, Pageable.unpaged())
                                                .getTotalElements(),
                                floatTopUpRequestRepository.findByStatusOrderByCreatedAtDesc(
                                                com.bakaroo.bingo.backend.feature.agent.entity.FloatTopUpRequestStatus.PENDING)
                                                .size(),
                                winClaimRepository.findAll().stream().filter(claim -> claim
                                                .getStatus() == com.bakaroo.bingo.backend.feature.game.entity.WinClaimStatus.PENDING_REVIEW)
                                                .count(),
                                gameSessionRepository
                                                .findByStateInAndStartedAtIsNotNullAndEndedAtIsNullOrderByScheduledStartAtAsc(
                                                                List.of(GameSessionState.STARTED,
                                                                                GameSessionState.DRAWING))
                                                .size(),
                                playerBalance, agentAvailable, agentClearing);
        }

        public AdminDashboardTrendsResponse dashboardTrends(int days) {
                ZoneId zone = businessProperties.zoneId();
                LocalDate end = LocalDate.now(zone);
                LocalDate start = end.minusDays(days - 1L);
                Map<LocalDate, Long> registrations = new TreeMap<>();
                Map<LocalDate, Long> tickets = new TreeMap<>();
                for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                        registrations.put(date, 0L);
                        tickets.put(date, 0L);
                }
                Instant startAt = start.atStartOfDay(zone).toInstant();
                Instant endExclusive = end.plusDays(1).atStartOfDay(zone).toInstant();
                userRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(startAt, endExclusive).stream()
                                .filter(user -> user.hasRole(UserRole.PLAYER))
                                .map(user -> user.getCreatedAt().atZone(zone).toLocalDate())
                                .filter(date -> !date.isBefore(start) && !date.isAfter(end))
                                .forEach(date -> registrations.computeIfPresent(date, (key, count) -> count + 1));
                supportTicketRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                                startAt, endExclusive, Pageable.unpaged()).stream()
                                .map(ticket -> ticket.getCreatedAt().atZone(zone).toLocalDate())
                                .filter(date -> !date.isBefore(start) && !date.isAfter(end))
                                .forEach(date -> tickets.computeIfPresent(date, (key, count) -> count + 1));
                return new AdminDashboardTrendsResponse(zone.getId(),
                                registrations.entrySet().stream()
                                                .map(entry -> new DailyTrendPoint(entry.getKey(), entry.getValue()))
                                                .toList(),
                                tickets.entrySet().stream()
                                                .map(entry -> new DailyTrendPoint(entry.getKey(), entry.getValue()))
                                                .toList());
        }

        public Page<AdminPlayerSummaryResponse> searchPlayers(String query, AccountStatus status, Pageable pageable) {
                String search = query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
                return userRepository.findAdminUsers(List.of(UserRole.PLAYER), search, status, pageable)
                                .map(user -> new AdminPlayerSummaryResponse(user.getId(),
                                user.getDisplayName(), user.getNormalizedPhone(), user.getRoles(), user.getStatus(),
                                user.getCreatedAt(),
                                user.getUpdatedAt(), walletTransactionRepository.countByOwnerId(user.getId()),
                                depositClaimRepository.countByPlayerId(user.getId()),
                                supportTicketRepository.countByRequesterId(user.getId())));
        }

        public AdminPlayerDetailResponse playerDetail(Long playerId) {
                User user = userRepository.findById(playerId)
                                .orElseThrow(() -> new NotFoundException("PLAYER_NOT_FOUND",
                                                "Player account not found"));
                var wallet = walletRepository.findByOwnerId(user.getId()).orElse(null);
                return new AdminPlayerDetailResponse(user.getId(), user.getDisplayName(), user.getNormalizedPhone(),
                                user.getEmail(), user.getRoles(), user.getStatus(), user.getCreatedAt(),
                                user.getUpdatedAt(), wallet == null ? null : wallet.getCurrency(),
                                wallet == null ? null : wallet.getAvailableBalance(),
                                wallet == null ? null : wallet.getPromotionalBalance(),
                                walletTransactionRepository.countByOwnerId(user.getId()),
                                depositClaimRepository.countByPlayerId(user.getId()),
                                supportTicketRepository.countByRequesterId(user.getId()),
                                ticketRepository.countByOwnerId(user.getId()),
                                winClaimRepository.countByClaimantId(user.getId()));
        }

        @Transactional(readOnly = true)
        public Page<AdminSessionSummaryResponse> sessions(String query, GameSessionState state, Long roomId,
                        Boolean upcoming, Pageable pageable) {
                String normalizedQuery = query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
                Long sessionId = null;
                if (normalizedQuery != null) {
                        try {
                                sessionId = Long.valueOf(normalizedQuery);
                        } catch (NumberFormatException ignored) {
                                sessionId = null;
                        }
                }
                return gameSessionRepository.searchAdminSessions(normalizedQuery, sessionId, state, roomId, upcoming,
                                Instant.now(), pageable).map(this::toSessionSummary);
        }

        @Transactional(readOnly = true)
        public Page<AdminSessionSummaryResponse> sessionsReport(Instant from, Instant to, Pageable pageable) {
                return gameSessionRepository
                                .findByScheduledStartAtGreaterThanEqualAndScheduledStartAtLessThanOrderByScheduledStartAtDescIdDesc(
                                                from, to, pageable)
                                .map(this::toSessionSummary);
        }

        @Transactional(readOnly = true)
        public List<AdminSessionSummaryResponse> liveSessions() {
                return gameSessionRepository
                                .findByStateInAndStartedAtIsNotNullAndEndedAtIsNullOrderByScheduledStartAtAsc(
                                                List.of(GameSessionState.STARTED, GameSessionState.DRAWING))
                                .stream()
                                .map(this::toSessionSummary)
                                .toList();
        }

        @Transactional(readOnly = true)
        public Page<AdminAgentSummaryResponse> agents(String query, AgentStatus status, Pageable pageable) {
                if (pageable.getPageNumber() < 0 || pageable.getPageSize() < 1 || pageable.getPageSize() > 100) {
                        throw new BadRequestException("INVALID_PAGINATION",
                                        "Agent page must be non-negative and size must be between 1 and 100");
                }
                String search = query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
                java.util.Set<String> sortable = java.util.Set.of(
                                "id", "createdAt", "updatedAt", "status", "city", "territory");
                for (org.springframework.data.domain.Sort.Order order : pageable.getSort()) {
                        if (!sortable.contains(order.getProperty())) {
                                throw new BadRequestException("INVALID_SORT_FIELD", "Unsupported agent sort field");
                        }
                }
                org.springframework.data.domain.Sort stableSort = pageable.getSort().isSorted()
                                ? pageable.getSort()
                                : org.springframework.data.domain.Sort.by(
                                                org.springframework.data.domain.Sort.Order.desc("createdAt"));
                if (stableSort.getOrderFor("id") == null) {
                        stableSort = stableSort.and(org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Order.desc("id")));
                }
                Pageable stablePageable = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(),
                                pageable.getPageSize(), stableSort);
                Page<Agent> agentPage = agentRepository.searchAdminAgents(search, status, stablePageable);
                if (agentPage.isEmpty()) {
                        return new org.springframework.data.domain.PageImpl<>(List.of(), stablePageable,
                                        agentPage.getTotalElements());
                }
                List<Long> agentIds = agentPage.getContent().stream().map(Agent::getId).toList();
                List<Long> userIds = agentPage.getContent().stream().map(agent -> agent.getUser().getId()).toList();
                Map<Long, Long> ticketCounts = countMap(ticketRepository.countByOwnerIds(userIds));
                Map<Long, Long> floatCounts = countMap(agentFloatTransactionRepository.countByAgentIds(agentIds));
                Map<Long, Long> topUpCounts = countMap(floatTopUpRequestRepository.countByAgentIds(agentIds));
                Map<Long, Long> payoutCounts = countMap(payoutVerificationRecordRepository.countByAgentIds(agentIds));
                return agentPage.map(agent -> new AdminAgentSummaryResponse(agent.getId(), agent.getUser().getId(),
                                agent.getUser().getDisplayName(), agent.getUser().getNormalizedPhone(),
                                agent.getStatus(), agent.getCity(), agent.getTerritory(), agent.getCreatedAt(),
                                agent.getUpdatedAt(), ticketCounts.getOrDefault(agent.getUser().getId(), 0L),
                                floatCounts.getOrDefault(agent.getId(), 0L),
                                topUpCounts.getOrDefault(agent.getId(), 0L),
                                payoutCounts.getOrDefault(agent.getId(), 0L)));
        }

        @Transactional(readOnly = true)
        public AdminAgentDetailResponse agentDetail(Long agentId) {
                return toAgentDetail(agentRepository.findById(agentId)
                                .orElseThrow(() -> new NotFoundException("AGENT_NOT_FOUND", "Agent not found")));
        }

        @Transactional(readOnly = true)
        public Page<AdminAuditLogSummaryResponse> auditLogs(Pageable pageable) {
                return auditLogRepository.findAll(pageable).map(this::toAuditLogSummary);
        }

        @Transactional(readOnly = true)
        public Page<AdminAuditLogSummaryResponse> searchAuditLogs(
                                String query,
                                Long actorUserId,
                                Long auditLogId,
                                String action,
                                String targetType,
                                Long targetId,
                                String operationId,
                                Instant from,
                                Instant to,
                                Pageable pageable) {
                if (from != null && to != null && !from.isBefore(to)) {
                                throw new BadRequestException("INVALID_AUDIT_DATE_RANGE",
                                                "Audit search start must be before its end");
                }

                return auditLogRepository.searchAuditLogs(
                                        normalizeFilter(query),
                                        actorUserId,
                                        auditLogId,
                                        normalizeFilter(action),
                                        normalizeFilter(targetType),
                                        targetId,
                                        normalizeFilter(operationId),
                                        from,
                                        to,
                                        pageable)
                                        .map(this::toAuditLogSummary);
        }

        @Transactional(readOnly = true)
        public Page<AdminAuditLogSummaryResponse> auditActivityReport(Instant from, Instant to, Pageable pageable) {
                return auditLogRepository
                                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                                                        from, to, pageable)
                                        .map(this::toAuditLogSummary);
        }

        @Transactional(readOnly = true)
        public AuditLogDetailResponse auditLogDetail(Long auditLogId) {
                var entry = auditLogRepository.findById(auditLogId)
                                .orElseThrow(() -> new NotFoundException("AUDIT_LOG_NOT_FOUND", "Audit log not found"));
                User actor = entry.getActor();
                return new AuditLogDetailResponse(entry.getId(), actor == null ? null : actor.getId(),
                                actor == null ? null : actor.getDisplayName(), entry.getAction(), entry.getTargetType(),
                                entry.getTargetId(), entry.getReason(), entry.getOperationId(), entry.getMetadataJson(),
                                entry.getCreatedAt());
        }

        private AdminAuditLogSummaryResponse toAuditLogSummary(
                        com.bakaroo.bingo.backend.feature.audit.entity.AuditLogEntry entry) {
                User actor = entry.getActor();
                return new AdminAuditLogSummaryResponse(
                                entry.getId(),
                                actor == null ? null : actor.getId(),
                                actor == null ? null : actor.getDisplayName(),
                                entry.getAction(),
                                entry.getTargetType(),
                                entry.getTargetId(),
                                entry.getReason(),
                                entry.getOperationId(),
                                entry.getCreatedAt());
        }

        private String normalizeFilter(String value) {
                if (value == null || value.isBlank()) {
                        return null;
                }
                return value.trim().toLowerCase(Locale.ROOT);
        }

        public Page<AdminReviewQueueItemResponse> reviewQueue(Pageable pageable) {
                return supportTicketRepository.findAll(pageable)
                                .map(ticket -> new AdminReviewQueueItemResponse(ticket.getId(),
                                                "SUPPORT", ticket.getSubject(), ticket.getStatus().name(),
                                                ticket.getPriority().name(),
                                                ticket.getRequester().getId(),
                                                ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getId(),
                                                ticket.getLinkedEntityId(),
                                                ticket.getLinkedEntityType() == null ? null
                                                                : ticket.getLinkedEntityType().name(),
                                                ticket.getCreatedAt(), ticket.getUpdatedAt()));
        }

        private AdminSessionSummaryResponse toSessionSummary(GameSession session) {
                return new AdminSessionSummaryResponse(session.getId(), session.getRoom().getId(),
                                session.getRoom().getName(),
                                session.getSessionName(), session.getState(), session.getScheduledStartAt(),
                                session.getStartedAt(),
                                session.getEndedAt(), session.getTicketPriceSnapshot(), session.getCurrencySnapshot(),
                                ticketRepository.countBySessionId(session.getId()),
                                drawEventRepository.countBySessionId(session.getId()),
                                winClaimRepository.countBySessionId(session.getId()),
                                supportTicketRepository.countByLinkedEntityTypeAndLinkedEntityId(
                                                SupportLinkedEntityType.GAME_SESSION, session.getId()));
        }

        private Map<Long, Long> countMap(List<Object[]> rows) {
                Map<Long, Long> counts = new HashMap<>();
                for (Object[] row : rows) {
                        counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
                }
                return counts;
        }

        private AdminAgentDetailResponse toAgentDetail(Agent agent) {
                return new AdminAgentDetailResponse(agent.getId(), agent.getUser().getId(),
                                agent.getUser().getDisplayName(),
                                agent.getUser().getNormalizedPhone(), agent.getStatus(), agent.getCity(),
                                agent.getTerritory(), agent.getIdDocumentType(), agent.getIdDocumentNumber(),
                                agent.getIdDocumentFrontReference(), agent.getIdDocumentBackReference(),
                                agent.getCreatedAt(), agent.getUpdatedAt(),
                                ticketRepository.countByOwnerId(agent.getUser().getId()),
                                agentFloatTransactionRepository.countByAgentFloatAgentId(agent.getId()),
                                floatTopUpRequestRepository.countByAgentId(agent.getId()),
                                payoutVerificationRecordRepository.countByAgentId(agent.getId()));
        }
}
