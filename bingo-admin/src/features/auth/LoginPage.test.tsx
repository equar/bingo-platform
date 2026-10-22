import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { LoginPage } from "./LoginPage";

const loginMock = vi.fn();
const writeSessionMock = vi.fn();
const clearSessionMock = vi.fn();
const navigateMock = vi.fn();
const locationMock = { state: undefined };

vi.mock("../../api/admin", () => ({
  login: (...args: unknown[]) => loginMock(...args),
}));

vi.mock("../../api/session", () => ({
  writeSession: (...args: unknown[]) => writeSessionMock(...args),
  clearSession: () => clearSessionMock(),
}));

vi.mock("../../api/types", () => ({
  parseApiError: (error: unknown) => ({ code: "AUTH_FAILED", message: (error as Error).message ?? "Request failed" }),
}));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return {
    ...actual,
    useNavigate: () => navigateMock,
    useLocation: () => locationMock,
  };
});

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  };
}

describe("LoginPage", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();
    locationMock.state = undefined;
    loginMock.mockResolvedValue({
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

  it("loads the saved email and keeps it in local storage", async () => {
    window.localStorage.setItem("bingo-admin-login-email", "saved@bingo.local");

    const user = userEvent.setup();
    render(<LoginPage />, { wrapper: createWrapper() });

    expect(screen.getByRole("heading", { name: "Sunset Bingo Admin" })).toBeInTheDocument();
    expect(screen.getByText(/Welcome to Sunset Bingo's operations hub/i)).toBeInTheDocument();
    expect(await screen.findByLabelText(/email address/i)).toHaveValue("saved@bingo.local");

    await user.clear(screen.getByLabelText(/email address/i));
    await user.type(screen.getByLabelText(/email address/i), "new@bingo.local");

    expect(window.localStorage.getItem("bingo-admin-login-email")).toBe("new@bingo.local");
  });

  it("routes temporary-password users directly to password change", async () => {
    loginMock.mockResolvedValue({
      accessToken: "temporary-token",
      tokenType: "Bearer",
      expiresInSeconds: 3600,
      user: {
        id: 3,
        displayName: "Temporary Admin",
        phoneNumber: "251911000003",
        roles: ["ADMIN"],
        status: "ACTIVE",
        createdAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z",
        mustChangePassword: true,
      },
    });

    const user = userEvent.setup();
    const { container } = render(<LoginPage />, { wrapper: createWrapper() });

    await user.type(await screen.findByLabelText(/email address/i), "temporary@bingo.local");
    const passwordInput = container.querySelector('input[name="password"]');
    if (!passwordInput) {
      throw new Error("Password input was not rendered");
    }
    await user.type(passwordInput, "temporary-password");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith("/change-password", { replace: true });
    });
  });
});
