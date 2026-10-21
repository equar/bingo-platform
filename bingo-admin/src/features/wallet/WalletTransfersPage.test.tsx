import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { walletTransfers } from "../../api/admin";
import { WalletTransfersPage } from "./WalletTransfersPage";

vi.mock("../../api/admin", () => ({ walletTransfers: vi.fn() }));

const walletTransfersMock = vi.mocked(walletTransfers);

function createWrapper({ children }: PropsWithChildren) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe("WalletTransfersPage", () => {
  afterEach(cleanup);

  beforeEach(() => {
    walletTransfersMock.mockReset();
    walletTransfersMock.mockResolvedValue({
      content: [
        {
          id: 24,
          operationId: "transfer-operation-24",
          senderId: 12,
          senderPhoneNumber: "251911000012",
          recipientId: 18,
          recipientPhoneNumber: "251911000018",
          amount: "25.00",
          currency: "ETB",
          note: "Shared ticket funds",
          status: "COMPLETED",
          senderBalanceBefore: "100.00",
          senderBalanceAfter: "75.00",
          recipientBalanceBefore: "10.00",
          recipientBalanceAfter: "35.00",
          createdAt: "2026-10-20T15:07:22Z",
          completedAt: "2026-10-20T15:07:22Z",
          failureReason: null,
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

  it("renders a selectable transfer row and its detail panel", async () => {
    const user = userEvent.setup();
    render(<WalletTransfersPage />, { wrapper: createWrapper });

    const row = await screen.findByTestId("wallet-transfer-row-24");
    expect(screen.getByText("Shared ticket funds")).toBeInTheDocument();
    await user.click(row);

    expect(screen.getByText("Transfer detail")).toBeInTheDocument();
    expect(screen.getByText("Operation: transfer-operation-24")).toBeInTheDocument();
    expect(walletTransfersMock).toHaveBeenCalledWith(undefined, undefined, undefined, undefined, 0, 20);
  });

  it("passes search and transfer filters to the server", async () => {
    const user = userEvent.setup();
    render(<WalletTransfersPage />, { wrapper: createWrapper });

    await screen.findByTestId("wallet-transfer-row-24");
    await user.type(screen.getByRole("textbox", { name: "Search sender, recipient, note, or operation" }), "shared");
    await waitFor(() =>
      expect(walletTransfersMock).toHaveBeenLastCalledWith("shared", undefined, undefined, undefined, 0, 20),
    );

    await user.click(screen.getByRole("combobox", { name: "Status" }));
    await user.click(await screen.findByRole("option", { name: "Completed" }));
    await waitFor(() =>
      expect(walletTransfersMock).toHaveBeenLastCalledWith("shared", "COMPLETED", undefined, undefined, 0, 20),
    );

    await user.type(screen.getByRole("textbox", { name: "Sender phone" }), "251911000012");
    await waitFor(() =>
      expect(walletTransfersMock).toHaveBeenLastCalledWith("shared", "COMPLETED", "251911000012", undefined, 0, 20),
    );

    await user.type(screen.getByRole("textbox", { name: "Recipient phone" }), "251911000018");
    await waitFor(() =>
      expect(walletTransfersMock).toHaveBeenLastCalledWith(
        "shared",
        "COMPLETED",
        "251911000012",
        "251911000018",
        0,
        20,
      ),
    );
  });

  it("requests the next server page", async () => {
    const user = userEvent.setup();
    render(<WalletTransfersPage />, { wrapper: createWrapper });

    await screen.findByTestId("wallet-transfer-row-24");
    await user.click(screen.getByRole("button", { name: "Go to next page" }));

    await waitFor(() =>
      expect(walletTransfersMock).toHaveBeenLastCalledWith(undefined, undefined, undefined, undefined, 1, 20),
    );
  });
});
