import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { adminAgents, adminFloatBankAccounts, adminFloatTopUpRequests, currentCommissionRule, floatTopUpVerificationHistory } from "../../api/admin";
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

  beforeEach(() => {
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
    expect(await screen.findByText(/Request #17: 25.00 ETB/)).toBeInTheDocument();
    await user.type(screen.getByRole("textbox", { name: "Review note" }), "QA rejection");
    expect(screen.getByRole("button", { name: "Review request (reject, no credit)" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Post verified request (QA simulation)" })).toBeDisabled();
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
