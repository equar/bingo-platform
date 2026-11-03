import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { PropsWithChildren } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { auditLogs, dashboardMetrics, liveSessions } from "../../api/admin";
import i18n from "../../i18n";
import { MemoryRouter } from "react-router-dom";
import { DashboardPage } from "./DashboardPage";

vi.mock("../../api/admin", () => ({
  auditLogs: vi.fn(),
  dashboardMetrics: vi.fn(),
  liveSessions: vi.fn(),
}));

const auditLogsMock = vi.mocked(auditLogs);
const dashboardMetricsMock = vi.mocked(dashboardMetrics);
const liveSessionsMock = vi.mocked(liveSessions);

function metricsResponse(overrides: Partial<Awaited<ReturnType<typeof dashboardMetrics>>> = {}) {
  return {
    generatedAt: new Date().toISOString(),
    businessTimezone: "Africa/Addis_Ababa",
    activePlayers: 18,
    activeAgents: 4,
    openSupportTickets: 3,
    openDepositReviews: 2,
    openAgentFloatReviews: 0,
    openWinClaims: 1,
    activeSessions: 2,
    playerWalletBalanceTotal: "1234.50",
    agentFloatAvailableTotal: "500.00",
    agentFloatClearingTotal: "20.25",
    ...overrides,
  } satisfies Awaited<ReturnType<typeof dashboardMetrics>>;
}

function auditResponse(entries: Awaited<ReturnType<typeof auditLogs>>["content"] = []) {
  return {
    content: entries,
    page: 0,
    size: 5,
    totalElements: entries.length,
    totalPages: entries.length > 0 ? 1 : 0,
    first: true,
    last: true,
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

describe("DashboardPage", () => {
  afterEach(cleanup);

  beforeEach(async () => {
    await i18n.changeLanguage("en");
    dashboardMetricsMock.mockReset().mockResolvedValue(metricsResponse());
    liveSessionsMock.mockReset().mockResolvedValue([]);
    auditLogsMock.mockReset().mockResolvedValue(auditResponse());
  });

  it("prioritizes real review counts and links to supported queue filters", async () => {
    render(<DashboardPage />, { wrapper: createWrapper });

    expect(await screen.findByRole("heading", { name: "Needs attention" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Review pending deposits/ })).toHaveAttribute(
      "href",
      "/deposits?status=PENDING",
    );
    expect(screen.getByRole("link", { name: /Review pending claims/ })).toHaveAttribute(
      "href",
      "/claims?status=PENDING_REVIEW",
    );
    expect(screen.getByRole("link", { name: /View support queue/ })).toHaveAttribute("href", "/support/tickets");
    expect(screen.getByText("ETB 1,234.50")).toBeInTheDocument();
    expect(screen.getByText("ETB 500.00")).toBeInTheDocument();
    expect(screen.getByText("ETB 20.25")).toBeInTheDocument();
    expect(screen.getByText("No active sessions right now.")).toBeInTheDocument();
    expect(screen.getByText("No audit history.")).toBeInTheDocument();
  });

  it("shows loading and empty states without presenting missing list data as zero counts", async () => {
    let resolveSessions: (sessions: Awaited<ReturnType<typeof liveSessions>>) => void = () => undefined;
    liveSessionsMock.mockImplementation(
      () => new Promise((resolve) => {
        resolveSessions = resolve;
      }),
    );
    auditLogsMock.mockImplementation(() => new Promise(() => undefined));

    render(<DashboardPage />, { wrapper: createWrapper });

    expect(await screen.findByText("Loading live sessions")).toBeInTheDocument();
    expect(screen.getByText("Loading recent activity")).toBeInTheDocument();

    resolveSessions([]);

    expect(await screen.findByText("No active sessions right now.")).toBeInTheDocument();
  });

  it("keeps the dashboard available with a retry action when metrics fail", async () => {
    dashboardMetricsMock
      .mockRejectedValueOnce(new Error("Service unavailable"))
      .mockResolvedValueOnce(metricsResponse());

    render(<DashboardPage />, { wrapper: createWrapper });

    const retry = await screen.findByRole("button", { name: "Retry" });
    await userEvent.click(retry);

    await waitFor(() => expect(dashboardMetricsMock).toHaveBeenCalledTimes(2));
    expect(await screen.findByRole("heading", { name: "Needs attention" })).toBeInTheDocument();
  });

  it("labels a stale metrics snapshot instead of presenting it as current", async () => {
    dashboardMetricsMock.mockResolvedValue(metricsResponse({ generatedAt: "2020-01-01T00:00:00.000Z" }));

    render(<DashboardPage />, { wrapper: createWrapper });

    expect(await screen.findByText("Dashboard data may be out of date.")).toBeInTheDocument();
    expect(screen.getByText(/Data as of/)).toBeInTheDocument();
  });
});
