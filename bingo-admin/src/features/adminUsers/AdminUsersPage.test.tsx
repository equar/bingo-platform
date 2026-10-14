import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AdminUsersPage } from "./AdminUsersPage";

const adminUsersMock = vi.fn();
const createAdminUserMock = vi.fn();
const updateAdminUserMock = vi.fn();

vi.mock("../../api/admin", () => ({
  adminUsers: (...args: unknown[]) => adminUsersMock(...args),
  createAdminUser: (...args: unknown[]) => createAdminUserMock(...args),
  updateAdminUser: (...args: unknown[]) => updateAdminUserMock(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return function Wrapper({ children }: { children: React.ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

describe("AdminUsersPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders admin users and creates a new one", async () => {
    adminUsersMock.mockResolvedValue({
      content: [
        {
          id: 1,
          displayName: "Alice Admin",
          email: "alice@bingo.local",
          phoneNumber: "251911000001",
          roles: ["ADMIN"],
          status: "ACTIVE",
          createdAt: "2026-01-01T00:00:00.000Z",
          updatedAt: "2026-01-01T00:00:00.000Z",
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });
    createAdminUserMock.mockResolvedValue({});

    const user = userEvent.setup();
    render(<AdminUsersPage />, { wrapper: createWrapper() });

    expect(await screen.findByRole("heading", { name: "Admin Users" })).toBeInTheDocument();
    expect(screen.getByText("Alice Admin")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Create admin user" }));
    const createDialog = await screen.findByRole("dialog", { name: "Create admin user" });
    await user.type(within(createDialog).getByRole("textbox", { name: /display name/i }), "New Admin");
    await user.type(within(createDialog).getByRole("textbox", { name: /email/i }), "new@bingo.local");
    await user.type(within(createDialog).getByRole("textbox", { name: /phone number/i }), "0925636541");
    await user.click(screen.getByRole("button", { name: "Create user" }));

    await waitFor(() => {
      expect(createAdminUserMock).toHaveBeenCalledWith({
        displayName: "New Admin",
        email: "new@bingo.local",
        phoneNumber: "0925636541",
        status: "ACTIVE",
        roles: ["ADMIN"],
      });
    });
  }, 10000);

  it("opens an edit dialog and saves changes", async () => {
    adminUsersMock.mockResolvedValue({
      content: [
        {
          id: 1,
          displayName: "Alice Admin",
          email: "alice@bingo.local",
          phoneNumber: "251911000001",
          roles: ["ADMIN", "SUPPORT"],
          status: "ACTIVE",
          createdAt: "2026-01-01T00:00:00.000Z",
          updatedAt: "2026-01-01T00:00:00.000Z",
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });
    updateAdminUserMock.mockResolvedValue({});

    const user = userEvent.setup();
    render(<AdminUsersPage />, { wrapper: createWrapper() });

    expect(await screen.findByText("Alice Admin")).toBeInTheDocument();
    await user.click(await screen.findByRole("button", { name: "Edit" }));
    const editDialog = await screen.findByRole("dialog", { name: "Edit admin user" });
    await waitFor(() => {
      expect(within(editDialog).getByText("ADMIN, SUPPORT")).toBeInTheDocument();
    });
    await user.clear(within(editDialog).getByRole("textbox", { name: /email/i }));
    await user.type(within(editDialog).getByRole("textbox", { name: /email/i }), "alice.admin@bingo.local");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => {
      expect(updateAdminUserMock).toHaveBeenCalledWith(1, {
        displayName: "Alice Admin",
        email: "alice.admin@bingo.local",
        phoneNumber: "251911000001",
        password: undefined,
        status: "ACTIVE",
        roles: ["ADMIN", "SUPPORT"],
      });
    });
  });
});
