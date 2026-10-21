package com.equar.bingo.backend.feature.admin.service;

import com.equar.bingo.backend.common.exception.NotFoundException;
import com.equar.bingo.backend.feature.admin.dto.*;
import com.equar.bingo.backend.feature.agent.entity.Agent;
import com.equar.bingo.backend.feature.agent.repository.AgentFloatRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentFloatTransactionRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentRepository;
import com.equar.bingo.backend.feature.agent.repository.FloatTopUpRequestRepository;
import com.equar.bingo.backend.feature.agent.repository.PayoutVerificationRecordRepository;
import com.equar.bingo.backend.feature.audit.repository.AuditLogRepository;
import com.equar.bingo.backend.feature.audit.dto.AuditLogDetailResponse;
import com.equar.bingo.backend.feature.config.BusinessProperties;
import com.equar.bingo.backend.feature.game.entity.GameSession;
import com.equar.bingo.backend.feature.game.entity.GameSessionState;
import com.equar.bingo.backend.feature.game.repository.DrawEventRepository;
import com.equar.bingo.backend.feature.game.repository.GameSessionRepository;
import com.equar.bingo.backend.feature.game.repository.PrizeEntitlementRepository;
import com.equar.bingo.backend.feature.game.repository.TicketRepository;
import com.equar.bingo.backend.feature.game.repository.WinClaimRepository;
import com.equar.bingo.backend.feature.support.entity.SupportTicketStatus;
import com.equar.bingo.backend.feature.support.repository.SupportTicketRepository;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.equar.bingo.backend.feature.wallet.entity.DepositClaimStatus;
import com.equar.bingo.backend.feature.wallet.repository.DepositClaimRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
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
                                userRepository.findAll().stream().filter(user -> user.hasRole(UserRole.PLAYER)).count(),
                                agentRepository.countByStatus(
                                                com.equar.bingo.backend.feature.agent.entity.AgentStatus.APPROVED),
                                supportTicketRepository.findAll().stream()
                                                .filter(ticket -> ticket.getStatus() == SupportTicketStatus.OPEN
                                                                || ticket.getStatus() == SupportTicketStatus.IN_PROGRESS
                                                                || ticket.getStatus() == SupportTicketStatus.WAITING_FOR_CUSTOMER)
                                                .count(),
                                depositClaimRepository.findByStatus(DepositClaimStatus.PENDING, Pageable.unpaged())
                                                .getTotalElements(),
                                floatTopUpRequestRepository.findByStatusOrderByCreatedAtDesc(
                                                com.equar.bingo.backend.feature.agent.entity.FloatTopUpRequestStatus.PENDING)
                                                .size(),
                                winClaimRepository.findAll().stream().filter(claim -> claim
                                                .getStatus() == com.equar.bingo.backend.feature.game.entity.WinClaimStatus.PENDING_REVIEW)
                                                .count(),
                                gameSessionRepository
                                                .findByStateInAndStartedAtIsNotNullAndEndedAtIsNullOrderByScheduledStartAtAsc(
                                                                List.of(GameSessionState.STARTED,
                                                                                GameSessionState.DRAWING))
                                                .size(),
                                playerBalance, agentAvailable, agentClearing);
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
        public List<AdminSessionSummaryResponse> liveSessions() {
                return gameSessionRepository
                                .findByStateInAndStartedAtIsNotNullAndEndedAtIsNullOrderByScheduledStartAtAsc(
                                                List.of(GameSessionState.STARTED, GameSessionState.DRAWING))
                                .stream()
                                .map(this::toSessionSummary)
                                .toList();
        }

        public Page<AdminAgentSummaryResponse> agents(Pageable pageable) {
                return agentRepository.findAll(pageable).map(this::toAgentSummary);
        }

        public AdminAgentSummaryResponse agentDetail(Long agentId) {
                return toAgentSummary(agentRepository.findById(agentId)
                                .orElseThrow(() -> new NotFoundException("AGENT_NOT_FOUND", "Agent not found")));
        }

        @Transactional(readOnly = true)
        public Page<AdminAuditLogSummaryResponse> auditLogs(Pageable pageable) {
                return auditLogRepository.findAll(pageable).map(entry -> {
                        User actor = entry.getActor();
                        return new AdminAuditLogSummaryResponse(entry.getId(), actor == null ? null : actor.getId(),
                                        entry.getAction(), entry.getTargetType(), entry.getTargetId(), entry.getReason(),
                                        entry.getOperationId(), entry.getCreatedAt());
                });
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
                                winClaimRepository.findBySessionIdOrderByCreatedAtDesc(session.getId()).size(),
                                supportTicketRepository.findAll().stream()
                                                .filter(ticket -> session.getId().equals(ticket.getLinkedEntityId()))
                                                .count());
        }

        private AdminAgentSummaryResponse toAgentSummary(Agent agent) {
                return new AdminAgentSummaryResponse(agent.getId(), agent.getUser().getId(),
                                agent.getUser().getDisplayName(),
                                agent.getUser().getNormalizedPhone(), agent.getStatus(), agent.getCity(),
                                agent.getTerritory(),
                                agent.getCreatedAt(), agent.getUpdatedAt(),
                                ticketRepository.findAll().stream()
                                                .filter(ticket -> ticket.getOwner().getId()
                                                                .equals(agent.getUser().getId()))
                                                .count(),
                                agentFloatTransactionRepository
                                                .findByAgentFloatAgentIdOrderByCreatedAtDesc(agent.getId()).size(),
                                floatTopUpRequestRepository.findByAgentIdOrderByCreatedAtDesc(agent.getId()).size(),
                                payoutVerificationRecordRepository.findByAgentIdOrderByCreatedAtDesc(agent.getId())
                                                .size());
        }
}
