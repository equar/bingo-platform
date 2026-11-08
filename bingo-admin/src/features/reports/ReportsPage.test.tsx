import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ReportsPage } from "./ReportsPage";
import i18n from "../../i18n";
import { clearSession, writeSession } from "../../api/session";

const reportMocks = vi.hoisted(() => ({
  auditActivityReport: vi.fn(),
  depositReport: vi.fn(),
  playerVerificationReport: vi.fn(),
  prizeEntitlementsReport: vi.fn(),
  sessionActivityReport: vi.fn(),
  supportTicketReport: vi.fn(),
}));
vi.mock("../../api/admin", () => reportMocks);

function createWrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

function page<T>(content: T[]) {
  return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1, first: true, last: true };
}

describe("ReportsPage", () => {
  afterEach(() => {
    cleanup();
    clearSession();
  });

  beforeEach(async () => {
    await i18n.changeLanguage("en");
    vi.clearAllMocks();
    writeSession({
      accessToken: "test-token",
      tokenType: "Bearer",
      user: {
        id: 1,
        displayName: "Report Tester",
        email: null,
        phoneNumber: null,
        roles: ["ADMIN"],
        status: "ACTIVE",
        createdAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z",
      },
    });
    reportMocks.auditActivityReport.mockResolvedValue(page([]));
    reportMocks.depositReport.mockResolvedValue(page([{
      id: 21,
      playerId: 7,
      submittedCashAmount: "100.00",
      calculatedCreditAmount: "100.00",
      currency: "ETB",
      status: "APPROVED",
      submittedAt: "2026-11-05T08:00:00Z",
      verifiedAt: "2026-11-05T08:10:00Z",
      reviewedAt: "2026-11-05T08:12:00Z",
    }]));
    reportMocks.playerVerificationReport.mockResolvedValue(page([]));
    reportMocks.prizeEntitlementsReport.mockResolvedValue(page([{
      id: 88,
      winClaimId: 31,
      sessionId: 14,
      ticketId: 708,
      claimantId: 72,
      prizeType: "FULL_HOUSE",
      settlementPath: "WALLET_CREDIT",
      status: "SETTLED",
      prizeAmount: "500.00",
      currency: "ETB",
      operationId: "op-prize-88",
      createdAt: "2026-11-05T08:00:00Z",
      settledAt: "2026-11-05T08:12:00Z",
      settledBy: 3,
      settlementReference: "PAY-88",
      settlementNote: null,
    }]));
    reportMocks.sessionActivityReport.mockResolvedValue(page([]));
    reportMocks.supportTicketReport.mockResolvedValue(page([{
      id: 21,
      ticketNumber: "ST-21",
      requesterUserId: 7,
      subject: "Deposit pending",
      category: "WALLET",
      priority: "HIGH",
      status: "OPEN",
      assignedToUserId: 3,
      linkedEntityType: "DEPOSIT",
      linkedEntityId: 8,
      createdAt: "2026-11-05T08:00:00Z",
      updatedAt: "2026-11-05T08:10:00Z",
    }]));
  });

  it("generates and previews the date-filtered support ticket report", async () => {
    const user = userEvent.setup();
    render(<ReportsPage />, { wrapper: createWrapper });

    await user.click(screen.getByRole("button", { name: "Generate report" }));

    expect(await screen.findByText("ST-21")).toBeInTheDocument();
    expect(screen.getByText("Deposit pending")).toBeInTheDocument();
    expect(reportMocks.supportTicketReport).toHaveBeenCalledWith(
      expect.any(String),
      expect.any(String),
      0,
      100,
      "",
      undefined,
    );
    expect(screen.getByRole("button", { name: "Download CSV" })).toBeEnabled();
  });

  it("generates a searchable, status-filtered prize entitlement report", async () => {
    const user = userEvent.setup();
    render(<ReportsPage />, { wrapper: createWrapper });

    await user.click(screen.getByRole("combobox", { name: "Report type" }));
    await user.click(await screen.findByText("Prize entitlement activity"));
    await user.type(screen.getByRole("textbox", { name: "Search" }), "72");
    await user.click(screen.getByRole("combobox", { name: "Status" }));
    await user.click(await screen.findByText("SETTLED"));
    await user.click(screen.getByRole("button", { name: "Generate report" }));

    expect(await screen.findByText("op-prize-88")).toBeInTheDocument();
    expect(reportMocks.prizeEntitlementsReport).toHaveBeenCalledWith(
      expect.any(String),
      expect.any(String),
      0,
      100,
      "72",
      "SETTLED",
    );
    expect(screen.getByRole("heading", { name: "Prize entitlement activity", level: 6 })).toBeInTheDocument();
  });
});
