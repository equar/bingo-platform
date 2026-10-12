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
  roles: string[];
  status: string;
  createdAt: string;
  updatedAt: string;
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
  updatedAt: string;
}

export interface WalletTransactionResponse {
  id: number;
  operationId: string;
  transactionType: string;
  amount: string;
  balanceBefore: string;
  balanceAfter: string;
  currency: string;
  referenceType: string;
  referenceId: number | null;
  note: string | null;
  createdAt: string;
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
  phoneNumber: string;
  password: string;
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
  const { data } = await http.post<AuthResponse>('/api/v1/auth/login', request);
  return data;
}

export async function me() {
  const { data } = await http.get<MeResponse>('/api/v1/me');
  return data;
}

export async function dashboardMetrics() {
  const { data } = await http.get<AdminDashboardMetricsResponse>('/api/v1/admin/dashboard/metrics');
  return data;
}

export async function searchPlayers(query?: string, page = 0, size = 20) {
  const { data } = await http.get<PageResponse<AdminPlayerSummaryResponse>>('/api/v1/admin/players', {
    params: { query, page, size },
  });
  return data;
}

export async function playerDetail(playerId: number) {
  const { data } = await http.get<AdminPlayerDetailResponse>(`/api/v1/admin/players/${playerId}`);
  return data;
}

export async function playerWalletTransactions(playerId: number, page = 0, size = 20) {
  const { data } = await http.get<PageResponse<WalletTransactionResponse>>('/api/v1/wallet/transactions', {
    params: { page, size },
  });
  return data;
}

export async function listRooms() {
  const { data } = await http.get<BingoRoomResponse[]>('/api/v1/rooms');
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

export async function listSessions(page = 0, size = 20) {
  const { data } = await http.get<PageResponse<AdminSessionSummaryResponse>>('/api/v1/admin/sessions', {
    params: { page, size },
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

export async function winClaims(page = 0, size = 20) {
  const { data } = await http.get<PageResponse<WinClaimResponse>>('/api/v1/admin/win-claims', {
    params: { page, size },
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

export async function prizeEntitlements(page = 0, size = 20) {
  const { data } = await http.get<PageResponse<PrizeEntitlementResponse>>('/api/v1/admin/prize-entitlements', {
    params: { page, size },
  });
  return data;
}

export async function deposits(page = 0, size = 20) {
  const { data } = await http.get<PageResponse<DepositClaimResponse>>('/api/v1/admin/deposits', {
    params: { page, size },
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

export async function auditLogs(page = 0, size = 20) {
  const { data } = await http.get<PageResponse<AdminAuditLogSummaryResponse>>('/api/v1/admin/audit-logs', {
    params: { page, size },
  });
  return data;
}

export async function reviewQueue(page = 0, size = 20) {
  const { data } = await http.get<PageResponse<AdminReviewQueueItemResponse>>('/api/v1/admin/review-queue', {
    params: { page, size },
  });
  return data;
}
