import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ProfilePage } from "./ProfilePage";

const useSessionMock = vi.fn();
const readSessionMock = vi.fn();
const writeSessionMock = vi.fn();
const updateMyPreferencesMock = vi.fn();

vi.mock("../../api/session", () => ({
  useSession: () => useSessionMock(),
  readSession: () => readSessionMock(),
  writeSession: (...args: unknown[]) => writeSessionMock(...args),
  consumeSessionExpired: () => false,
}));

vi.mock("../../api/admin", () => ({
  updateMyPreferences: (...args: unknown[]) => updateMyPreferencesMock(...args),
}));

const session = {
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
    updatedAt: "2026-01-02T00:00:00.000Z",
    preferredLanguage: "en",
  },
};

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
      mutations: {
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

describe("ProfilePage", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    useSessionMock.mockReturnValue(session);
    readSessionMock.mockReturnValue(session);
  });

  it("renders the read-only account summary", async () => {
    render(<ProfilePage />, { wrapper: createWrapper() });

    expect(await screen.findByRole("heading", { name: "Profile" })).toBeInTheDocument();
    expect(screen.getByText("Admin Tester")).toBeInTheDocument();
    expect(screen.getByText("Your current admin identity and status.")).toBeInTheDocument();
    expect(screen.getByText("Use the account menu in the top-right corner for password changes and logout.")).toBeInTheDocument();
  });

  it("renders the language preference with helper text and the saved value selected", async () => {
    render(<ProfilePage />, { wrapper: createWrapper() });

    expect(await screen.findByRole("heading", { name: "Preferences" })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: /Language/ })).toHaveTextContent("English");
    expect(screen.getByText("Controls the display language of the admin application.")).toBeInTheDocument();
  });

  it("saves Amharic, updates the session, and announces success", async () => {
    updateMyPreferencesMock.mockResolvedValue({ ...session.user, preferredLanguage: "am" });

    const user = userEvent.setup();
    render(<ProfilePage />, { wrapper: createWrapper() });

    await user.click(await screen.findByRole("combobox", { name: /Language/ }));
    await user.click(screen.getByRole("option", { name: "አማርኛ" }));
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(updateMyPreferencesMock).toHaveBeenCalledWith({ preferredLanguage: "am" });
      expect(writeSessionMock).toHaveBeenCalledWith({
        ...session,
        user: { ...session.user, preferredLanguage: "am" },
      });
    });
    expect(await screen.findByText("Your language preference has been saved.")).toBeInTheDocument();
  });

  it("keeps the previous preference and shows a retryable error when saving fails", async () => {
    updateMyPreferencesMock.mockRejectedValueOnce(new Error("save failed"));
    updateMyPreferencesMock.mockResolvedValueOnce({ ...session.user, preferredLanguage: "am" });

    const user = userEvent.setup();
    render(<ProfilePage />, { wrapper: createWrapper() });

    await user.click(await screen.findByRole("combobox", { name: /Language/ }));
    await user.click(screen.getByRole("option", { name: "አማርኛ" }));
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("Your language preference could not be saved.")).toBeInTheDocument();
    expect(writeSessionMock).not.toHaveBeenCalled();

    // Retry succeeds without reselecting the language.
    await user.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => {
      expect(writeSessionMock).toHaveBeenCalledWith({
        ...session,
        user: { ...session.user, preferredLanguage: "am" },
      });
    });
  });

  it("ignores a late save response from a previous session", async () => {
    updateMyPreferencesMock.mockImplementation(async () => {
      // Another account signs in while the request is in flight.
      readSessionMock.mockReturnValue({ ...session, accessToken: "another-user-token" });
      return { ...session.user, preferredLanguage: "am" };
    });

    const user = userEvent.setup();
    render(<ProfilePage />, { wrapper: createWrapper() });

    await user.click(await screen.findByRole("combobox", { name: /Language/ }));
    await user.click(screen.getByRole("option", { name: "አማርኛ" }));
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(updateMyPreferencesMock).toHaveBeenCalled();
    });
    expect(writeSessionMock).not.toHaveBeenCalled();
  });

  it("supports changing the language with the keyboard only", async () => {
    updateMyPreferencesMock.mockResolvedValue({ ...session.user, preferredLanguage: "am" });

    const user = userEvent.setup();
    render(<ProfilePage />, { wrapper: createWrapper() });

    const combobox = await screen.findByRole("combobox", { name: /Language/ });
    combobox.focus();
    await user.keyboard("{Enter}");
    await user.keyboard("{ArrowDown}{Enter}");
    expect(combobox).toHaveTextContent("አማርኛ");

    await user.tab();
    expect(screen.getByRole("button", { name: "Save" })).toHaveFocus();
    await user.keyboard("{Enter}");

    await waitFor(() => {
      expect(updateMyPreferencesMock).toHaveBeenCalledWith({ preferredLanguage: "am" });
    });
  });
});
