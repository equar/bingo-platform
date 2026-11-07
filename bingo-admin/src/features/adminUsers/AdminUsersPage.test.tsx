import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AdminUsersPage } from "./AdminUsersPage";
import i18n from "../../i18n";

const adminUsersMock = vi.fn();
const createAdminUserMock = vi.fn();
const updateAdminUserMock = vi.fn();
const revokeFinanceRoleMock = vi.fn();

vi.mock("../../api/admin", () => ({
  adminUsers: (...args: unknown[]) => adminUsersMock(...args),
  createAdminUser: (...args: unknown[]) => createAdminUserMock(...args),
  updateAdminUser: (...args: unknown[]) => updateAdminUserMock(...args),
  revokeFinanceRole: (...args: unknown[]) => revokeFinanceRoleMock(...args),
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
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/admin-users"]}>
          <Routes>
            <Route path="/admin-users" element={children} />
            <Route path="/admin-users/new" element={children} />
            <Route path="/admin-users/:userId/edit" element={children} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );
  };
}

describe("AdminUsersPage", () => {
  afterEach(cleanup);

  beforeEach(async () => {
    vi.clearAllMocks();
    await i18n.changeLanguage("en");
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

    expect((await screen.findAllByRole("heading", { name: "Admin users" }))[0]).toBeInTheDocument();
    expect(screen.getByText("Alice Admin")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Create admin user" }));
    expect(await screen.findByRole("heading", { name: "Create admin user" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Status")).not.toBeInTheDocument();
    await user.type(screen.getByRole("textbox", { name: /display name/i }), "New Admin");
    await user.type(screen.getByRole("textbox", { name: /email/i }), "new@bingo.local");
    await user.type(screen.getByRole("textbox", { name: /phone number/i }), "0925636541");
    await user.click(screen.getByRole("button", { name: "Create admin user" }));

    await waitFor(() => {
      expect(createAdminUserMock).toHaveBeenCalledWith({
        displayName: "New Admin",
        email: "new@bingo.local",
        phoneNumber: "0925636541",
        status: "PENDING",
        roles: ["ADMIN"],
      });
    });
  }, 20000);

  it("rejects empty and invalid create-form values without calling a mutation", async () => {
    adminUsersMock.mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    });

    const user = userEvent.setup();
    render(<AdminUsersPage />, { wrapper: createWrapper() });
    await user.click(await screen.findByRole("button", { name: "Create admin user" }));
    await screen.findByRole("heading", { name: "Create admin user" });
    const submit = screen.getByRole("button", { name: "Create admin user" });

    await user.click(submit);
    await screen.findByText("Enter a display name.");

    await user.type(screen.getByRole("textbox", { name: /display name/i }), "New Admin");
    await user.type(screen.getByRole("textbox", { name: /^email$/i }), "not-an-email");
    await user.type(screen.getByRole("textbox", { name: /phone number/i }), "123456789");
    await user.click(submit);
    await screen.findByText("Enter a valid email address.");

    await user.clear(screen.getByRole("textbox", { name: /^email$/i }));
    await user.type(screen.getByRole("textbox", { name: /^email$/i }), "new@bingo.local");
    await user.click(submit);
    expect(screen.getByText("Enter exactly 10 digits.")).toBeInTheDocument();
    await act(async () => { await i18n.changeLanguage("am"); });
    expect(screen.getByText("በትክክል 10 አሃዞች ያስገቡ።")).toBeInTheDocument();
    expect(createAdminUserMock).not.toHaveBeenCalled();
    expect(updateAdminUserMock).not.toHaveBeenCalled();
  });

  it("opens the edit details route and saves changes", async () => {
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
    expect(await screen.findByRole("heading", { name: "Edit admin user" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: /phone number/i })).toHaveValue("0911000001");
    const saveButton = screen.getByRole("button", { name: "Save changes" });
    expect(saveButton).toBeDisabled();

    const nameField = screen.getByRole("textbox", { name: /display name/i });
    await user.clear(nameField);
    await user.type(nameField, "Alice Administrator");
    expect(saveButton).toBeEnabled();
    await user.clear(nameField);
    await user.type(nameField, "Alice Admin");
    expect(saveButton).toBeDisabled();
    await user.clear(nameField);
    await user.type(nameField, "Alice Admin Updated");
    await user.click(saveButton);

    await waitFor(() => {
      expect(updateAdminUserMock).toHaveBeenCalledWith(1, {
        displayName: "Alice Admin Updated",
        email: "alice@bingo.local",
        phoneNumber: "0911000001",
        status: "ACTIVE",
        roles: ["ADMIN", "SUPPORT"],
      });
    });
  });

  it("keeps unsaved create values when editing continues and returns to the list after discard", async () => {
    adminUsersMock.mockResolvedValue({
      content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true,
    });
    const user = userEvent.setup();
    render(<AdminUsersPage />, { wrapper: createWrapper() });
    await user.click(await screen.findByRole("button", { name: "Create admin user" }));
    const nameField = screen.getByRole("textbox", { name: /display name/i });
    await user.type(nameField, "Draft account");
    await user.click(screen.getAllByRole("button", { name: "Cancel" })[0]);
    const discardDialog = screen.getByRole("dialog", { name: "Discard unsaved changes?" });
    await user.click(within(discardDialog).getByRole("button", { name: "Keep editing" }));
    expect(await screen.findByRole("textbox", { name: /display name/i })).toHaveValue("Draft account");
    await user.click(screen.getAllByRole("button", { name: "Cancel" })[0]);
    await user.click(within(screen.getByRole("dialog", { name: "Discard unsaved changes?" })).getByRole("button", { name: "Discard" }));
    expect(await screen.findByText("No admin users match the current filters.")).toBeInTheDocument();
  });

  it("revokes a finance role and refreshes the admin user list", async () => {
    adminUsersMock.mockResolvedValue({
      content: [{
        id: 17,
        displayName: "Finance QA",
        email: "finance@example.invalid",
        phoneNumber: "251911000017",
        roles: ["ADMIN", "FINANCE_VERIFIER"],
        status: "ACTIVE",
        createdAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z",
      }],
      page: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true,
    });
    revokeFinanceRoleMock.mockResolvedValue({});
    const user = userEvent.setup();
    render(<AdminUsersPage />, { wrapper: createWrapper() });
    await user.click(await screen.findByRole("button", { name: "Revoke Finance verifier from Finance QA" }));
    await waitFor(() => {
      expect(revokeFinanceRoleMock).toHaveBeenCalledWith(17, "FINANCE_VERIFIER");
    });
  });

  it("switches operational labels and display-only statuses without changing API values", async () => {
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

      render(<AdminUsersPage />, { wrapper: createWrapper() });
      expect((await screen.findAllByRole("heading", { name: "Admin users" }))[0]).toBeInTheDocument();

      await act(async () => {
        await i18n.changeLanguage("am");
      });

      expect((await screen.findAllByRole("heading", { name: "የአስተዳዳሪ ተጠቃሚዎች" }))[0]).toBeInTheDocument();
      expect(screen.getAllByText("ንቁ")[0]).toBeInTheDocument();
      expect(screen.getAllByText("Alice Admin")[0]).toBeInTheDocument();
      expect(adminUsersMock).toHaveBeenCalledWith(0, 20, "", "");
  });
});
