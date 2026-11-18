import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { walletTransferDetail, walletTransfers } from "../../api/admin";
import i18n from "../../i18n";
import { WalletTransfersPage } from "./WalletTransfersPage";
import { WalletTransferDetailPage } from "./WalletTransferDetailPage";

vi.mock("../../api/admin", () => ({ walletTransferDetail: vi.fn(), walletTransfers: vi.fn() }));

const walletTransfersMock = vi.mocked(walletTransfers);
const walletTransferDetailMock = vi.mocked(walletTransferDetail);

function createWrapper({ children }: PropsWithChildren) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/wallet/transfers"]}>
        <Routes>
          <Route path="/wallet/transfers" element={children} />
          <Route path="/wallet/transfers/:transferId" element={<WalletTransferDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("WalletTransfersPage", () => {
  afterEach(cleanup);

  beforeEach(async () => {
    await i18n.changeLanguage("en");
    walletTransfersMock.mockReset();
    walletTransferDetailMock.mockReset();
    walletTransferDetailMock.mockResolvedValue({
      senderDisplayName: "Mekdes Sender",
      recipientDisplayName: "Abebe Receiver",
      transfer: {
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
      ledgerEntries: [
        {
          id: 51,
          walletId: 12,
          walletOwnerId: 12,
          walletOwnerDisplayName: "Mekdes Sender",
          operationId: "transfer-operation-24",
          transactionType: "WALLET_TRANSFER_DEBIT",
          amount: "25.00",
          balanceBefore: "100.00",
          balanceAfter: "75.00",
          currency: "ETB",
          referenceType: "WALLET_TRANSFER",
          referenceId: 24,
          createdAt: "2026-10-20T15:07:22Z",
        },
      ],
    });
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
    expect(screen.queryByText("Shared ticket funds")).not.toBeInTheDocument();
    expect(screen.getByText("Sender")).toBeInTheDocument();
    expect(screen.getByText("Receiver")).toBeInTheDocument();
    expect(within(row).getByText("251911000012")).toBeInTheDocument();
    expect(within(row).getByText("251911000018")).toBeInTheDocument();
    expect(row).not.toHaveTextContent("#24");
    expect(row).not.toHaveTextContent("#12");
    expect(row).not.toHaveTextContent("#18");
    await user.click(within(row).getByRole("button", { name: "View details for transfer 24" }));

    expect(await screen.findByText("Transfer details")).toBeInTheDocument();
    expect(screen.getByText("transfer-operation-24")).toBeInTheDocument();
    expect(await screen.findByText("Wallet transfer debit")).toBeInTheDocument();
    expect(walletTransferDetailMock).toHaveBeenCalledWith(24);
    expect(await screen.findByText("Mekdes Sender · 251911000012")).toBeInTheDocument();
    expect(screen.getByText("Abebe Receiver · 251911000018")).toBeInTheDocument();
    expect(screen.getByText("Mekdes Sender", { exact: true })).toBeInTheDocument();
    expect(walletTransfersMock).toHaveBeenCalledWith(undefined, undefined, undefined, undefined, 0, 20);
  });

  it("does not display server internals when transfer detail fails", async () => {
    const user = userEvent.setup();
    walletTransferDetailMock.mockRejectedValue(new Error("SQL host and query text"));
    render(<WalletTransfersPage />, { wrapper: createWrapper });

    const row = await screen.findByTestId("wallet-transfer-row-24");
    await user.click(within(row).getByRole("button", { name: "View details for transfer 24" }));

    expect(await screen.findByText("Transfer details could not be loaded. Please try again.")).toBeInTheDocument();
    expect(screen.queryByText("SQL host and query text")).not.toBeInTheDocument();
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
