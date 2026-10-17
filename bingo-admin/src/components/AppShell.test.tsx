import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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
}));

describe("AppShell", () => {
  beforeEach(() => {
    vi.useFakeTimers();
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
    vi.useRealTimers();
  });

  it("logs out after 15 minutes of inactivity", async () => {
    const queryClient = new QueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/management"]}>
          <Routes>
            <Route element={<AppShell />}>
              <Route path="management" element={<div>Management content</div>} />
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
    const user = userEvent.setup();

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/management"]}>
          <Routes>
            <Route element={<AppShell />}>
              <Route path="management" element={<div>Management content</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(screen.getAllByRole("banner")[0].textContent).toContain("Bingo Admin");
    expect(screen.getAllByText("Admin Tester")[0]).toBeInTheDocument();
    expect(screen.getAllByText("Management")[0]).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /admin tester/i })[0]).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Admin Users" })[0]).toBeInTheDocument();
    expect(screen.getAllByText("Operations")[0]).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Dashboard" })[0]).toBeInTheDocument();
    expect(screen.getAllByText("Management content")[0]).toBeInTheDocument();

  });
});
