import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { approveDeposit, deposits, rejectDeposit } from "../../api/admin";
import { DepositsPage } from "./DepositsPage";

vi.mock("../../api/admin", () => ({
  approveDeposit: vi.fn(),
  deposits: vi.fn(),
  rejectDeposit: vi.fn(),
}));

const approveDepositMock = vi.mocked(approveDeposit);
const depositsMock = vi.mocked(deposits);
const rejectDepositMock = vi.mocked(rejectDeposit);

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
    approveDepositMock.mockReset().mockResolvedValue({} as Awaited<ReturnType<typeof approveDeposit>>);
    rejectDepositMock.mockReset().mockResolvedValue({} as Awaited<ReturnType<typeof rejectDeposit>>);
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

  it("approves and rejects pending deposits with a review reason", async () => {
    const user = userEvent.setup();
    render(<DepositsPage />, { wrapper: createWrapper });
    await screen.findByTestId("deposit-row-32");

    await user.click(screen.getByTestId("deposit-approve-32"));
    await user.type(screen.getByRole("textbox", { name: "Review note" }), "Payment verified");
    await user.click(screen.getByRole("button", { name: "Confirm" }));
    await waitFor(() =>
      expect(approveDepositMock).toHaveBeenCalledWith(32, {
        reviewNote: "Payment verified",
        idempotencyKey: expect.any(String),
      }),
    );

    await screen.findByTestId("deposit-row-32");
    await user.click(screen.getByTestId("deposit-reject-32"));
    await user.type(screen.getByRole("textbox", { name: "Rejection reason" }), "Reference not found");
    await user.click(screen.getByRole("button", { name: "Confirm" }));
    await waitFor(() =>
      expect(rejectDepositMock).toHaveBeenCalledWith(32, {
        reviewNote: "Reference not found",
        idempotencyKey: expect.any(String),
      }),
    );
  });

  it("prevents duplicate review requests while the deposit action is pending", async () => {
    const user = userEvent.setup();
    approveDepositMock.mockReturnValue(new Promise(() => {}));
    render(<DepositsPage />, { wrapper: createWrapper });
    await screen.findByTestId("deposit-row-32");

    await user.click(screen.getByTestId("deposit-approve-32"));
    await user.type(screen.getByRole("textbox", { name: "Review note" }), "Payment verified");
    const confirmButton = screen.getByRole("button", { name: "Confirm" });
    await user.click(confirmButton);
    await waitFor(() => expect(confirmButton).toBeDisabled());
    fireEvent.click(confirmButton);

    expect(approveDepositMock).toHaveBeenCalledTimes(1);
  });

  it("shows a deposit review API failure", async () => {
    const user = userEvent.setup();
    approveDepositMock.mockRejectedValue(new Error("Permission denied"));
    render(<DepositsPage />, { wrapper: createWrapper });
    await screen.findByTestId("deposit-row-32");

    await user.click(screen.getByTestId("deposit-approve-32"));
    await user.type(screen.getByRole("textbox", { name: "Review note" }), "Payment verified");
    await user.click(screen.getByRole("button", { name: "Confirm" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Permission denied");
  });
});
