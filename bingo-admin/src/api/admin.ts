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

export interface PlayerStatusResponse {
  playerId: number;
  status: string;
  updatedAt: string;
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

export interface AdminAgentDetailResponse extends AdminAgentSummaryResponse {
  idDocumentType: string;
  idDocumentNumber: string;
  idDocumentFrontReference: string | null;
  idDocumentBackReference: string | null;
}

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
  submissionNote?: string | null;
    receivingBankAccountId?: number | null;
    bankNameSnapshot?: string | null;
    maskedAccountSnapshot?: string | null;
    accountHolderSnapshot?: string | null;
    transferReference?: string | null;
    supportingEvidenceNote?: string | null;
}

export interface AdminFloatTopUpRequestResponse extends FloatTopUpRequestResponse {
  agentDisplayName: string;
  agentPhoneNumber: string;
}

export interface CommissionRuleRequest {
  commissionRateBps: number;
  effectiveFrom: string;
  active: boolean;
}

export interface AdminCommissionRuleResponse extends CommissionRuleRequest {
  id: number | null;
  versionNumber: number;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface ReviewFloatTopUpRequest {
  approve: boolean;
  reviewNote: string;
}

export interface FloatBankAccountResponse {
  id: number;
  bankName: string;
  maskedAccountNumber: string;
  accountHolderName: string;
  currency: string;
  active: boolean;
  playerDepositsEnabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface FloatBankAccountRequest {
  bankName: string;
  accountNumber: string;
  accountHolderName: string;
  currency: "ETB";
}

export interface FloatVerificationResponse {
  id: number | null;
  requestId: number | null;
  statementEntryId: number | null;
  reviewerUserId: number;
  outcome: string;
  exceptionReason: string | null;
  evidenceSource: string;
  actualReceivingBankAccountId: number | null;
  bankTransactionReference: string | null;
  verifiedAmount: string | null;
  bankTransactionDate: string | null;
  verifiedAt: string;
  synthetic: boolean;
}

export interface FloatPostingResponse {
  journalId: number;
  requestId: number;
  postingReference: string;
  simulated: boolean;
  currency: string;
  amount: string;
  availableBalance: string;
  verifierUserId: number;
  approverUserId: number;
  postedAt: string;
}

export interface FloatReconciliationResponse {
  statementDate: string;
  syntheticQaData: boolean;
  settledStatementCount: number;
  settledStatementAmount: string;
  postingCount: number;
  postedAmount: string;
  debitTotal: string;
  creditTotal: string;
  unmatchedSettledStatementCount: number;
  verifiedUnpostedRequestCount: number;
  unresolvedExceptionCount: number;
  statementToPostingDifference: string;
  journalBalanced: boolean;
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

export interface SupportAssigneeResponse {
  userId: number;
  displayName: string;
  roles: string[];
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
  version?: number;
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

export interface WalletTransferLedgerEntryResponse {
  id: number;
  walletId: number;
  walletOwnerId: number;
  operationId: string;
  transactionType: string;
  amount: string;
  balanceBefore: string;
  balanceAfter: string;
  currency: string;
  referenceType: string | null;
  referenceId: number | null;
  createdAt: string;
}

export interface WalletTransferDetailResponse {
  transfer: WalletTransferResponse;
  ledgerEntries: WalletTransferLedgerEntryResponse[];
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
  verifiedBy: number | null;
  verifiedAt: string | null;
  paymentInstructions: PaymentInstructionsResponse | null;
}

export interface DepositVerificationResponse {
  id: number;
  depositClaimId: number;
  statementEntryId: number | null;
  receivingBankAccountId: number | null;
  transactionReference: string | null;
  amount: string | null;
  currency: string | null;
  transactionDate: string | null;
  outcome: string;
  evidenceSource: string;
  synthetic: boolean;
  reviewerUserId: number;
  createdAt: string;
}

export interface WalletAdjustmentSubmissionRequest {
  amount: number;
  currency: string;
  reason: string;
  supportingReference: string;
}

export interface WalletAdjustmentRequestResponse {
  id: number;
  playerId: number;
  makerUserId: number;
  amount: string;
  currency: string;
  reason: string;
  supportingReference: string;
  status: string;
  operationId: string;
  checkerUserId: number | null;
  reviewNote: string | null;
  submittedAt: string;
  reviewedAt: string | null;
  journalId: number | null;
  postingReference: string | null;
  balanceBefore: string | null;
  balanceAfter: string | null;
  simulated: boolean;
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

export async function activateAdminAccount(request: { token: string; newPassword: string }) {
  await http.post('/api/v1/admin/activate', request);
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

export async function updatePlayerStatus(playerId: number, status: string, reason: string) {
  const { data } = await http.put<PlayerStatusResponse>(`/api/v1/admin/players/${playerId}/status`, {
    status,
    reason,
  });
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

export async function verifyDeposit(depositId: number, statementEntryId: number) {
  const { data } = await http.post<DepositVerificationResponse>(
    `/api/v1/admin/deposits/${depositId}/verify`,
    { statementEntryId },
  );
  return data;
}

export async function submitWalletAdjustmentRequest(
  playerId: number,
  body: WalletAdjustmentSubmissionRequest,
) {
  const { data } = await http.post<WalletAdjustmentRequestResponse>(
    `/api/v1/admin/players/${playerId}/wallet-adjustments/requests`,
    body,
  );
  return data;
}

export async function walletAdjustmentHistory(playerId: number) {
  const { data } = await http.get<WalletAdjustmentRequestResponse[]>(
    `/api/v1/admin/players/${playerId}/wallet-adjustments/requests`,
  );
  return data;
}

export async function pendingWalletAdjustmentRequests() {
  const { data } = await http.get<WalletAdjustmentRequestResponse[]>(
    "/api/v1/admin/wallet-adjustment-requests",
  );
  return data;
}

export async function reviewWalletAdjustmentRequest(
  requestId: number,
  decision: "approve" | "reject",
  reviewNote: string,
) {
  const { data } = await http.post<WalletAdjustmentRequestResponse>(
    `/api/v1/admin/wallet-adjustment-requests/${requestId}/${decision}`,
    { reviewNote },
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
  const { data } = await http.get<WalletTransferDetailResponse>(`/api/v1/admin/wallet/transfers/${transferId}`);
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

export async function refreshAdminUserProfile() {
  const { data } = await http.get<MeResponse>("/api/v1/auth/me");
  return data.user;
}

export async function createAdminUser(request: AdminUserCreateRequest) {
  const { data } = await http.post<UserSummaryResponse>('/api/v1/admin/users', request);
  return data;
}

export async function updateAdminUser(userId: number, request: AdminUserUpdateRequest) {
  const { data } = await http.put<UserSummaryResponse>(`/api/v1/admin/users/${userId}`, request);
  return data;
}

export async function revokeFinanceRole(userId: number, role: string) {
  const { data } = await http.delete<UserSummaryResponse>(`/api/v1/admin/users/${userId}/finance-roles`, {
    params: { role },
  });
  return data;
}

export async function setAgentPin(userId: number, pin: string) {
  await http.post<void>(`/api/v1/admin/users/${userId}/agent-pin`, { pin });
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

export async function adminAgentFloatBalance(agentId: number) {
  const { data } = await http.get<AgentFloatResponse>(`/api/v1/admin/agents/${agentId}/float`);
  return data;
}

export async function adminAgentFloatHistory(agentId: number, page = 0, size = 20) {
  const { data } = await http.get<PageResponse<AgentFloatTransactionResponse>>(
    `/api/v1/admin/agents/${agentId}/float/history`, { params: { page, size } },
  );
  return data;
}

export async function approveAgent(agentId: number) {
  const { data } = await http.post<AgentProfileResponse>(`/api/v1/admin/agents/${agentId}/approve`);
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

export async function adminFloatTopUpRequests(page = 0, size = 20, status = "PENDING", query?: string) {
  const { data } = await http.get<PageResponse<AdminFloatTopUpRequestResponse>>(
    "/api/v1/admin/agents/float-top-up-requests",
    { params: { page, size, status, query } },
  );
  return data;
}

export async function adminFloatBankAccounts() {
  const { data } = await http.get<FloatBankAccountResponse[]>("/api/v1/admin/float-bank-accounts");
  return data;
}

export async function createFloatBankAccount(body: FloatBankAccountRequest) {
  const { data } = await http.post<FloatBankAccountResponse>("/api/v1/admin/float-bank-accounts", body);
  return data;
}

export async function setFloatBankAccountActive(accountId: number, active: boolean) {
  const { data } = await http.put<FloatBankAccountResponse>(
    `/api/v1/admin/float-bank-accounts/${accountId}/active`, null, { params: { active } },
  );
  return data;
}

export async function setFloatBankAccountPlayerDepositsEnabled(accountId: number, enabled: boolean) {
  const { data } = await http.put<FloatBankAccountResponse>(
    `/api/v1/admin/float-bank-accounts/${accountId}/player-deposit-eligibility`,
    null,
    { params: { enabled } },
  );
  return data;
}

export async function recordSyntheticFloatBankStatement(body: {
  receivingBankAccountId: number; transactionReference: string; amount: string; currency: "ETB";
  transactionDate: string; settlementState: "SETTLED" | "UNSETTLED" | "REVERSED";
}) {
  const { data } = await http.post<FloatVerificationResponse>("/api/v1/admin/float-bank-statements/synthetic", body);
  return data;
}

export async function verifyFloatTopUp(requestId: number, body: {
  statementEntryId?: number; exceptionOutcome?: string; exceptionReason?: string;
}) {
  const { data } = await http.post<FloatVerificationResponse>(
    `/api/v1/admin/agents/float-top-up-requests/${requestId}/verify`, body,
  );
  return data;
}

export async function floatTopUpVerificationHistory(requestId: number) {
  const { data } = await http.get<FloatVerificationResponse[]>(
    `/api/v1/admin/agents/float-top-up-requests/${requestId}/verifications`,
  );
  return data;
}

export async function postVerifiedFloatTopUp(requestId: number, reviewNote: string) {
  const { data } = await http.post<FloatPostingResponse>(
    `/api/v1/admin/agents/float-top-up-requests/${requestId}/post`, { approve: true, reviewNote },
  );
  return data;
}

export async function dailyFloatReconciliation(date: string) {
  const { data } = await http.get<FloatReconciliationResponse>(
    "/api/v1/admin/float-reconciliation/daily", { params: { date } },
  );
  return data;
}

export async function commissionRule(body: CommissionRuleRequest) {
  const { data } = await http.post<CommissionRuleRequest>('/api/v1/admin/agents/commission-rules', body);
  return data;
}

export async function currentCommissionRule() {
  const { data } = await http.get<AdminCommissionRuleResponse>('/api/v1/admin/agents/commission-rules/current');
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

export async function supportAssignees() {
  const { data } = await http.get<SupportAssigneeResponse[]>("/api/v1/admin/support/tickets/assignees");
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
