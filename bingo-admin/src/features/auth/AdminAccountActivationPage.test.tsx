import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Profiler } from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
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
  getFieldError: () => undefined,
}));

function renderPage(token: string | null = "single-use-token", onRender?: () => void) {
  window.history.replaceState(null, "", "/activate-account");
  window.location.hash = token ? `token=${token}` : "";
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <Profiler id="activation" onRender={onRender ?? (() => {})}>
        <MemoryRouter initialEntries={["/activate-account"]}>
          <Routes>
            <Route path="/activate-account" element={<AdminAccountActivationPage />} />
            <Route path="/login" element={<div>Sign in page</div>} />
          </Routes>
        </MemoryRouter>
      </Profiler>
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

  it("redirects to sign-in after activating with the link token and matching password", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText(/New password/), "new-password-123");
    await user.type(screen.getByLabelText(/Confirm new password/), "new-password-123");
    await user.click(screen.getByRole("button", { name: "Activate account" }));

    await waitFor(() => expect(activateMock).toHaveBeenCalledWith({
      token: "single-use-token",
      newPassword: "new-password-123",
    }));
    expect(await screen.findByText("Sign in page")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Set up your Sunset Bingo Admin account" }))
      .not.toBeInTheDocument();
    expect(screen.queryByLabelText(/New password/)).not.toBeInTheDocument();
  });

  it("rejects mismatched passwords without calling the API", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText(/New password/), "new-password-123");
    await user.type(screen.getByLabelText(/Confirm new password/), "different-password");
    const activate = screen.getByRole("button", { name: "Activate account" });
    expect(activate).toBeDisabled();
    fireEvent.submit(activate.closest("form")!);

    expect(await screen.findByText(/passwords do not match/i)).toBeInTheDocument();
    expect(activateMock).not.toHaveBeenCalled();
  });

  it("enables activation only for a valid matching password and submits with Enter", async () => {
    const user = userEvent.setup();
    const commits = { count: 0 };
    renderPage("single-use-token", () => { commits.count += 1; });

    const password = screen.getByLabelText(/New password/);
    const confirmation = screen.getByLabelText(/Confirm new password/);
    const activate = screen.getByRole("button", { name: "Activate account" });
    const startCommits = commits.count;

    expect(activate).toBeDisabled();
    await user.type(password, "short");
    await user.type(confirmation, "short");
    expect(activate).toBeDisabled();
    expect(screen.getByText(/choose a password that meets the requirements/i)).toBeInTheDocument();
    expect(activateMock).not.toHaveBeenCalled();

    await user.clear(password);
    await user.type(password, "new-password-123");
    await user.clear(confirmation);
    await user.type(confirmation, "new-password-123");
    expect(activate).toBeEnabled();
    expect(commits.count - startCommits).toBeLessThan(30);

    await user.keyboard("{Enter}");
    await waitFor(() => expect(activateMock).toHaveBeenCalledTimes(1));
  });

  it("does not submit without an activation token", async () => {
    renderPage(null);

    expect(screen.getByText(/link is missing or has already been used/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Activate account" })).toBeDisabled();
  });
});
