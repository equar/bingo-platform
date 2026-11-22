import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { approveDeposit, deposit, rejectDeposit, verifyDeposit } from "../../api/admin";
import { ApiError } from "../../api/types";
import { clearSession, writeSession } from "../../api/session";
import i18n from "../../i18n";
import { DepositDetailPage } from "./DepositDetailPage";

vi.mock("../../api/admin", () => ({
  approveDeposit: vi.fn(),
  deposit: vi.fn(),
  rejectDeposit: vi.fn(),
  verifyDeposit: vi.fn(),
}));

const approveDepositMock = vi.mocked(approveDeposit);
const depositMock = vi.mocked(deposit);
const rejectDepositMock = vi.mocked(rejectDeposit);
const verifyDepositMock = vi.mocked(verifyDeposit);

function depositResponse(status = "PENDING") {
  return {
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
  } as Awaited<ReturnType<typeof deposit>>;
}

function verificationResponse(outcome: string) {
  return { outcome } as Awaited<ReturnType<typeof verifyDeposit>>;
}

function writeSessionWithRoles(roles: string[]) {
  writeSession({
    accessToken: "test-token",
    tokenType: "Bearer",
    user: {
      id: 5,
      displayName: "Deposit Reviewer",
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
      <MemoryRouter initialEntries={["/deposits/32"]}>
        <Routes>
          <Route path="/deposits/:depositId" element={children} />
          <Route path="/deposits" element={<div data-testid="deposit-list" />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("DepositDetailPage", () => {
  afterEach(() => {
    cleanup();
    clearSession();
  });

  beforeEach(async () => {
    await i18n.changeLanguage("en");
    clearSession();
    writeSessionWithRoles(["SUPPORT"]);
    depositMock.mockReset().mockResolvedValue(depositResponse());
    verifyDepositMock.mockReset().mockResolvedValue(verificationResponse("MATCHED"));
    approveDepositMock.mockReset().mockResolvedValue(depositResponse("APPROVED"));
    rejectDepositMock.mockReset().mockResolvedValue(depositResponse("REJECTED"));
  });

  it("verifies a matched payment without approving it", async () => {
    const user = userEvent.setup();
    render(<DepositDetailPage />, { wrapper: createWrapper });

    await user.type(await screen.findByRole("textbox", { name: "Full payment reference" }), "TXN-8842");
    await user.click(screen.getByRole("button", { name: "Verify payment reference" }));

    await waitFor(() => expect(verifyDepositMock).toHaveBeenCalledWith(32, "TXN-8842", expect.any(String)));
    expect(approveDepositMock).not.toHaveBeenCalled();
    expect(await screen.findByText("Payment reference matched. A different authorized staff member must approve this deposit.")).toBeInTheDocument();
  });

  it("lets a different authorized staff member approve a verified deposit", async () => {
    const user = userEvent.setup();
    depositMock.mockResolvedValue({ ...depositResponse(), verifiedBy: 6 });
    render(<DepositDetailPage />, { wrapper: createWrapper });

    await user.click(await screen.findByRole("button", { name: "Approve and credit deposit" }));

    expect(approveDepositMock).toHaveBeenCalledWith(32, {
      reviewNote: "SUPPORT_REFERENCE_MATCHED",
      idempotencyKey: expect.any(String),
    });
  });

  it("explains that a verifier cannot approve their own deposit", async () => {
    depositMock.mockResolvedValue({ ...depositResponse(), verifiedBy: 5 });
    render(<DepositDetailPage />, { wrapper: createWrapper });

    expect(await screen.findByText("You verified this deposit. A different authorized staff member must approve it.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve and credit deposit" })).not.toBeInTheDocument();
  });

  it("allows support to reject a deposit with a reason", async () => {
    const user = userEvent.setup();
    render(<DepositDetailPage />, { wrapper: createWrapper });

    await user.click(await screen.findByRole("combobox", { name: "Rejection reason" }));
    await user.click(await screen.findByRole("option", { name: "PAYMENT_REFERENCE_NOT_FOUND" }));
    await user.click(screen.getByRole("button", { name: "Reject" }));

    await waitFor(() =>
      expect(rejectDepositMock).toHaveBeenCalledWith(32, {
        reviewNote: "PAYMENT_REFERENCE_NOT_FOUND",
        idempotencyKey: expect.any(String),
      }),
    );
  });

  it("prevents duplicate verification requests while the action is pending", async () => {
    const user = userEvent.setup();
    let resolveVerification!: (value: Awaited<ReturnType<typeof verifyDeposit>>) => void;
    verifyDepositMock.mockReturnValue(new Promise((resolve) => { resolveVerification = resolve; }));
    render(<DepositDetailPage />, { wrapper: createWrapper });

    await user.type(await screen.findByRole("textbox", { name: "Full payment reference" }), "TXN-8842");
    const verifyButton = screen.getByRole("button", { name: "Verify payment reference" });
    await user.click(verifyButton);
    await waitFor(() => expect(verifyButton).toBeDisabled());
    fireEvent.click(verifyButton);

    expect(verifyDepositMock).toHaveBeenCalledTimes(1);
    await act(async () => resolveVerification(verificationResponse("NO_MATCH")));
    expect(await screen.findByText("NO_MATCH")).toBeInTheDocument();
  });

  it("shows a safe message when verification fails", async () => {
    const user = userEvent.setup();
    verifyDepositMock.mockRejectedValue({
      response: { status: 403, data: { code: "ACCESS_DENIED", message: "Internal authorization detail" } },
    });
    render(<DepositDetailPage />, { wrapper: createWrapper });

    await user.type(await screen.findByRole("textbox", { name: "Full payment reference" }), "TXN-8842");
    await user.click(screen.getByRole("button", { name: "Verify payment reference" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "You don’t have permission to perform this action.",
    );
  });

  it("lets administrators verify rejected deposits without approving them", async () => {
    const user = userEvent.setup();
    writeSessionWithRoles(["ADMIN"]);
    depositMock.mockResolvedValue(depositResponse("REJECTED"));
    render(<DepositDetailPage />, { wrapper: createWrapper });

    await user.type(await screen.findByRole("textbox", { name: "Full payment reference" }), "TXN-8842");
    await user.click(screen.getByRole("button", { name: "Verify payment reference" }));

    await waitFor(() => expect(verifyDepositMock).toHaveBeenCalledWith(32, "TXN-8842", expect.any(String)));
    expect(approveDepositMock).not.toHaveBeenCalled();
    expect(await screen.findByText("Payment reference matched. A different authorized staff member must approve this deposit.")).toBeInTheDocument();
  });

  it("reuses the verification key after an uncertain response", async () => {
    const user = userEvent.setup();
    verifyDepositMock
      .mockRejectedValueOnce(new ApiError("unknown", { outcomeUnknown: true }))
      .mockResolvedValueOnce(verificationResponse("MATCHED"));
    render(<DepositDetailPage />, { wrapper: createWrapper });
    const reference = await screen.findByRole("textbox", { name: "Full payment reference" });
    await user.type(reference, "TXN-RETRY");
    await user.click(screen.getByRole("button", { name: "Verify payment reference" }));
    await waitFor(() => expect(reference).toBeDisabled());
    await user.click(screen.getByRole("button", { name: "Verify payment reference" }));
    await waitFor(() => expect(verifyDepositMock).toHaveBeenCalledTimes(2));
    expect(verifyDepositMock.mock.calls[1][2]).toBe(verifyDepositMock.mock.calls[0][2]);
  });
});
