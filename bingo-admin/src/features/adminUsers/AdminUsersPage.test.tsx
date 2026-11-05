import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
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
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

describe("AdminUsersPage", () => {
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

    expect((await screen.findAllByRole("heading", { name: "Admin Users" }))[0]).toBeInTheDocument();
    expect(screen.getByText("Alice Admin")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Create admin user" }));
    const createDialog = await screen.findByRole("dialog", { name: "Create admin user" });
    expect(within(createDialog).queryByLabelText("Status")).not.toBeInTheDocument();
    await user.type(within(createDialog).getByRole("textbox", { name: /display name/i }), "New Admin");
    await user.type(within(createDialog).getByRole("textbox", { name: /email/i }), "new@bingo.local");
    await user.type(within(createDialog).getByRole("textbox", { name: /phone number/i }), "0925636541");
    await user.click(screen.getByRole("button", { name: "Create user" }));

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
    const dialog = await screen.findByRole("dialog", { name: "Create admin user" });
    const submit = within(dialog).getByRole("button", { name: "Create user" });

    await user.click(submit);
    await within(dialog).findByText("Enter a display name.");
    expect(dialog).toHaveTextContent("Enter a display name.");

    await user.type(within(dialog).getByRole("textbox", { name: /display name/i }), "New Admin");
    await user.type(within(dialog).getByRole("textbox", { name: /^email$/i }), "not-an-email");
    await user.type(within(dialog).getByRole("textbox", { name: /phone number/i }), "123456789");
    await user.click(submit);
    await within(dialog).findByText("Enter a valid email address.");
    expect(dialog).toHaveTextContent("Enter a valid email address.");

    await user.clear(within(dialog).getByRole("textbox", { name: /^email$/i }));
    await user.type(within(dialog).getByRole("textbox", { name: /^email$/i }), "new@bingo.local");
    await user.click(submit);
    expect(dialog).toHaveTextContent("Enter exactly 10 digits.");
    await act(async () => { await i18n.changeLanguage("am"); });
    expect(dialog).toHaveTextContent("በትክክል 10 አሃዞች ያስገቡ።");
    expect(createAdminUserMock).not.toHaveBeenCalled();
    expect(updateAdminUserMock).not.toHaveBeenCalled();
  });

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
    expect(within(editDialog).getByRole("textbox", { name: /phone number/i })).toHaveValue("0911000001");
    await waitFor(() => {
      expect(within(editDialog).getByText("Administrator")).toBeInTheDocument();
    });
    await user.clear(within(editDialog).getByRole("textbox", { name: /email/i }));
    await user.type(within(editDialog).getByRole("textbox", { name: /email/i }), "alice.admin@bingo.local");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => {
      expect(updateAdminUserMock).toHaveBeenCalledWith(1, {
        displayName: "Alice Admin",
        email: "alice.admin@bingo.local",
        phoneNumber: "0911000001",
        password: undefined,
        status: "ACTIVE",
        roles: ["ADMIN"],
      });
    });
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
      expect((await screen.findAllByRole("heading", { name: "Admin Users" }))[0]).toBeInTheDocument();

      await act(async () => {
        await i18n.changeLanguage("am");
      });

      expect((await screen.findAllByRole("heading", { name: "አስተዳዳሪዎች" }))[0]).toBeInTheDocument();
      expect(screen.getAllByText("ንቁ")[0]).toBeInTheDocument();
      expect(screen.getAllByText("Alice Admin")[0]).toBeInTheDocument();
      expect(adminUsersMock).toHaveBeenCalledWith(0, 20, "", "");
  });
});
