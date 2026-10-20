import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { adjustWallet, playerDeposits, playerDetail } from "../../api/admin";
import { PlayerDetailPage } from "./PlayerDetailPage";

vi.mock("../../api/admin", () => ({
  adjustWallet: vi.fn(),
  playerDeposits: vi.fn(),
  playerDetail: vi.fn(),
  playerSupportTickets: vi.fn(),
  playerTickets: vi.fn(),
  playerWalletTransactions: vi.fn(),
  playerWinClaims: vi.fn(),
}));

const adjustWalletMock = vi.mocked(adjustWallet);
const playerDepositsMock = vi.mocked(playerDeposits);
const playerDetailMock = vi.mocked(playerDetail);

const player = {
  id: 6,
  displayName: "Player Six",
  normalizedPhone: "251911000006",
  email: "player6@example.test",
  roles: ["PLAYER"],
  status: "ACTIVE",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-10-20T00:00:00Z",
  walletCurrency: "ETB",
  walletAvailableBalance: "125.00",
  walletPromotionalBalance: "20.00",
  walletTransactionCount: 19,
  depositClaimCount: 2,
  supportTicketCount: 1,
  ticketCount: 8,
  winClaimCount: 1,
};

function createWrapper({ children }: PropsWithChildren) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/players/6"]}>
        <Routes>
          <Route path="/players/:playerId" element={children} />
          <Route path="/players" element={<div>Players list destination</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("PlayerDetailPage", () => {
  afterEach(cleanup);

  beforeEach(() => {
    playerDetailMock.mockReset();
    adjustWalletMock.mockReset();
    playerDepositsMock.mockReset();
    playerDetailMock.mockResolvedValue(player);
    adjustWalletMock.mockResolvedValue({
      operationId: "adjustment-op-1",
      walletId: 16,
      playerId: 6,
      amount: "-12.50",
      balanceBefore: "125.00",
      balanceAfter: "112.50",
      reason: "Support correction (Reference: CASE-18)",
      createdAt: "2026-10-20T00:00:00Z",
    });
  });

  it("shows player contact, status, wallet balances, and activity totals", async () => {
    render(<PlayerDetailPage />, { wrapper: createWrapper });

    expect(await screen.findByText("player6@example.test")).toBeInTheDocument();
    expect(screen.getByText("125.00 ETB")).toBeInTheDocument();
    expect(screen.getByText("20.00 ETB")).toBeInTheDocument();
    expect(screen.getByText("19")).toBeInTheDocument();
    expect(screen.getByText("Player Six")).toBeInTheDocument();
  });

  it("keeps a back-to-players button available while the profile is loading", async () => {
    const user = userEvent.setup();
    playerDetailMock.mockReturnValue(new Promise(() => {}));
    render(<PlayerDetailPage />, { wrapper: createWrapper });

    await screen.findByText("Fetching player profile");
    await user.click(screen.getByRole("link", { name: "Back to players" }));

    expect(screen.getByText("Players list destination")).toBeInTheDocument();
  });

  it("opens the selected player's deposit history from the summary", async () => {
    const user = userEvent.setup();
    playerDepositsMock.mockResolvedValue({
      content: [
        {
          id: 61,
          operationId: "deposit-op-61",
          playerId: 6,
          submittedCashAmount: "50.00",
          currency: "ETB",
          conversionRateSnapshot: "1.00",
          calculatedCreditAmount: "50.00",
          normalizedTelebirrReference: "REF-PLAYER-6",
          status: "APPROVED",
          submittedAt: "2026-10-01T10:00:00Z",
          reviewedBy: 1,
          reviewedAt: "2026-10-01T11:00:00Z",
          reviewNote: "Verified",
          paymentInstructions: null,
        },
      ],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });

    render(<PlayerDetailPage />, { wrapper: createWrapper });
    await screen.findByText("Player Six");
    await user.click(screen.getByRole("button", { name: "View deposits details" }));

    expect(await screen.findByText("REF-PLAYER-6")).toBeInTheDocument();
    expect(playerDepositsMock).toHaveBeenCalledWith(6, 0, 10);
  });

  it("submits a debit as a negative amount and includes the reference in the audit reason", async () => {
    const user = userEvent.setup();
    render(<PlayerDetailPage />, { wrapper: createWrapper });

    await screen.findByText("Player Six");
    await user.click(screen.getByRole("button", { name: "Adjust wallet" }));
    await user.click(screen.getByRole("combobox", { name: "Direction" }));
    await user.click(await screen.findByRole("option", { name: "Debit" }));
    await user.type(screen.getByRole("spinbutton", { name: "Amount" }), "12.50");
    await user.type(screen.getByRole("textbox", { name: "Reason" }), "Support correction");
    await user.type(screen.getByRole("textbox", { name: "Reference" }), "CASE-18");
    await user.click(screen.getByRole("button", { name: "Review" }));
    await user.click(await screen.findByRole("button", { name: "Confirm adjustment" }));

    await waitFor(() =>
      expect(adjustWalletMock).toHaveBeenCalledWith(6, {
        amount: -12.5,
        currency: "ETB",
        reason: "Support correction (Reference: CASE-18)",
        idempotencyKey: expect.any(String),
      }),
    );
  });
});
