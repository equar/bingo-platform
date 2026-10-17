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
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.equar.bingo.backend.feature.wallet.entity.DepositClaimStatus;
import com.equar.bingo.backend.feature.wallet.repository.DepositClaimRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class AdminReadService {

        private final UserRepository userRepository;
        private final WalletRepository walletRepository;
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

        public Page<AdminPlayerSummaryResponse> searchPlayers(String query, Pageable pageable) {
                return userRepository.findAll(pageable).map(user -> new AdminPlayerSummaryResponse(user.getId(),
                                user.getDisplayName(), user.getNormalizedPhone(), user.getRoles(), user.getStatus(),
                                user.getCreatedAt(),
                                user.getUpdatedAt(), walletRepository.countByOwnerId(user.getId()),
                                depositClaimRepository.findByPlayerId(user.getId(), Pageable.unpaged())
                                                .getTotalElements(),
                                supportTicketRepository
                                                .findByRequesterIdOrderByCreatedAtDesc(user.getId(), Pageable.unpaged())
                                                .getTotalElements()));
        }

        public AdminPlayerDetailResponse playerDetail(Long playerId) {
                User user = userRepository.findById(playerId)
                                .orElseThrow(() -> new NotFoundException("PLAYER_NOT_FOUND",
                                                "Player account not found"));
                return new AdminPlayerDetailResponse(user.getId(), user.getDisplayName(), user.getNormalizedPhone(),
                                user.getRoles(),
                                user.getStatus(), user.getCreatedAt(), user.getUpdatedAt(),
                                walletRepository.countByOwnerId(user.getId()),
                                depositClaimRepository.findByPlayerId(user.getId(), Pageable.unpaged())
                                                .getTotalElements(),
                                supportTicketRepository
                                                .findByRequesterIdOrderByCreatedAtDesc(user.getId(), Pageable.unpaged())
                                                .getTotalElements(),
                                ticketRepository.countByOwnerId(user.getId()),
                                winClaimRepository.findAll().stream()
                                                .filter(claim -> claim.getClaimant().getId().equals(user.getId()))
                                                .count());
        }

        public Page<AdminSessionSummaryResponse> sessions(Pageable pageable) {
                return gameSessionRepository.findAll(pageable).map(this::toSessionSummary);
        }

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

        public Page<AdminAuditLogSummaryResponse> auditLogs(Pageable pageable) {
                return auditLogRepository.findAll(pageable).map(entry -> new AdminAuditLogSummaryResponse(entry.getId(),
                                entry.getActor() == null ? null : entry.getActor().getId(), entry.getAction(),
                                entry.getTargetType(),
                                entry.getTargetId(), entry.getReason(), entry.getOperationId(), entry.getCreatedAt()));
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