import { http } from './http';
import type { PageResponse } from './types';

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: AdminUserProfile;
}

export interface AdminUserProfile {
  id: number;
  displayName: string;
  phoneNumber: string;
  roles: string[];
  status: string;
  createdAt: string;
  updatedAt: string;
  mustChangePassword?: boolean;
}

export interface MeResponse {
  user: AdminUserProfile;
}

export interface AdminDashboardMetricsResponse {
  generatedAt: string;
  businessTimezone: string;
  activePlayers: number;
  activeAgents: number;
  openSupportTickets: number;
  openDepositReviews: number;
  openAgentFloatReviews: number;
  openWinClaims: number;
  activeSessions: number;
  playerWalletBalanceTotal: string;
  agentFloatAvailableTotal: string;
  agentFloatClearingTotal: string;
}

export interface AdminPlayerSummaryResponse {
  id: number;
  displayName: string;
  normalizedPhone: string;
  roles: string[];
  status: string;
  createdAt: string;
  updatedAt: string;
  walletTransactionCount: number;
  depositClaimCount: number;
  supportTicketCount: number;
}

export interface AdminPlayerDetailResponse {
  id: number;
  displayName: string;
  normalizedPhone: string;
  email: string | null;
  roles: string[];
  status: string;
  createdAt: string;
  updatedAt: string;
  walletCurrency: string | null;
  walletAvailableBalance: string | null;
  walletPromotionalBalance: string | null;
  walletTransactionCount: number;
  depositClaimCount: number;
  supportTicketCount: number;
  ticketCount: number;
  winClaimCount: number;
}

export interface AdminSessionSummaryResponse {
  id: number;
  roomId: number;
  roomName: string;
  sessionName: string;
  state: string;
  scheduledStartAt: string;
  startedAt: string | null;
  endedAt: string | null;
  ticketPrice: string;
  currency: string;
  ticketCount: number;
  drawCount: number;
  winClaimCount: number;
  supportTicketCount: number;
}

export interface AdminReviewQueueItemResponse {
  id: number;
  queueType: string;
  title: string;
  status: string;
  priority: string;
  requesterUserId: number | null;
  assignedToUserId: number | null;
  linkedEntityId: number | null;
  linkedEntityType: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface UserSummaryResponse {
  id: number;
  displayName: string;
  email: string;
  phoneNumber: string;
  roles: string[];
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface AdminUserCreateRequest {
  displayName: string;
  email: string;
  phoneNumber: string;
  status: string;
  roles: string[];
}

export interface AdminUserUpdateRequest {
  displayName: string;
  email: string;
  phoneNumber: string;
  password?: string;
  status: string;
  roles: string[];
}

export interface AdminAgentSummaryResponse {
  agentId: number;
  userId: number;
  displayName: string;
  normalizedPhone: string;
  status: string;
  city: string | null;
  territory: string | null;
  createdAt: string;
  updatedAt: string;
  ticketSalesCount: number;
  floatTransactionCount: number;
  topUpRequestCount: number;
  payoutVerificationCount: number;
}

export type AdminAgentDetailResponse = AdminAgentSummaryResponse;

export interface AgentProfileResponse {
  agentId: number | null;
  userId: number | null;
  phoneNumber: string;
  displayName: string;
  status: string;
  city: string | null;
  territory: string | null;
  idDocumentType: string | null;
  idDocumentNumber: string | null;
  idDocumentFrontReference: string | null;
  idDocumentBackReference: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AgentDashboardResponse {
  agentId: number;
  status: string;
  availableFloat: string;
  clearingFloat: string;
  ticketsSold: number;
  commissionAccruals: number;
  topUpRequests: number;
  generatedAt: string;
}

export interface AgentFloatResponse {
  agentId: number;
  floatId: number;
  currency: string;
  availableBalance: string;
  clearingBalance: string;
  updatedAt: string;
}

export interface AgentFloatTransactionResponse {
  id: number;
  agentFloatId: number;
  transactionType: string;
  amount: string;
  availableBefore: string;
  availableAfter: string;
  clearingBefore: string;
  clearingAfter: string;
  relatedEntityType: string | null;
  relatedEntityId: number | null;
  operationId: string;
  note: string | null;
  createdAt: string;
}

export interface CommissionAccrualResponse {
  id: number;
  agentId: number;
  ticketId: number;
  sessionId: number;
  commissionRuleVersion: number;
  saleAmount: string;
  commissionAmount: string;
  status: string;
  operationId: string;
  createdAt: string;
  settledAt: string | null;
  settlementReference: string | null;
  settlementNote: string | null;
}

export interface FloatTopUpRequestResponse {
  id: number;
  agentId: number;
  requestedByUserId: number;
  amount: string;
  currency: string;
  status: string;
  operationId: string;
  reviewNote: string | null;
  reviewedByUserId: number | null;
  reviewedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CommissionRuleRequest {
  commissionRateBps: number;
  effectiveFrom: string;
  active: boolean;
}

export interface ReviewFloatTopUpRequest {
  approve: boolean;
  reviewNote: string;
}

export interface SupportMessageResponse {
  id: number;
  authorUserId: number;
  body: string;
  createdAt: string;
}

export interface InternalNoteResponse {
  id: number;
  authorUserId: number;
  body: string;
  createdAt: string;
}

export interface SupportTicketSummaryResponse {
  id: number;
  ticketNumber: string;
  requesterUserId: number;
  subject: string;
  category: string;
  priority: string;
  status: string;
  assignedToUserId: number | null;
  linkedEntityType: string | null;
  linkedEntityId: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface SupportTicketResponse {
  id: number;
  ticketNumber: string;
  requesterUserId: number;
  requesterDisplayName: string;
  subject: string;
  description: string;
  category: string;
  priority: string;
  status: string;
  linkedEntityType: string | null;
  linkedEntityId: number | null;
  linkedEntityReference: string | null;
  assignedToUserId: number | null;
  resolutionSummary: string | null;
  createdAt: string;
  updatedAt: string;
  messages: SupportMessageResponse[];
  internalNotes: InternalNoteResponse[];
}

export interface SupportTicketAssignmentRequest {
  assignedToUserId: number;
}

export interface SupportTicketStatusUpdateRequest {
  status: string;
  resolutionSummary?: string | null;
}

export interface SupportTicketMessageRequest {
  body: string;
}

export interface SupportTicketCreateRequest {
  subject: string;
  description: string;
  category: string;
  priority: string;
  linkedEntityType?: string | null;
  linkedEntityId?: number | null;
  linkedEntityReference?: string | null;
}

export interface AdminAuditLogSummaryResponse {
  id: number;
  actorUserId: number | null;
  action: string;
  targetType: string;
  targetId: number | null;
  reason: string | null;
  operationId: string | null;
  createdAt: string;
}

export interface AdminAuditLogDetailResponse extends AdminAuditLogSummaryResponse {
  actorDisplayName: string | null;
  metadataJson: string | null;
}

export interface BingoPatternConfig {
  prizeType: string;
  amount: number;
  settlementPath: string;
  maxWinners?: number | null;
}

export interface BingoRoomResponse {
  id: number;
  name: string;
  description: string | null;
  ticketPrice: string;
  currency: string;
  timezone: string;
  scheduleRule: string;
  sessionCapacity: number;
  maxTicketsPerPlayer: number;
  ticketSalesCloseOnSessionStart: boolean;
  allowMultipleWinnersPerPrize: boolean;
  active: boolean;
  configuredPatterns: string[];
  configuredPrizes: BingoPatternConfig[];
  createdAt: string;
  updatedAt: string;
}

export interface GameSessionResponse {
  id: number;
  roomId: number;
  roomName: string;
  sessionName: string;
  state: string;
  scheduledStartAt: string;
  startedAt: string | null;
  endedAt: string | null;
  ticketPrice: string;
  currency: string;
  sessionCapacity: number;
  maxTicketsPerPlayer: number;
  ticketSalesCloseOnSessionStart: boolean;
  allowMultipleWinnersPerPrize: boolean;
  configuredPatterns: string[];
  configuredPrizes: BingoPatternConfig[];
  ticketCount: number;
  drawCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface DrawEventResponse {
  id: number;
  sessionId: number;
  sequenceNo: number;
  numberDrawn: number;
  operationId: string;
  drawnAt: string;
}

export interface TicketResponse {
  id: number;
  sessionId: number;
  ownerId: number;
  publicTicketCode: string;
  operationId: string;
  grid: number[][];
  purchasedAt: string;
}

export interface WinClaimResponse {
  id: number;
  ticketId: number;
  sessionId: number;
  claimantId: number;
  claimedPattern: string;
  status: string;
  serverValidationPassed: boolean;
  serverValidationCode: string | null;
  serverValidationMessage: string | null;
  drawSequenceSnapshot: number;
  calledNumbersSnapshot: number[];
  evidenceJson: string | null;
  operationId: string;
  reviewNote: string | null;
  reviewedBy: number | null;
  reviewedAt: string | null;
  entitlementId: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface WinClaimReviewRequest {
  reviewNote: string;
  idempotencyKey?: string;
}

export interface PrizeEntitlementResponse {
  id: number;
  winClaimId: number;
  sessionId: number;
  ticketId: number;
  claimantId: number;
  prizeType: string;
  settlementPath: string;
  status: string;
  prizeAmount: string;
  currency: string;
  operationId: string;
  createdAt: string;
  settledAt: string | null;
  settledBy: number | null;
  settlementReference: string | null;
  settlementNote: string | null;
}

export interface WalletBalanceResponse {
  walletId: number;
  currency: string;
  availableBalance: string;
  promotionalBalance: string;
  totalBalance: string;
  updatedAt: string;
}

export interface WalletTransactionResponse {
  id: number;
  operationId: string;
  transactionType: string;
  amount: string;
  balanceBefore: string;
  balanceAfter: string;
  transferableBalanceBefore: string | null;
  transferableBalanceAfter: string | null;
  promotionalBalanceBefore: string | null;
  promotionalBalanceAfter: string | null;
  currency: string;
  referenceType: string;
  referenceId: number | null;
  note: string | null;
  metadataJson: string | null;
  createdAt: string;
}

export interface WalletRecipientLookupResponse {
  userId: number;
  displayName: string;
  normalizedPhone: string;
}

export interface WalletTransferRequest {
  recipientPhoneNumber: string;
  amount: number;
  currency: string;
  note?: string | null;
}

export interface WalletTransferResponse {
  id: number;
  operationId: string;
  senderId: number;
  senderPhoneNumber: string;
  recipientId: number;
  recipientPhoneNumber: string;
  amount: string;
  currency: string;
  note: string | null;
  status: string;
  senderBalanceBefore: string;
  senderBalanceAfter: string;
  recipientBalanceBefore: string;
  recipientBalanceAfter: string;
  createdAt: string;
  completedAt: string | null;
  failureReason: string | null;
}

export interface SignupRewardSettingsResponse {
  id: number;
  enabled: boolean;
  rewardAmount: string;
  currency: string;
  effectiveFrom: string;
  effectiveUntil: string | null;
  changedByUserId: number | null;
  reason: string | null;
  createdAt: string;
  updatedAt: string;
  activeNow: boolean;
}

export interface SignupRewardSettingsRequest {
  enabled: boolean;
  rewardAmount: number;
  currency: string;
  effectiveFrom: string;
  effectiveUntil?: string | null;
  reason?: string | null;
}

export interface SignupRewardGrantResponse {
  id: number;
  playerId: number;
  playerDisplayName: string;
  playerPhoneNumber: string;
  policyId: number;
  operationId: string;
  rewardAmount: string;
  currency: string;
  awardedAt: string;
}

export interface WalletTransferSettingsResponse {
  id: number;
  enabled: boolean;
  maxSingleTransferAmount: string;
  dailyOutgoingLimitAmount: string;
  currency: string;
  effectiveFrom: string;
  effectiveUntil: string | null;
  changedByUserId: number | null;
  reason: string | null;
  createdAt: string;
  updatedAt: string;
  activeNow: boolean;
}

export interface WalletTransferSettingsRequest {
  enabled: boolean;
  maxSingleTransferAmount: number;
  dailyOutgoingLimitAmount: number;
  currency: string;
  effectiveFrom: string;
  effectiveUntil?: string | null;
  reason?: string | null;
}

export interface DepositClaimResponse {
  id: number;
  operationId: string;
  playerId: number;
  submittedCashAmount: string;
  currency: string;
  conversionRateSnapshot: string;
  calculatedCreditAmount: string;
  normalizedTelebirrReference: string;
  status: string;
  submittedAt: string;
  reviewedBy: number | null;
  reviewedAt: string | null;
  reviewNote: string | null;
  paymentInstructions: PaymentInstructionsResponse | null;
}

export interface PaymentInstructionsResponse {
  providerName: string;
  businessAccount: string;
  paymentInstructions: string;
  currency: string;
}

export interface DepositReviewRequest {
  reviewNote: string;
  idempotencyKey?: string;
}

export interface WalletAdjustmentRequest {
  amount: number;
  currency: string;
  reason: string;
  idempotencyKey?: string;
}

export interface WalletAdjustmentResponse {
  operationId: string;
  walletId: number;
  playerId: number;
  amount: string;
  balanceBefore: string;
  balanceAfter: string;
  reason: string;
  createdAt: string;
}

export interface PageRequest {
  page?: number;
  size?: number;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface ChangeAdminPasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface RegisterRequest {
  displayName: string;
  phoneNumber: string;
  password: string;
}

export interface AuthPayload {
  phoneNumber: string;
  password: string;
}

export function buildQuery(page?: PageRequest) {
  return {
    page: page?.page ?? 0,
    size: page?.size ?? 20,
  };
}

export async function login(request: LoginRequest) {
  const { data } = await http.post<AuthResponse>('/api/v1/admin/login', request);
  return data;
}

export async function changeAdminPassword(request: ChangeAdminPasswordRequest) {
  await http.put('/api/v1/admin/me/password', request);
}

export async function me() {
  const { data } = await http.get<MeResponse>('/api/v1/me');
  return data;
}

export async function dashboardMetrics() {
  const { data } = await http.get<AdminDashboardMetricsResponse>('/api/v1/admin/dashboard/metrics');
  return data;
}

export async function searchPlayers(query?: string, page = 0, size = 20, status?: string) {
  const { data } = await http.get<PageResponse<AdminPlayerSummaryResponse>>('/api/v1/admin/players', {
    params: { query, status, page, size },
  });
  return data;
}

export async function playerDetail(playerId: number) {
  const { data } = await http.get<AdminPlayerDetailResponse>(`/api/v1/admin/players/${playerId}`);
  return data;
}

export async function adminSessionDetail(sessionId: number) {
  const { data } = await http.get<GameSessionResponse>(`/api/v1/admin/sessions/${sessionId}`);
  return data;
}

export async function adminTicketDetail(ticketId: number) {
  const { data } = await http.get<TicketResponse>(`/api/v1/admin/tickets/${ticketId}`);
  return data;
}

export async function adminWinClaimDetail(claimId: number) {
  const { data } = await http.get<WinClaimResponse>(`/api/v1/admin/win-claims/${claimId}`);
  return data;
}

export async function playerWalletTransactions(playerId: number, page = 0, size = 20) {
  const { data } = await http.get<PageResponse<WalletTransactionResponse>>(
    `/api/v1/admin/players/${playerId}/wallet-transactions`,
    { params: { page, size } },
  );
  return data;
}

export async function playerDeposits(playerId: number, page = 0, size = 20) {
  const { data } = await http.get<PageResponse<DepositClaimResponse>>(`/api/v1/admin/players/${playerId}/deposits`, {
    params: { page, size },
  });
  return data;
}

export async function playerSupportTickets(playerId: number, page = 0, size = 20) {
  const { data } = await http.get<PageResponse<SupportTicketSummaryResponse>>(
    `/api/v1/admin/players/${playerId}/support-tickets`,
    { params: { page, size } },
  );
  return data;
}

export async function playerTickets(playerId: number, page = 0, size = 20) {
  const { data } = await http.get<PageResponse<TicketResponse>>(`/api/v1/admin/players/${playerId}/tickets`, {
    params: { page, size },
  });
  return data;
}

export async function playerWinClaims(playerId: number, page = 0, size = 20) {
  const { data } = await http.get<PageResponse<WinClaimResponse>>(`/api/v1/admin/players/${playerId}/win-claims`, {
    params: { page, size },
  });
  return data;
}

export async function lookupWalletRecipient(phoneNumber: string) {
  const { data } = await http.get<WalletRecipientLookupResponse>('/api/v1/wallet/transfers/recipient', {
    params: { phoneNumber },
  });
  return data;
}

export async function submitWalletTransfer(request: WalletTransferRequest, idempotencyKey: string) {
  const { data } = await http.post<WalletTransferResponse>(
    '/api/v1/wallet/transfers',
    request,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  );
  return data;
}

export async function listRooms() {
  const { data } = await http.get<BingoRoomResponse[]>('/api/v1/rooms');
  return data;
}

export async function listAdminRooms(page = 0, size = 20, query?: string, active?: boolean) {
  const { data } = await http.get<PageResponse<BingoRoomResponse>>('/api/v1/admin/rooms', {
    params: { page, size, query, active },
  });
  return data;
}

export async function createRoom(body: Partial<BingoRoomResponse>) {
  const { data } = await http.post<BingoRoomResponse>('/api/v1/admin/rooms', body);
  return data;
}

export async function updateRoom(roomId: number, body: Partial<BingoRoomResponse>) {
  const { data } = await http.put<BingoRoomResponse>(`/api/v1/admin/rooms/${roomId}`, body);
  return data;
}

export async function listSessions(
  page = 0,
  size = 20,
  query?: string,
  state?: string,
  roomId?: number,
  upcoming?: boolean,
) {
  const { data } = await http.get<PageResponse<AdminSessionSummaryResponse>>('/api/v1/admin/sessions', {
    params: { page, size, query, state, roomId, upcoming },
  });
  return data;
}

export async function liveSessions() {
  const { data } = await http.get<AdminSessionSummaryResponse[]>('/api/v1/admin/sessions/live');
  return data;
}

export async function sessionDetail(sessionId: number) {
  const { data } = await http.get<GameSessionResponse>(`/api/v1/sessions/${sessionId}`);
  return data;
}

export async function createSession(roomId: number, body: Record<string, unknown>) {
  const { data } = await http.post<GameSessionResponse>(`/api/v1/admin/rooms/${roomId}/sessions`, body);
  return data;
}

export async function updateSessionState(sessionId: number, state: string) {
  const { data } = await http.put<GameSessionResponse>(`/api/v1/admin/sessions/${sessionId}/state`, { state });
  return data;
}

export async function sessionDraws(sessionId: number, afterSequence?: number) {
  const { data } = await http.get<DrawEventResponse[]>(`/api/v1/sessions/${sessionId}/draws`, {
    params: afterSequence == null ? undefined : { afterSequence },
  });
  return data;
}

export async function postDraw(sessionId: number, numberDrawn: number) {
  const { data } = await http.post<DrawEventResponse>(`/api/v1/admin/sessions/${sessionId}/draws`, { numberDrawn });
  return data;
}

export async function winClaims(
  page = 0,
  size = 20,
  query?: string,
  status?: string,
  pattern?: string,
  serverValidationPassed?: boolean,
) {
  const { data } = await http.get<PageResponse<WinClaimResponse>>('/api/v1/admin/win-claims', {
    params: { page, size, query, status, pattern, serverValidationPassed },
  });
  return data;
}

export async function approveWinClaim(claimId: number, body: WinClaimReviewRequest) {
  const { data } = await http.post<WinClaimResponse>(`/api/v1/admin/win-claims/${claimId}/approve`, { reviewNote: body.reviewNote }, {
    headers: body.idempotencyKey ? { 'Idempotency-Key': body.idempotencyKey } : undefined,
  });
  return data;
}

export async function rejectWinClaim(claimId: number, body: WinClaimReviewRequest) {
  const { data } = await http.post<WinClaimResponse>(`/api/v1/admin/win-claims/${claimId}/reject`, { reviewNote: body.reviewNote }, {
    headers: body.idempotencyKey ? { 'Idempotency-Key': body.idempotencyKey } : undefined,
  });
  return data;
}

export async function prizeEntitlements(page = 0, size = 20, query?: string, status?: string) {
  const { data } = await http.get<PageResponse<PrizeEntitlementResponse>>('/api/v1/admin/prize-entitlements', {
    params: { page, size, query, status },
  });
  return data;
}

export interface PrizeEntitlementSettlementRequest {
  settlementReference: string;
  settlementNote?: string;
}

export async function settlePrizeEntitlement(
  entitlementId: number,
  body: PrizeEntitlementSettlementRequest,
) {
  const { data } = await http.post<PrizeEntitlementResponse>(`/api/v1/admin/prize-entitlements/${entitlementId}/settle`, body);
  return data;
}

export async function deposits(page = 0, size = 20, query?: string, status?: string) {
  const { data } = await http.get<PageResponse<DepositClaimResponse>>('/api/v1/admin/deposits', {
    params: { page, size, query, status },
  });
  return data;
}

export async function approveDeposit(depositId: number, body: DepositReviewRequest) {
  const { data } = await http.post<DepositClaimResponse>(
    `/api/v1/admin/deposits/${depositId}/approve`,
    { reviewNote: body.reviewNote },
    { headers: body.idempotencyKey ? { 'Idempotency-Key': body.idempotencyKey } : undefined },
  );
  return data;
}

export async function rejectDeposit(depositId: number, body: DepositReviewRequest) {
  const { data } = await http.post<DepositClaimResponse>(
    `/api/v1/admin/deposits/${depositId}/reject`,
    { reviewNote: body.reviewNote },
    { headers: body.idempotencyKey ? { 'Idempotency-Key': body.idempotencyKey } : undefined },
  );
  return data;
}

export async function adjustWallet(
  playerId: number,
  body: WalletAdjustmentRequest,
) {
  const { idempotencyKey, ...requestBody } = body;
  const { data } = await http.post<WalletAdjustmentResponse>(
    `/api/v1/admin/players/${playerId}/wallet-adjustments`,
    requestBody,
    { headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined },
  );
  return data;
}

export async function currentSignupRewardSettings() {
  const { data } = await http.get<SignupRewardSettingsResponse>('/api/v1/admin/wallet/signup-rewards/settings');
  return data;
}

export async function signupRewardSettingsHistory(page = 0, size = 20) {
  const { data } = await http.get<PageResponse<SignupRewardSettingsResponse>>(
    '/api/v1/admin/wallet/signup-rewards/settings/history',
    { params: { page, size } },
  );
  return data;
}

export async function saveSignupRewardSettings(request: SignupRewardSettingsRequest) {
  const { data } = await http.post<SignupRewardSettingsResponse>('/api/v1/admin/wallet/signup-rewards/settings', request);
  return data;
}

export async function signupRewardGrants(page = 0, size = 20) {
  const { data } = await http.get<PageResponse<SignupRewardGrantResponse>>('/api/v1/admin/wallet/signup-rewards/grants', {
    params: { page, size },
  });
  return data;
}

export async function currentWalletTransferSettings() {
  const { data } = await http.get<WalletTransferSettingsResponse>('/api/v1/admin/wallet/transfers/settings');
  return data;
}

export async function walletTransferSettingsHistory(page = 0, size = 20) {
  const { data } = await http.get<PageResponse<WalletTransferSettingsResponse>>(
    '/api/v1/admin/wallet/transfers/settings/history',
    { params: { page, size } },
  );
  return data;
}

export async function saveWalletTransferSettings(request: WalletTransferSettingsRequest) {
  const { data } = await http.post<WalletTransferSettingsResponse>('/api/v1/admin/wallet/transfers/settings', request);
  return data;
}

export async function walletTransfers(
  query?: string,
  status?: string,
  senderPhone?: string,
  recipientPhone?: string,
  page = 0,
  size = 20,
) {
  const { data } = await http.get<PageResponse<WalletTransferResponse>>('/api/v1/admin/wallet/transfers', {
    params: { query, status, senderPhone, recipientPhone, page, size },
  });
  return data;
}

export async function walletTransferDetail(transferId: number) {
  const { data } = await http.get<WalletTransferResponse>(`/api/v1/admin/wallet/transfers/${transferId}`);
  return data;
}

export async function auditLogs(page = 0, size = 20) {
  const { data } = await http.get<PageResponse<AdminAuditLogSummaryResponse>>('/api/v1/admin/audit-logs', {
    params: { page, size },
  });
  return data;
}

export async function auditLogDetail(auditLogId: number) {
  const { data } = await http.get<AdminAuditLogDetailResponse>(`/api/v1/admin/audit-logs/${auditLogId}`);
  return data;
}

export async function reviewQueue(page = 0, size = 20) {
  const { data } = await http.get<PageResponse<AdminReviewQueueItemResponse>>('/api/v1/admin/review-queue', {
    params: { page, size },
  });
  return data;
}

export async function adminUsers(page = 0, size = 20, search = "", status = "") {
  const { data } = await http.get<PageResponse<UserSummaryResponse>>('/api/v1/admin/users', {
    params: { page, size, search: search.trim() || undefined, status: status.trim() || undefined },
  });
  return data;
}

export async function createAdminUser(request: AdminUserCreateRequest) {
  const { data } = await http.post<UserSummaryResponse>('/api/v1/admin/users', request);
  return data;
}

export async function updateAdminUser(userId: number, request: AdminUserUpdateRequest) {
  const { data } = await http.put<UserSummaryResponse>(`/api/v1/admin/users/${userId}`, request);
  return data;
}

export async function adminAgents(page = 0, size = 20) {
  const { data } = await http.get<PageResponse<AdminAgentSummaryResponse>>('/api/v1/admin/agents', {
    params: { page, size },
  });
  return data;
}

export async function adminAgentDetail(agentId: number) {
  const { data } = await http.get<AdminAgentDetailResponse>(`/api/v1/admin/agents/${agentId}`);
  return data;
}

export async function approveAgent(userId: number, body: AgentProfileResponse) {
  const { data } = await http.post<AgentProfileResponse>(`/api/v1/admin/agents/${userId}/approve`, body);
  return data;
}

export async function suspendAgent(agentId: number) {
  const { data } = await http.post<AgentProfileResponse>(`/api/v1/admin/agents/${agentId}/suspend`);
  return data;
}

export async function reviewFloatTopUpRequest(requestId: number, body: ReviewFloatTopUpRequest) {
  const { data } = await http.post<FloatTopUpRequestResponse>(`/api/v1/admin/agents/float-top-up-requests/${requestId}/review`, body);
  return data;
}

export async function commissionRule(body: CommissionRuleRequest) {
  const { data } = await http.post<CommissionRuleRequest>('/api/v1/admin/agents/commission-rules', body);
  return data;
}

export async function supportTickets(
  query?: string,
  status?: string,
  category?: string,
  assignedToUserId?: string,
  priority?: string,
  page = 0,
  size = 20,
) {
  const { data } = await http.get<PageResponse<SupportTicketSummaryResponse>>('/api/v1/admin/support/tickets', {
    params: { query, status, category, assignedToUserId, priority, page, size },
  });
  return data;
}

export async function supportTicketDetail(ticketId: number) {
  const { data } = await http.get<SupportTicketResponse>(`/api/v1/admin/support/tickets/${ticketId}`);
  return data;
}

export async function assignSupportTicket(ticketId: number, body: SupportTicketAssignmentRequest) {
  const { data } = await http.post<SupportTicketResponse>(`/api/v1/admin/support/tickets/${ticketId}/assign`, body);
  return data;
}

export async function replySupportTicket(ticketId: number, body: SupportTicketMessageRequest) {
  const { data } = await http.post<SupportTicketResponse>(`/api/v1/admin/support/tickets/${ticketId}/messages`, body);
  return data;
}

export async function noteSupportTicket(ticketId: number, body: SupportTicketMessageRequest) {
  const { data } = await http.post<SupportTicketResponse>(`/api/v1/admin/support/tickets/${ticketId}/notes`, body);
  return data;
}

export async function updateSupportTicketStatus(ticketId: number, body: SupportTicketStatusUpdateRequest) {
  const { data } = await http.put<SupportTicketResponse>(`/api/v1/admin/support/tickets/${ticketId}/status`, body);
  return data;
}
