import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AppShell } from "./AppShell";

const useSessionMock = vi.fn();
const clearSessionMock = vi.fn();
const navigateMock = vi.fn();

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return {
    ...actual,
    useNavigate: () => navigateMock,
  };
});

vi.mock("../api/session", () => ({
  useSession: () => useSessionMock(),
  clearSession: () => clearSessionMock(),
  consumeSessionExpired: () => false,
}));

describe("AppShell", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    window.localStorage.removeItem("bingo-admin-nav-collapsed");
    clearSessionMock.mockReset();
    navigateMock.mockReset();
    useSessionMock.mockReturnValue({
      accessToken: "token",
      tokenType: "Bearer",
      expiresInSeconds: 3600,
      user: {
        id: 1,
        displayName: "Admin Tester",
        phoneNumber: "251911000001",
        roles: ["ADMIN"],
        status: "ACTIVE",
        createdAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z",
      },
    });
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it("logs out after 15 minutes of inactivity", async () => {
    const queryClient = new QueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/dashboard"]}>
          <Routes>
            <Route element={<AppShell />}>
              <Route path="dashboard" element={<div>Dashboard content</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    window.dispatchEvent(new Event("mousemove"));

    await act(async () => {
      vi.advanceTimersByTime(14 * 60 * 1000);
    });

    expect(clearSessionMock).not.toHaveBeenCalled();

    await act(async () => {
      vi.advanceTimersByTime(60 * 1000);
    });

    expect(clearSessionMock).toHaveBeenCalledTimes(1);
    expect(navigateMock).toHaveBeenCalledWith("/login", { replace: true });
  });

  it("renders grouped navigation and the current user menu", async () => {
    const queryClient = new QueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/dashboard"]}>
          <Routes>
            <Route element={<AppShell />}>
              <Route path="dashboard" element={<div>Dashboard content</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(screen.getAllByRole("banner")[0].textContent).toContain("Sunset Bingo Admin");
    expect(screen.getAllByText("Admin Tester")[0]).toBeInTheDocument();
    expect(screen.queryByText("Overview")).not.toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /admin tester/i })[0]).toBeInTheDocument();
    expect(screen.getAllByText("Operations")[0]).toBeInTheDocument();
    expect(screen.getAllByText("Reviews & Support")[0]).toBeInTheDocument();
    expect(screen.getAllByText("Players & Agents")[0]).toBeInTheDocument();
    expect(screen.getAllByText("Finance")[0]).toBeInTheDocument();
    expect(screen.getAllByText("Administration")[0]).toBeInTheDocument();
    for (const group of ["Operations", "Reviews & Support", "Players & Agents", "Finance", "Administration"]) {
      const toggle = screen.getAllByRole("button", { name: new RegExp(group) })[0];
      if (toggle.getAttribute("aria-expanded") !== "true") {
        fireEvent.click(toggle);
      }
    }
    expect(screen.getAllByRole("link", { name: "Admin Users" })[0]).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Dashboard" })[0]).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Sessions" })[0]).toHaveAttribute("href", "/sessions");
    expect(screen.getAllByRole("link", { name: "Session tickets" })[0]).toHaveAttribute("href", "/session-tickets");
    expect(screen.getAllByRole("link", { name: "Rooms" })[0]).toHaveAttribute("href", "/rooms");
    expect(screen.getAllByRole("link", { name: "Player community" })[0]).toHaveAttribute("href", "/community");
    expect(screen.getAllByRole("link", { name: "Deposits" })[0]).toHaveAttribute("href", "/deposits");
    expect(screen.getAllByRole("link", { name: "Win Claims" })[0]).toHaveAttribute("href", "/claims");
    expect(screen.getAllByRole("link", { name: "Support" })[0]).toHaveAttribute("href", "/support/tickets");
    expect(screen.getAllByRole("link", { name: "Prize Entitlements" })[0]).toHaveAttribute("href", "/prize-entitlements");
    expect(screen.getAllByRole("link", { name: "Players" })[0]).toHaveAttribute("href", "/players");
    expect(screen.getAllByRole("link", { name: "Player verification" })[0]).toHaveAttribute("href", "/player-verifications");
    expect(screen.getAllByRole("link", { name: "Agents" })[0]).toHaveAttribute("href", "/agents");
    expect(screen.getAllByRole("link", { name: "Wallet Policies" })[0]).toHaveAttribute("href", "/wallet/signup-reward");
    expect(screen.getAllByRole("link", { name: "Transfer History" })[0]).toHaveAttribute("href", "/wallet/transfers");
    expect(screen.getAllByRole("link", { name: "Audit Logs" })[0]).toHaveAttribute("href", "/audit-logs");
    expect(screen.getAllByText("Dashboard content")[0]).toBeInTheDocument();

    const reviewsToggle = screen.getAllByRole("button", { name: /Reviews & Support/ })[0];
    expect(reviewsToggle).toHaveAttribute("aria-expanded", "true");

    fireEvent.click(reviewsToggle);

    expect(reviewsToggle).toHaveAttribute("aria-expanded", "false");

    fireEvent.click(reviewsToggle);

    expect(reviewsToggle).toHaveAttribute("aria-expanded", "true");
  });

  it("shows session ticket lookup to support staff", () => {
    useSessionMock.mockReturnValue({
      accessToken: "token",
      tokenType: "Bearer",
      expiresInSeconds: 3600,
      user: {
        id: 2,
        displayName: "Support Tester",
        phoneNumber: "251911000002",
        roles: ["SUPPORT"],
        status: "ACTIVE",
        createdAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z",
      },
    });
    const queryClient = new QueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/support/tickets"]}>
          <Routes>
            <Route element={<AppShell />}>
              <Route path="support/tickets" element={<div>Support tickets</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(screen.getByRole("link", { name: "Session tickets" })).toHaveAttribute("href", "/session-tickets");
    expect(screen.queryByRole("link", { name: "Sessions" })).not.toBeInTheDocument();
  });

  it("provides profile, password, and logout actions in the account menu", () => {
    const queryClient = new QueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/dashboard"]}>
          <Routes>
            <Route element={<AppShell />}>
              <Route path="dashboard" element={<div>Dashboard content</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    const accountButton = screen.getByRole("button", { name: /admin tester/i });
    fireEvent.click(accountButton);

    expect(screen.getByRole("menuitem", { name: "Profile" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Change password" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Logout" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("menuitem", { name: "Profile" }));
    expect(navigateMock).toHaveBeenLastCalledWith("/profile");

    fireEvent.click(accountButton);
    fireEvent.click(screen.getByRole("menuitem", { name: "Change password" }));
    expect(navigateMock).toHaveBeenLastCalledWith("/change-password");

    fireEvent.click(accountButton);
    fireEvent.click(screen.getByRole("menuitem", { name: "Logout" }));
    expect(clearSessionMock).toHaveBeenCalledTimes(1);
    expect(navigateMock).toHaveBeenLastCalledWith("/login", { replace: true });
  });

  it("collapses navigation to labeled icons and persists the preference", () => {
    const queryClient = new QueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/dashboard"]}>
          <Routes>
            <Route element={<AppShell />}>
              <Route path="dashboard" element={<div>Dashboard content</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    fireEvent.click(screen.getByRole("button", { name: "Collapse navigation" }));

    expect(screen.getByRole("button", { name: "Expand navigation" })).toHaveAttribute("aria-pressed", "true");
    expect(window.localStorage.getItem("bingo-admin-nav-collapsed")).toBe("true");
    expect(screen.getByRole("link", { name: "Dashboard" })).toBeInTheDocument();
  });

  it("shows audit logs to support-only users without exposing admin navigation", () => {
    useSessionMock.mockReturnValue({
      accessToken: "support-token",
      tokenType: "Bearer",
      expiresInSeconds: 3600,
      user: {
        id: 2,
        displayName: "Support Tester",
        phoneNumber: "251911000002",
        roles: ["SUPPORT"],
        status: "ACTIVE",
        createdAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z",
      },
    });
    const queryClient = new QueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/support/tickets"]}>
          <Routes>
            <Route element={<AppShell />}>
              <Route path="support/tickets" element={<div>Support content</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(screen.getByRole("link", { name: "Support" })).toHaveAttribute("aria-current", "page");
    expect(screen.queryByRole("link", { name: "Dashboard" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Wallet Policies" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Transfer History" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Player verification" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Audit Logs" })).toHaveAttribute("href", "/audit-logs");
    expect(screen.getByText("Support content")).toBeInTheDocument();
  });
});
