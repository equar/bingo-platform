import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ReportsPage } from "./ReportsPage";
import i18n from "../../i18n";

const reportMocks = vi.hoisted(() => ({
  auditActivityReport: vi.fn(),
  depositReport: vi.fn(),
  playerVerificationReport: vi.fn(),
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
  afterEach(cleanup);

  beforeEach(async () => {
    await i18n.changeLanguage("en");
    vi.clearAllMocks();
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
    );
    expect(screen.getByRole("button", { name: "Download CSV" })).toBeEnabled();
  });
});
