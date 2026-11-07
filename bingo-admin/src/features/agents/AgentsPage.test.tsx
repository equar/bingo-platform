import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { adminAgents, adminFloatBankAccounts, adminFloatTopUpRequests, currentCommissionRule, floatTopUpVerificationHistory, type AdminAgentSummaryResponse } from "../../api/admin";
import i18n from "../../i18n";
import { AgentsPage } from "./AgentsPage";

vi.mock("../../api/admin", () => ({
  adminAgents: vi.fn(),
  adminFloatTopUpRequests: vi.fn(),
  adminFloatBankAccounts: vi.fn(),
  approveAgent: vi.fn(),
  commissionRule: vi.fn(),
  currentCommissionRule: vi.fn(),
  reviewFloatTopUpRequest: vi.fn(),
  createFloatBankAccount: vi.fn(),
  setFloatBankAccountActive: vi.fn(),
  recordSyntheticFloatBankStatement: vi.fn(),
  verifyFloatTopUp: vi.fn(),
  floatTopUpVerificationHistory: vi.fn(),
  postVerifiedFloatTopUp: vi.fn(),
  dailyFloatReconciliation: vi.fn(),
  suspendAgent: vi.fn(),
}));

const adminAgentsMock = vi.mocked(adminAgents);
const adminFloatTopUpRequestsMock = vi.mocked(adminFloatTopUpRequests);
const adminFloatBankAccountsMock = vi.mocked(adminFloatBankAccounts);
const currentCommissionRuleMock = vi.mocked(currentCommissionRule);
const floatTopUpVerificationHistoryMock = vi.mocked(floatTopUpVerificationHistory);

function agentSummary(agentId: number, displayName: string): AdminAgentSummaryResponse {
  return {
    agentId, userId: agentId + 100, displayName, normalizedPhone: "251911000001", status: "APPROVED",
    city: "Addis", territory: "Central", createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
    ticketSalesCount: 0, floatTransactionCount: 0, topUpRequestCount: 0, payoutVerificationCount: 0,
  };
}

function createWrapper({ children }: PropsWithChildren) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe("AgentsPage float top-up review", () => {
  afterEach(cleanup);

  beforeEach(async () => {
    await i18n.changeLanguage("en");
    adminAgentsMock.mockReset().mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    });
    adminFloatBankAccountsMock.mockReset().mockResolvedValue([]);
    floatTopUpVerificationHistoryMock.mockReset().mockResolvedValue([]);
    adminFloatTopUpRequestsMock.mockReset().mockResolvedValue({
      content: [{
        id: 17, agentId: 3, agentDisplayName: "Test Agent", agentPhoneNumber: "251911000001",
        requestedByUserId: 11, amount: "25.00", currency: "ETB", status: "PENDING", operationId: "op",
        reviewNote: null, reviewedByUserId: null, reviewedAt: null, createdAt: "2026-10-23T08:00:00Z",
        updatedAt: "2026-10-23T08:00:00Z", submissionNote: "QA-REF-17", transferReference: "QA-REF-17",
        bankNameSnapshot: "QA Bank", maskedAccountSnapshot: "••••0001",
      }], page: 0, size: 10, totalElements: 1, totalPages: 1, first: true, last: true,
    });
    currentCommissionRuleMock.mockReset().mockResolvedValue({
      id: null, versionNumber: 0, commissionRateBps: 0, effectiveFrom: "1970-01-01T00:00:00Z",
      active: false, createdAt: null, updatedAt: null,
    });
  });

  it("shows the queued transfer and opens its independent review details", async () => {
    const user = userEvent.setup();
    render(<AgentsPage />, { wrapper: createWrapper });
    await screen.findByText("Float top-up review");
    expect(await screen.findByText("QA-REF-17")).toBeInTheDocument();
    const requestRow = screen.getAllByRole("row").find((row) => row.textContent?.includes("QA-REF-17"));
    expect(requestRow).toHaveTextContent("Test Agent");
    expect(requestRow).toHaveTextContent("251911000001");

    expect(screen.getByRole("button", { name: "Post verified request (QA simulation)" })).toBeDisabled();
    await user.click(within(requestRow!).getByRole("button", { name: "Details" }));
    expect(screen.getByRole("spinbutton", { name: "Request id" })).toHaveValue(17);
    const requestDetails = screen.getAllByRole("alert").find(
      (alert) => alert.textContent?.includes("25.00 ETB") && alert.textContent?.includes("QA Bank"),
    );
    expect(requestDetails).toBeDefined();
    await user.type(screen.getByRole("textbox", { name: "Review note" }), "QA rejection");
    expect(screen.getByRole("button", { name: "Review request (reject, no credit)" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Post verified request (QA simulation)" })).toBeDisabled();
  });

  it("requests the next agent page from the backend", async () => {
    adminAgentsMock.mockResolvedValue({
      content: [], page: 0, size: 20, totalElements: 21, totalPages: 2, first: true, last: false,
    });
    const user = userEvent.setup();
    render(<AgentsPage />, { wrapper: createWrapper });
    await screen.findByRole("button", { name: "Go to next page" });
    await user.click(screen.getByRole("button", { name: "Go to next page" }));
    await waitFor(() => expect(adminAgentsMock).toHaveBeenCalledWith(1, 20, "", ""));
  });

  it("resets the page when page size changes", async () => {
    adminAgentsMock.mockResolvedValue({
      content: [], page: 0, size: 20, totalElements: 101, totalPages: 6, first: true, last: false,
    });
    const user = userEvent.setup();
    render(<AgentsPage />, { wrapper: createWrapper });
    await user.click(await screen.findByRole("button", { name: "Go to next page" }));
    await waitFor(() => expect(adminAgentsMock).toHaveBeenCalledWith(1, 20, "", ""));
    await user.click(screen.getByRole("combobox", { name: "Rows per page:" }));
    await user.click(await screen.findByRole("option", { name: "10" }));
    await waitFor(() => expect(adminAgentsMock).toHaveBeenCalledWith(0, 10, "", ""));
  });

  it("recovers to the last available page when a page becomes out of range", async () => {
    adminAgentsMock
      .mockResolvedValueOnce({ content: [], page: 0, size: 20, totalElements: 21, totalPages: 2, first: true, last: false })
      .mockResolvedValueOnce({ content: [], page: 1, size: 20, totalElements: 20, totalPages: 1, first: false, last: true })
      .mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 20, totalPages: 1, first: true, last: true });
    const user = userEvent.setup();
    render(<AgentsPage />, { wrapper: createWrapper });
    await user.click(await screen.findByRole("button", { name: "Go to next page" }));
    await waitFor(() => expect(adminAgentsMock).toHaveBeenCalledTimes(3));
    expect(adminAgentsMock).toHaveBeenNthCalledWith(3, 0, 20, "", "");
  });

  it("does not show a late response from an older query after search changes", async () => {
    let resolveStalePage!: (response: Awaited<ReturnType<typeof adminAgents>>) => void;
    const stalePage = new Promise<Awaited<ReturnType<typeof adminAgents>>>((resolve) => {
      resolveStalePage = resolve;
    });
    adminAgentsMock.mockImplementation((page, _size, query) => {
      if (page === 1) return stalePage;
      if (query === "Rob") {
        return Promise.resolve({
          content: [agentSummary(2, "Rob Agent")], page: 0, size: 20, totalElements: 1, totalPages: 1,
          first: true, last: true,
        });
      }
      return Promise.resolve({
        content: [agentSummary(1, "Initial Agent")], page: 0, size: 20, totalElements: 21, totalPages: 2,
        first: true, last: false,
      });
    });
    const user = userEvent.setup();
    render(<AgentsPage />, { wrapper: createWrapper });
    await user.click(await screen.findByRole("button", { name: "Go to next page" }));
    await waitFor(() => expect(adminAgentsMock).toHaveBeenCalledWith(1, 20, "", ""));
    await user.type(screen.getByRole("textbox", { name: "Search agents" }), "Rob");
    expect(await screen.findByText("Rob Agent")).toBeInTheDocument();
    resolveStalePage({
      content: [agentSummary(3, "Stale Agent")], page: 1, size: 20, totalElements: 21, totalPages: 2,
      first: false, last: true,
    });
    await waitFor(() => expect(screen.getByText("Rob Agent")).toBeInTheDocument());
    expect(screen.queryByText("Stale Agent")).not.toBeInTheDocument();
  });

  it("loads the persisted current commission rule", async () => {
    currentCommissionRuleMock.mockResolvedValue({
      id: 8, versionNumber: 3, commissionRateBps: 0, effectiveFrom: "2000-01-01T00:00:00Z",
      active: false, createdAt: "2000-01-01T00:00:00Z", updatedAt: "2000-01-01T00:00:00Z",
    });
    render(<AgentsPage />, { wrapper: createWrapper });
    expect(await screen.findByText("Current version 3 · Inactive")).toBeInTheDocument();
    expect(screen.getByRole("spinbutton", { name: "Commission rate (bps)" })).toHaveValue(0);
    expect(screen.getByRole("checkbox", { name: "Active" })).not.toBeChecked();
  });
});
