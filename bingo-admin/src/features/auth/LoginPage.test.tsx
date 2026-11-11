import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { StrictMode } from "react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import i18n from "../../i18n";
import { LoginPage } from "./LoginPage";

const loginMock = vi.fn();
const writeSessionMock = vi.fn();
const clearSessionMock = vi.fn();
const navigateMock = vi.fn();
const consumeSessionExpiredMock = vi.fn();
const locationMock: { state: unknown } = { state: undefined };

vi.mock("../../api/admin", () => ({
  login: (...args: unknown[]) => loginMock(...args),
}));

vi.mock("../../api/session", () => ({
  writeSession: (...args: unknown[]) => writeSessionMock(...args),
  clearSession: () => clearSessionMock(),
  consumeSessionExpired: (...args: unknown[]) => consumeSessionExpiredMock(...args),
}));

vi.mock("../../api/types", () => ({
  parseApiError: (error: unknown) => ({
    status: (error as { status?: number }).status ?? 0,
    code: "AUTH_FAILED",
    message: (error as Error).message ?? "Request failed",
  }),
  getFieldError: () => undefined,
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
  afterEach(async () => {
    cleanup();
    await i18n.changeLanguage("en");
  });

  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();
    locationMock.state = undefined;
    consumeSessionExpiredMock.mockReturnValue(false);
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

  it("always renders in English even when the previous active language was Amharic", async () => {
    // Simulates a leftover Amharic UI language (e.g. from the previous account).
    await i18n.changeLanguage("am");

    render(<LoginPage />, { wrapper: createWrapper() });

    expect(screen.getByRole("heading", { name: "Sunset Bingo Admin" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sign in" })).toBeInTheDocument();
    expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
    expect(screen.getByText("Secure access for authorized admin and support accounts")).toBeInTheDocument();
    expect(screen.queryByText("ግባ")).not.toBeInTheDocument();
  });

  it("shows the expired-session notice in English regardless of the previous language", async () => {
    await i18n.changeLanguage("am");
    consumeSessionExpiredMock.mockReturnValueOnce(true).mockReturnValue(false);

    render(<LoginPage />, { wrapper: createWrapper() });

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Your session has expired. Please sign in again.",
    );
  });

  it("shows an activation success notice after redirecting to sign-in", () => {
    locationMock.state = { accountActivated: true };

    render(<LoginPage />, { wrapper: createWrapper() });

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Your account is ready. Sign in with your new password.",
    );
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

  it("explains when the backend cannot be reached during sign-in", async () => {
    loginMock.mockRejectedValueOnce(new Error("Network Error"));

    const user = userEvent.setup();
    render(<LoginPage />, { wrapper: createWrapper() });

    await user.type(await screen.findByLabelText(/email address/i), "admin@bingo.local");
    await user.type(screen.getByLabelText(/^password/i), "correct-password");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "We couldn’t reach the server right now. Check your connection or try again in a moment.",
    );
  });

  it("preserves the expired-session notice when StrictMode replays effects", async () => {
    consumeSessionExpiredMock.mockReturnValueOnce(true).mockReturnValue(false);

    render(
      <StrictMode>
        <LoginPage />
      </StrictMode>,
      { wrapper: createWrapper() },
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Your session has expired. Please sign in again.",
    );
    expect(consumeSessionExpiredMock).toHaveBeenCalledTimes(2);
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
