import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  adminSessionDetail,
  adminTicketDetail,
  adminWinClaimDetail,
  playerDetail,
  prizeEntitlements,
  settlePrizeEntitlement,
} from "../../api/admin";
import { PrizeEntitlementsPage } from "./PrizeEntitlementsPage";

vi.mock("../../api/admin", () => ({
  adminSessionDetail: vi.fn(),
  adminTicketDetail: vi.fn(),
  adminWinClaimDetail: vi.fn(),
  playerDetail: vi.fn(),
  prizeEntitlements: vi.fn(),
  settlePrizeEntitlement: vi.fn(),
}));

const adminSessionDetailMock = vi.mocked(adminSessionDetail);
const adminTicketDetailMock = vi.mocked(adminTicketDetail);
const adminWinClaimDetailMock = vi.mocked(adminWinClaimDetail);
const playerDetailMock = vi.mocked(playerDetail);
const prizeEntitlementsMock = vi.mocked(prizeEntitlements);
const settlePrizeEntitlementMock = vi.mocked(settlePrizeEntitlement);

const entitlement = {
  id: 24,
  winClaimId: 12,
  sessionId: 8,
  ticketId: 44,
  claimantId: 31,
  prizeType: "FULL_HOUSE",
  settlementPath: "EXTERNAL_AGENT",
  status: "PENDING",
  prizeAmount: "500.00",
  currency: "ETB",
  operationId: "entitlement-op-24",
  createdAt: "2026-10-20T15:07:22Z",
  settledAt: null,
  settledBy: null,
  settlementReference: null,
  settlementNote: null,
};

function createWrapper({ children }: PropsWithChildren) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

describe("PrizeEntitlementsPage", () => {
  afterEach(cleanup);

  beforeEach(() => {
    prizeEntitlementsMock.mockReset();
    settlePrizeEntitlementMock.mockReset();
    adminSessionDetailMock.mockReset();
    adminTicketDetailMock.mockReset();
    adminWinClaimDetailMock.mockReset();
    playerDetailMock.mockReset();
    prizeEntitlementsMock.mockResolvedValue({
      content: [entitlement, { ...entitlement, id: 25, status: "SETTLED" }],
      page: 0,
      size: 20,
      totalElements: 42,
      totalPages: 3,
      first: true,
      last: false,
    });
    settlePrizeEntitlementMock.mockResolvedValue({ ...entitlement, status: "SETTLED" });
  });

  it("forwards search, status, and pagination to the server", async () => {
    const user = userEvent.setup();
    render(<PrizeEntitlementsPage />, { wrapper: createWrapper });

    expect(await screen.findByTestId("entitlement-row-24")).toBeInTheDocument();
    expect(prizeEntitlementsMock).toHaveBeenCalledWith(0, 20, undefined, undefined);

    await user.type(screen.getByRole("textbox", { name: "Search entitlements" }), "entitlement-op");
    await waitFor(() => expect(prizeEntitlementsMock).toHaveBeenLastCalledWith(0, 20, "entitlement-op", undefined));

    await user.click(screen.getByRole("combobox", { name: "Status" }));
    await user.click(await screen.findByRole("option", { name: "Pending" }));
    await waitFor(() => expect(prizeEntitlementsMock).toHaveBeenLastCalledWith(0, 20, "entitlement-op", "PENDING"));

    await user.click(screen.getByRole("button", { name: "Go to next page" }));
    await waitFor(() => expect(prizeEntitlementsMock).toHaveBeenLastCalledWith(1, 20, "entitlement-op", "PENDING"));
  });

  it("blocks settlement until payout and accounting policy are approved", async () => {
    render(<PrizeEntitlementsPage />, { wrapper: createWrapper });

    await screen.findByTestId("entitlement-row-24");
    expect(screen.getByRole("alert")).toHaveTextContent("Prize settlement is blocked");
    expect(screen.getByTestId("entitlement-settle-24")).toBeDisabled();
    expect(screen.getByTestId("entitlement-settle-25")).toBeDisabled();
    expect(settlePrizeEntitlementMock).not.toHaveBeenCalled();
  });

  it("does not submit a settlement request when an operator activates the disabled action", async () => {
    render(<PrizeEntitlementsPage />, { wrapper: createWrapper });
    await screen.findByTestId("entitlement-row-24");

    fireEvent.click(screen.getByTestId("entitlement-settle-24"));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(settlePrizeEntitlementMock).not.toHaveBeenCalled();
  });

  it("keeps settlement blocked after refresh data contains pending and settled entries", async () => {
    const user = userEvent.setup();
    render(<PrizeEntitlementsPage />, { wrapper: createWrapper });
    await screen.findByTestId("entitlement-row-24");
    await user.click(screen.getByRole("button", { name: "Go to next page" }));
    await waitFor(() => expect(prizeEntitlementsMock).toHaveBeenLastCalledWith(1, 20, undefined, undefined));

    expect(screen.getByTestId("entitlement-settle-24")).toBeDisabled();
    expect(screen.getByTestId("entitlement-settle-25")).toBeDisabled();
    expect(settlePrizeEntitlementMock).not.toHaveBeenCalled();
  });

  it("opens player, claim, session, and ticket details in popups", async () => {
    const user = userEvent.setup();
    playerDetailMock.mockResolvedValue({
      id: 31,
      displayName: "Winner Thirty One",
      normalizedPhone: "251911000031",
      email: "winner31@example.test",
      roles: ["PLAYER"],
      status: "ACTIVE",
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-10-20T00:00:00Z",
      walletCurrency: "ETB",
      walletAvailableBalance: "100.00",
      walletPromotionalBalance: "10.00",
      walletTransactionCount: 5,
      depositClaimCount: 1,
      supportTicketCount: 0,
      ticketCount: 2,
      winClaimCount: 1,
    });
    adminWinClaimDetailMock.mockResolvedValue({
      id: 12,
      ticketId: 44,
      sessionId: 8,
      claimantId: 31,
      claimedPattern: "FULL_HOUSE",
      status: "APPROVED",
      serverValidationPassed: true,
      serverValidationCode: "VALID",
      serverValidationMessage: "Claim verified",
      drawSequenceSnapshot: 35,
      calledNumbersSnapshot: [1, 2, 3],
      evidenceJson: null,
      operationId: "claim-op-12",
      reviewNote: "Reviewed",
      reviewedBy: 1,
      reviewedAt: "2026-10-20T12:00:00Z",
      entitlementId: 24,
      createdAt: "2026-10-20T11:00:00Z",
      updatedAt: "2026-10-20T12:00:00Z",
    });
    adminSessionDetailMock.mockResolvedValue({
      id: 8,
      roomId: 3,
      roomName: "Evening Room",
      sessionName: "Evening Draw",
      state: "SETTLED",
      scheduledStartAt: "2026-10-20T10:00:00Z",
      startedAt: "2026-10-20T10:05:00Z",
      endedAt: "2026-10-20T10:30:00Z",
      ticketPrice: "25.00",
      currency: "ETB",
      sessionCapacity: 100,
      maxTicketsPerPlayer: 5,
      ticketSalesCloseOnSessionStart: true,
      allowMultipleWinnersPerPrize: false,
      configuredPatterns: ["FULL_HOUSE"],
      configuredPrizes: [],
      ticketCount: 44,
      drawCount: 35,
      createdAt: "2026-10-20T09:00:00Z",
      updatedAt: "2026-10-20T10:30:00Z",
    });
    adminTicketDetailMock.mockResolvedValue({
      id: 44,
      sessionId: 8,
      ownerId: 31,
      publicTicketCode: "BT-44-ABC",
      operationId: "ticket-op-44",
      grid: [
        [1, 2, 3],
        [4, 5, 6],
      ],
      purchasedAt: "2026-10-20T09:30:00Z",
    });

    render(<PrizeEntitlementsPage />, { wrapper: createWrapper });
    const row = await screen.findByTestId("entitlement-row-24");

    await user.click(within(row).getByRole("button", { name: "View player 31 details" }));
    expect(await screen.findByText("winner31@example.test")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Close" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());

    await user.click(within(row).getByRole("button", { name: "View claim 12 details" }));
    expect(await screen.findByText("Claim verified")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Close" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());

    await user.click(within(row).getByRole("button", { name: "View session 8 details" }));
    expect(await screen.findByText("Evening Draw")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Close" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());

    await user.click(within(row).getByRole("button", { name: "View ticket 44 details" }));
    expect(await screen.findByText("BT-44-ABC")).toBeInTheDocument();

    expect(playerDetailMock).toHaveBeenCalledWith(31);
    expect(adminWinClaimDetailMock).toHaveBeenCalledWith(12);
    expect(adminSessionDetailMock).toHaveBeenCalledWith(8);
    expect(adminTicketDetailMock).toHaveBeenCalledWith(44);
  });
});
