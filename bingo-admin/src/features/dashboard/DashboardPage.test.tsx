import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { PropsWithChildren } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { auditLogs, dashboardMetrics, liveSessions } from "../../api/admin";
import { clearSession, writeSession } from "../../api/session";
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
  afterEach(() => {
    cleanup();
    clearSession();
  });

  beforeEach(async () => {
    await i18n.changeLanguage("en");
    writeSession({
      accessToken: "admin-token",
      tokenType: "Bearer",
      user: {
        id: 1,
        displayName: "Admin",
        phoneNumber: "251911000001",
        roles: ["ADMIN"],
        status: "ACTIVE",
        createdAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z",
      },
    });
    dashboardMetricsMock.mockReset().mockResolvedValue(metricsResponse());
    liveSessionsMock.mockReset().mockResolvedValue([]);
    auditLogsMock.mockReset().mockResolvedValue(auditResponse());
  });

  it("prioritizes real review counts and links to supported queue filters", async () => {
    render(<DashboardPage />, { wrapper: createWrapper });

    expect(await screen.findByRole("heading", { name: "Needs attention" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Open deposit reviews/ })).toHaveAttribute(
      "href",
      "/deposits?status=PENDING",
    );
    expect(screen.getByRole("link", { name: /Open win claims/ })).toHaveAttribute(
      "href",
      "/claims?status=PENDING_REVIEW",
    );
    expect(screen.getByRole("link", { name: /Open support tickets/ })).toHaveAttribute("href", "/support/tickets");
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

  it("hides win-claim shortcuts from SUPPORT", async () => {
    writeSession({
      accessToken: "support-token",
      tokenType: "Bearer",
      user: {
        id: 2,
        displayName: "Support",
        phoneNumber: "251911000002",
        roles: ["SUPPORT"],
        status: "ACTIVE",
        createdAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z",
      },
    });

    render(<DashboardPage />, { wrapper: createWrapper });

    expect(await screen.findByRole("link", { name: /Open deposit reviews/ })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Open win claims/ })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Open support tickets/ })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Recent activity" })).not.toBeInTheDocument();
    expect(auditLogsMock).not.toHaveBeenCalled();
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

  it("refreshes all dashboard sources from the header action", async () => {
    const user = userEvent.setup();
    render(<DashboardPage />, { wrapper: createWrapper });

    await user.click(await screen.findByRole("button", { name: "Refresh dashboard" }));

    await waitFor(() => {
      expect(dashboardMetricsMock).toHaveBeenCalledTimes(2);
      expect(liveSessionsMock).toHaveBeenCalledTimes(2);
      expect(auditLogsMock).toHaveBeenCalledTimes(2);
    });
  });

  it("localizes the refresh action in Amharic", async () => {
    await i18n.changeLanguage("am");
    render(<DashboardPage />, { wrapper: createWrapper });

    expect(await screen.findByRole("button", { name: "ዳሽቦርዱን አድስ" })).toBeInTheDocument();
  });
});
