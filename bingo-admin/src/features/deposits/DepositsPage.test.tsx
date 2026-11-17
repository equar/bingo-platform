import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { deposits } from "../../api/admin";
import { clearSession, writeSession } from "../../api/session";
import i18n from "../../i18n";
import { DepositsPage } from "./DepositsPage";

vi.mock("../../api/admin", () => ({
  approveDeposit: vi.fn(),
  deposits: vi.fn(),
  rejectDeposit: vi.fn(),
}));

const depositsMock = vi.mocked(deposits);

function depositResponse(status = "PENDING") {
  return {
    content: [
      {
        id: 32,
        operationId: "deposit-operation-32",
        playerId: 14,
        playerDisplayName: "Player Name",
        playerPhoneNumber: "+251911000014",
        submittedCashAmount: "100.00",
        currency: "ETB",
        conversionRateSnapshot: "1.000000",
        calculatedCreditAmount: "100.00",
        normalizedTelebirrReference: "TXN-8842",
        status,
        submittedAt: "2026-10-20T15:07:22Z",
        reviewedBy: null,
        reviewedAt: null,
        reviewNote: null,
        verifiedBy: null,
        verifiedAt: null,
        paymentInstructions: null,
      },
    ],
    page: 0,
    size: 20,
    totalElements: 42,
    totalPages: 3,
    first: true,
    last: false,
  };
}

function writeSessionWithRoles(roles: string[]) {
  writeSession({
    accessToken: "test-token",
    tokenType: "Bearer",
    user: {
      id: 5,
      displayName: "Review Admin",
      phoneNumber: "251911000005",
      roles,
      status: "ACTIVE",
      createdAt: "2026-01-01T00:00:00.000Z",
      updatedAt: "2026-01-01T00:00:00.000Z",
      preferredLanguage: "en",
    },
  });
}

function createWrapper({ children }: PropsWithChildren) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

function createDepositRoutesWrapper({ children }: PropsWithChildren) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/deposits"]}>
        <Routes>
          <Route path="/deposits" element={children} />
          <Route path="/deposits/:depositId" element={<div data-testid="deposit-detail-route" />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

function createPendingDepositsWrapper({ children }: PropsWithChildren) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/deposits?status=PENDING"]}>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe("DepositsPage", () => {
  afterEach(() => {
    cleanup();
    clearSession();
  });

  beforeEach(async () => {
    await i18n.changeLanguage("en");
    clearSession();
    writeSessionWithRoles(["ADMIN"]);
    depositsMock.mockReset();
    depositsMock.mockResolvedValue(depositResponse());
  });

  it("shows deposit and player details and links to the detail page", async () => {
    render(<DepositsPage />, { wrapper: createWrapper });

    expect(await screen.findByTestId("deposit-row-32")).toBeInTheDocument();
    expect(screen.getByText("TXN-8842")).toBeInTheDocument();
    expect(screen.getByText("Player Name")).toBeInTheDocument();
    expect(screen.getByText("+251911000014")).toBeInTheDocument();
    expect(screen.getByTestId("deposit-details-32")).toBeInTheDocument();
    expect(screen.queryByTestId("deposit-verify-32")).not.toBeInTheDocument();
    expect(screen.queryByTestId("deposit-review-32")).not.toBeInTheDocument();
    expect(depositsMock).toHaveBeenCalledWith(0, 20, undefined, undefined);
  });

  it("shows masked references and verification navigation to support", async () => {
    writeSessionWithRoles(["SUPPORT"]);
    render(<DepositsPage />, { wrapper: createWrapper });

    await screen.findByTestId("deposit-row-32");

    expect(screen.getByText("****8842")).toBeInTheDocument();
    expect(screen.getByTestId("deposit-verify-32")).toBeInTheDocument();
    expect(screen.queryByTestId("deposit-reject-32")).not.toBeInTheDocument();
  });

  it("displays only canonical deposit statuses", async () => {
    depositsMock.mockResolvedValue(depositResponse("PENDING"));
    const user = userEvent.setup();
    render(<DepositsPage />, { wrapper: createWrapper });

    expect(await screen.findByText("PENDING")).toBeInTheDocument();
    expect(screen.queryByText("VERIFIED")).not.toBeInTheDocument();
    await user.click(screen.getByRole("combobox", { name: "Status" }));

    expect(
      screen.getAllByRole("option").map((option) => option.textContent),
    ).toEqual(["All statuses", "PENDING", "APPROVED", "REJECTED"]);
  });

  it("routes support to deposit details for verification and review actions", async () => {
    writeSessionWithRoles(["SUPPORT"]);
    const user = userEvent.setup();
    render(<DepositsPage />, { wrapper: createDepositRoutesWrapper });

    await user.click(await screen.findByTestId("deposit-verify-32"));
    expect(await screen.findByTestId("deposit-detail-route")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Reject" })).not.toBeInTheDocument();
  });

  it("lets administrators inspect rejected deposits as read-only records", async () => {
    depositsMock.mockResolvedValue(depositResponse("REJECTED"));
    const user = userEvent.setup();
    render(<DepositsPage />, { wrapper: createWrapper });

    await screen.findByTestId("deposit-row-32");

    await user.click(screen.getByTestId("deposit-details-32"));

    expect(
      await screen.findByRole("heading", { name: "Deposit details #32" }),
    ).toBeInTheDocument();
    expect(screen.queryByTestId("deposit-verify-32")).not.toBeInTheDocument();
  });

  it("uses the pending status when opened from the dashboard review metric", async () => {
    render(<DepositsPage />, { wrapper: createPendingDepositsWrapper });

    await screen.findByTestId("deposit-row-32");
    expect(depositsMock).toHaveBeenCalledWith(0, 20, undefined, "PENDING");
  });

  it("passes search and status filters to the server", async () => {
    const user = userEvent.setup();
    render(<DepositsPage />, { wrapper: createWrapper });

    await screen.findByTestId("deposit-row-32");
    await user.type(
      screen.getByRole("textbox", { name: "Search deposit, player, payment reference, or operation" }),
      "TXN",
    );
    await waitFor(() => expect(depositsMock).toHaveBeenLastCalledWith(0, 20, "TXN", undefined));

    await user.click(screen.getByRole("combobox", { name: "Status" }));
    await user.click(await screen.findByRole("option", { name: "APPROVED" }));
    await waitFor(() => expect(depositsMock).toHaveBeenLastCalledWith(0, 20, "TXN", "APPROVED"));
  });

  it("requests the next server page", async () => {
    const user = userEvent.setup();
    render(<DepositsPage />, { wrapper: createWrapper });

    await screen.findByTestId("deposit-row-32");
    await user.click(screen.getByRole("button", { name: "Go to next page" }));

    await waitFor(() => expect(depositsMock).toHaveBeenLastCalledWith(1, 20, undefined, undefined));
  });
});
