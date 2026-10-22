import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AdminAccountActivationPage } from "./AdminAccountActivationPage";

const activateMock = vi.fn();

vi.mock("../../api/admin", () => ({
  activateAdminAccount: (...args: unknown[]) => activateMock(...args),
}));

vi.mock("../../api/types", () => ({
  parseApiError: (error: unknown) => ({ code: "ACTIVATION_FAILED", message: (error as Error).message }),
}));

function renderPage(token: string | null = "single-use-token") {
  window.history.replaceState(null, "", "/activate-account");
  window.location.hash = token ? `token=${token}` : "";
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/activate-account"]}>
        <Routes>
          <Route path="/activate-account" element={<AdminAccountActivationPage />} />
          <Route path="/login" element={<div>Sign in page</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("AdminAccountActivationPage", () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.clearAllMocks();
    activateMock.mockResolvedValue(undefined);
  });

  afterEach(() => {
    window.history.replaceState(null, "", "/");
  });

  it("activates with the link token and matching new password", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText(/New password/), "new-password-123");
    await user.type(screen.getByLabelText(/Confirm new password/), "new-password-123");
    await user.click(screen.getByRole("button", { name: "Activate account" }));

    await waitFor(() => expect(activateMock).toHaveBeenCalledWith({
      token: "single-use-token",
      newPassword: "new-password-123",
    }));
    expect(await screen.findByText(/account is ready/i)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Sign in" }));
    expect(screen.getByText("Sign in page")).toBeInTheDocument();
  });

  it("rejects mismatched passwords without calling the API", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText(/New password/), "new-password-123");
    await user.type(screen.getByLabelText(/Confirm new password/), "different-password");
    await user.click(screen.getByRole("button", { name: "Activate account" }));

    expect(await screen.findByText(/passwords do not match/i)).toBeInTheDocument();
    expect(activateMock).not.toHaveBeenCalled();
  });

  it("does not submit without an activation token", async () => {
    renderPage(null);

    expect(screen.getByText(/link is missing or has already been used/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Activate account" })).toBeDisabled();
  });
});
