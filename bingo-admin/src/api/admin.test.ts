import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  adjustWallet,
  adminAgents,
  adminAgentFloatBalance,
  adminAgentFloatHistory,
  adminFloatBankAccounts,
  createFloatBankAccount,
  setFloatBankAccountActive,
  recordSyntheticFloatBankStatement,
  verifyFloatTopUp,
  setFloatBankAccountPlayerDepositsEnabled,
  floatTopUpVerificationHistory,
  postVerifiedFloatTopUp,
  dailyFloatReconciliation,
  adminFloatTopUpRequests,
  approveDeposit,
  approveWinClaim,
  deposits,
  listSessions,
  login,
  playerWalletTransactions,
  postDraw,
  prizeEntitlements,
  rejectDeposit,
  rejectWinClaim,
  reviewFloatTopUpRequest,
  sessionDraws,
  settlePrizeEntitlement,
  verifyDeposit,
  submitWalletAdjustmentRequest,
  walletAdjustmentHistory,
  pendingWalletAdjustmentRequests,
  reviewWalletAdjustmentRequest,
  submitWalletTransfer,
  winClaims,
} from "./admin";

const httpMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
}));

vi.mock("./http", () => ({ http: httpMock }));

describe("admin API client", () => {
  beforeEach(() => {
    httpMock.get.mockReset().mockResolvedValue({ data: { result: true } });
    httpMock.post.mockReset().mockResolvedValue({ data: { result: true } });
    httpMock.put.mockReset().mockResolvedValue({ data: { result: true } });
  });

  afterEach(() => vi.clearAllMocks());

  it("sends admin login credentials only to the login endpoint", async () => {
    const request = { email: "admin@example.test", password: "disposable-test-password" };

    await login(request);

    expect(httpMock.post).toHaveBeenCalledWith("/api/v1/admin/login", request);
  });

  it("lists and reviews deposits using the expected routes, payloads, and idempotency headers", async () => {
    await deposits(2, 10, "player", "PENDING");
    await approveDeposit(41, { reviewNote: "Verified", idempotencyKey: "deposit-key" });
    await rejectDeposit(42, { reviewNote: "Unverified" });

    expect(httpMock.get).toHaveBeenCalledWith("/api/v1/admin/deposits", {
      params: { page: 2, size: 10, query: "player", status: "PENDING" },
    });
    expect(httpMock.post).toHaveBeenNthCalledWith(
      1,
      "/api/v1/admin/deposits/41/approve",
      { reviewNote: "Verified" },
      { headers: { "Idempotency-Key": "deposit-key" } },
    );
    expect(httpMock.post).toHaveBeenNthCalledWith(2, "/api/v1/admin/deposits/42/reject", {
      reviewNote: "Unverified",
    }, { headers: undefined });
  });

  it("uses independent bank evidence and maker-checker wallet adjustment routes", async () => {
    await verifyDeposit(41, 81);
    await submitWalletAdjustmentRequest(6, {
      amount: -12.5,
      currency: "ETB",
      reason: "Correction",
      supportingReference: "CASE-18",
    });
    await walletAdjustmentHistory(6);
    await pendingWalletAdjustmentRequests();
    await reviewWalletAdjustmentRequest(71, "approve", "Checked");

    expect(httpMock.post).toHaveBeenNthCalledWith(
      1,
      "/api/v1/admin/deposits/41/verify",
      { statementEntryId: 81 },
    );
    expect(httpMock.post).toHaveBeenNthCalledWith(
      2,
      "/api/v1/admin/players/6/wallet-adjustments/requests",
      {
        amount: -12.5,
        currency: "ETB",
        reason: "Correction",
        supportingReference: "CASE-18",
      },
    );
    expect(httpMock.get).toHaveBeenNthCalledWith(
      1,
      "/api/v1/admin/players/6/wallet-adjustments/requests",
    );
    expect(httpMock.get).toHaveBeenNthCalledWith(2, "/api/v1/admin/wallet-adjustment-requests");
    expect(httpMock.post).toHaveBeenNthCalledWith(
      3,
      "/api/v1/admin/wallet-adjustment-requests/71/approve",
      { reviewNote: "Checked" },
    );
  });

  it("reviews win claims and lists prize entitlements", async () => {
    await winClaims(1, 15, "claim", "PENDING_REVIEW", "LINE", true);
    await approveWinClaim(21, { reviewNote: "Validated", idempotencyKey: "claim-key" });
    await rejectWinClaim(22, { reviewNote: "Invalid" });
    await prizeEntitlements(0, 20, "winner", "PENDING");
    await settlePrizeEntitlement(24, { settlementReference: "PAYOUT-24", settlementNote: "Paid" });

    expect(httpMock.get).toHaveBeenNthCalledWith(1, "/api/v1/admin/win-claims", {
      params: {
        page: 1,
        size: 15,
        query: "claim",
        status: "PENDING_REVIEW",
        pattern: "LINE",
        serverValidationPassed: true,
      },
    });
    expect(httpMock.post).toHaveBeenNthCalledWith(
      1,
      "/api/v1/admin/win-claims/21/approve",
      { reviewNote: "Validated" },
      { headers: { "Idempotency-Key": "claim-key" } },
    );
    expect(httpMock.post).toHaveBeenNthCalledWith(2, "/api/v1/admin/win-claims/22/reject", {
      reviewNote: "Invalid",
    }, { headers: undefined });
    expect(httpMock.get).toHaveBeenNthCalledWith(2, "/api/v1/admin/prize-entitlements", {
      params: { page: 0, size: 20, query: "winner", status: "PENDING" },
    });
    expect(httpMock.post).toHaveBeenLastCalledWith(
      "/api/v1/admin/prize-entitlements/24/settle",
      { settlementReference: "PAYOUT-24", settlementNote: "Paid" },
    );
  });

  it("uses authoritative wallet endpoints and sends idempotency keys for money operations", async () => {
    await playerWalletTransactions(6, 3, 25);
    await adjustWallet(6, {
      amount: -20,
      currency: "ETB",
      reason: "Support correction",
      idempotencyKey: "wallet-key",
    });
    await submitWalletTransfer(
      { recipientPhoneNumber: "251911000001", amount: 5, currency: "ETB", note: "Transfer" },
      "transfer-key",
    );

    expect(httpMock.get).toHaveBeenCalledWith("/api/v1/admin/players/6/wallet-transactions", {
      params: { page: 3, size: 25 },
    });
    expect(httpMock.post).toHaveBeenNthCalledWith(
      1,
      "/api/v1/admin/players/6/wallet-adjustments",
      { amount: -20, currency: "ETB", reason: "Support correction" },
      { headers: { "Idempotency-Key": "wallet-key" } },
    );
    expect(httpMock.post).toHaveBeenNthCalledWith(
      2,
      "/api/v1/wallet/transfers",
      { recipientPhoneNumber: "251911000001", amount: 5, currency: "ETB", note: "Transfer" },
      { headers: { "Idempotency-Key": "transfer-key" } },
    );
  });

  it("queries sessions and draw history and submits a draw to the admin endpoint", async () => {
    await listSessions(2, 10, "evening", "LIVE", 3, true);
    await sessionDraws(9, 12);
    await postDraw(9, 73);

    expect(httpMock.get).toHaveBeenNthCalledWith(1, "/api/v1/admin/sessions", {
      params: { page: 2, size: 10, query: "evening", state: "LIVE", roomId: 3, upcoming: true },
    });
    expect(httpMock.get).toHaveBeenNthCalledWith(2, "/api/v1/sessions/9/draws", {
      params: { afterSequence: 12 },
    });
    expect(httpMock.post).toHaveBeenCalledWith("/api/v1/admin/sessions/9/draws", { numberDrawn: 73 });
  });

  it("lists agents and submits an explicit float-review decision", async () => {
    await adminAgents(1, 30);
    await adminFloatTopUpRequests(0, 100, "PENDING", "251911000001");
    await adminAgentFloatBalance(4);
    await adminAgentFloatHistory(4, 1, 10);
    await reviewFloatTopUpRequest(17, { approve: false, reviewNote: "Insufficient evidence" });

    expect(httpMock.get).toHaveBeenNthCalledWith(1, "/api/v1/admin/agents", { params: { page: 1, size: 30 } });
    expect(httpMock.get).toHaveBeenNthCalledWith(2, "/api/v1/admin/agents/float-top-up-requests", {
      params: { page: 0, size: 100, status: "PENDING", query: "251911000001" },
    });
    expect(httpMock.get).toHaveBeenNthCalledWith(3, "/api/v1/admin/agents/4/float");
    expect(httpMock.get).toHaveBeenNthCalledWith(4, "/api/v1/admin/agents/4/float/history", {
      params: { page: 1, size: 10 },
    });
    expect(httpMock.post).toHaveBeenCalledWith(
      "/api/v1/admin/agents/float-top-up-requests/17/review",
      { approve: false, reviewNote: "Insufficient evidence" },
    );
  });

  it("uses the manual funding verification, posting, and reconciliation routes", async () => {
    const account = { bankName: "QA Bank", accountNumber: "0000123456", accountHolderName: "QA Synthetic", currency: "ETB" as const };
    const statement = { receivingBankAccountId: 5, transactionReference: "QA-REF-1", amount: "25.00", currency: "ETB" as const, transactionDate: "2026-10-23", settlementState: "SETTLED" as const };
    await adminFloatBankAccounts();
    await createFloatBankAccount(account);
    await setFloatBankAccountActive(5, false);
    await setFloatBankAccountPlayerDepositsEnabled(5, true);
    await recordSyntheticFloatBankStatement(statement);
    await verifyFloatTopUp(17, { statementEntryId: 9 });
    await floatTopUpVerificationHistory(17);
    await postVerifiedFloatTopUp(17, "Independent QA review");
    await dailyFloatReconciliation("2026-10-23");

    expect(httpMock.get).toHaveBeenNthCalledWith(1, "/api/v1/admin/float-bank-accounts");
    expect(httpMock.post).toHaveBeenNthCalledWith(1, "/api/v1/admin/float-bank-accounts", account);
    expect(httpMock.put).toHaveBeenCalledWith("/api/v1/admin/float-bank-accounts/5/active", null, { params: { active: false } });
    expect(httpMock.put).toHaveBeenCalledWith(
      "/api/v1/admin/float-bank-accounts/5/player-deposit-eligibility",
      null,
      { params: { enabled: true } },
    );
    expect(httpMock.post).toHaveBeenNthCalledWith(2, "/api/v1/admin/float-bank-statements/synthetic", statement);
    expect(httpMock.post).toHaveBeenNthCalledWith(3, "/api/v1/admin/agents/float-top-up-requests/17/verify", { statementEntryId: 9 });
    expect(httpMock.get).toHaveBeenNthCalledWith(2, "/api/v1/admin/agents/float-top-up-requests/17/verifications");
    expect(httpMock.post).toHaveBeenNthCalledWith(4, "/api/v1/admin/agents/float-top-up-requests/17/post", { approve: true, reviewNote: "Independent QA review" });
    expect(httpMock.get).toHaveBeenNthCalledWith(3, "/api/v1/admin/float-reconciliation/daily", { params: { date: "2026-10-23" } });
  });
});
