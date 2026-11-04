import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Profiler } from "react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { clearSession, readSession, writeSession } from "../../api/session";
import { ChangePasswordPage } from "./ChangePasswordPage";

const changeAdminPasswordMock = vi.fn();
const navigateMock = vi.fn();

vi.mock("../../api/admin", () => ({
  changeAdminPassword: (...args: unknown[]) => changeAdminPasswordMock(...args),
}));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return {
    ...actual,
    useNavigate: () => navigateMock,
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

describe("ChangePasswordPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    changeAdminPasswordMock.mockResolvedValue(undefined);
    writeSession({
      accessToken: "temporary-token",
      tokenType: "Bearer",
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
  });

  afterEach(() => {
    cleanup();
    clearSession();
  });

  it("submits the password change form", async () => {
    const user = userEvent.setup();
    const { container } = render(<ChangePasswordPage />, { wrapper: createWrapper() });

    expect(await screen.findByRole("heading", { name: "Change password" })).toBeInTheDocument();

    const [currentPasswordInput, newPasswordInput, confirmPasswordInput] = container.querySelectorAll(
      'input[type="password"]',
    );

    await user.type(currentPasswordInput, "OldPass123!");
    await user.type(newPasswordInput, "NewPass123!");
    await user.type(confirmPasswordInput, "NewPass123!");
    await user.click(screen.getByRole("button", { name: "Save password" }));

    await waitFor(() => {
      expect(changeAdminPasswordMock).toHaveBeenCalledWith({
        currentPassword: "OldPass123!",
        newPassword: "NewPass123!",
      });
    });

    expect(await screen.findByText("Password updated successfully.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Back to profile" })).toBeInTheDocument();
    expect(readSession()?.user.mustChangePassword).toBe(false);
    expect(navigateMock).toHaveBeenCalledWith("/dashboard", { replace: true });
  });

  it("blocks invalid input, clears corrected validation, and submits by keyboard", async () => {
    const user = userEvent.setup();
    const commits = { count: 0 };
    const { container } = render(
      <Profiler id="change-password" onRender={() => { commits.count += 1; }}>
        <ChangePasswordPage />
      </Profiler>,
      { wrapper: createWrapper() },
    );
    const [currentPasswordInput, newPasswordInput, confirmPasswordInput] =
      container.querySelectorAll('input[type="password"]');
    const saveButton = screen.getByRole("button", { name: "Save password" });
    const form = container.querySelector("form");
    const startCommits = commits.count;

    expect(saveButton).toBeDisabled();
    await user.type(currentPasswordInput, "OldPass123!");
    await user.type(newPasswordInput, "short");
    await user.type(confirmPasswordInput, "short");
    fireEvent.submit(form!);
    expect(changeAdminPasswordMock).not.toHaveBeenCalled();
    expect(screen.getByText("Password must contain 8 to 72 characters.")).toBeInTheDocument();

    await user.clear(newPasswordInput);
    await user.type(newPasswordInput, "NewPass123!");
    expect(saveButton).toBeDisabled();
    expect(screen.getByText("The passwords do not match.")).toBeInTheDocument();
    await user.clear(confirmPasswordInput);
    await user.type(confirmPasswordInput, "NewPass123!");
    expect(saveButton).toBeEnabled();
    expect(commits.count - startCommits).toBeLessThan(40);

    await user.keyboard("{Enter}");
    await waitFor(() => expect(changeAdminPasswordMock).toHaveBeenCalledTimes(1));
  });

  it("clears a safe request failure when corrected input is entered and preserves other fields", async () => {
    const user = userEvent.setup();
    changeAdminPasswordMock.mockRejectedValueOnce(new Error("SQL details are private"));
    const { container } = render(<ChangePasswordPage />, { wrapper: createWrapper() });
    const [currentPasswordInput, newPasswordInput, confirmPasswordInput] =
      container.querySelectorAll('input[type="password"]');

    await user.type(currentPasswordInput, "OldPass123!");
    await user.type(newPasswordInput, "NewPass123!");
    await user.type(confirmPasswordInput, "NewPass123!");
    await user.click(screen.getByRole("button", { name: "Save password" }));

    expect(await screen.findByText("Something went wrong. Please try again.")).toBeInTheDocument();
    expect(screen.queryByText("SQL details are private")).not.toBeInTheDocument();
    await user.type(currentPasswordInput, "2");
    expect(screen.queryByText("Something went wrong. Please try again.")).not.toBeInTheDocument();
    expect(newPasswordInput).toHaveValue("NewPass123!");
    expect(confirmPasswordInput).toHaveValue("NewPass123!");

    await user.click(screen.getByRole("button", { name: "Save password" }));
    await waitFor(() => expect(changeAdminPasswordMock).toHaveBeenCalledTimes(2));
  });
});
