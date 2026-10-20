import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { deposits } from "../../api/admin";
import { DepositsPage } from "./DepositsPage";

vi.mock("../../api/admin", () => ({
  approveDeposit: vi.fn(),
  deposits: vi.fn(),
  rejectDeposit: vi.fn(),
}));

const depositsMock = vi.mocked(deposits);

function createWrapper({ children }: PropsWithChildren) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe("DepositsPage", () => {
  afterEach(cleanup);

  beforeEach(() => {
    depositsMock.mockReset();
    depositsMock.mockResolvedValue({
      content: [
        {
          id: 32,
          operationId: "deposit-operation-32",
          playerId: 14,
          submittedCashAmount: "100.00",
          currency: "ETB",
          conversionRateSnapshot: "1.000000",
          calculatedCreditAmount: "100.00",
          normalizedTelebirrReference: "TXN-8842",
          status: "PENDING",
          submittedAt: "2026-10-20T15:07:22Z",
          reviewedBy: null,
          reviewedAt: null,
          reviewNote: null,
          paymentInstructions: null,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 42,
      totalPages: 3,
      first: true,
      last: false,
    });
  });

  it("loads deposits into the table and retains pending review actions", async () => {
    render(<DepositsPage />, { wrapper: createWrapper });

    expect(await screen.findByTestId("deposit-row-32")).toBeInTheDocument();
    expect(screen.getByText("TXN-8842")).toBeInTheDocument();
    expect(screen.getByTestId("deposit-approve-32")).toBeEnabled();
    expect(screen.getByTestId("deposit-reject-32")).toBeEnabled();
    expect(depositsMock).toHaveBeenCalledWith(0, 20, undefined, undefined);
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
