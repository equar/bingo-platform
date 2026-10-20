import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { adminUsers, supportTickets } from "../../api/admin";
import { SupportTicketsPage } from "./SupportTicketsPage";

vi.mock("../../api/admin", () => ({
  adminUsers: vi.fn(),
  supportTickets: vi.fn(),
}));

const adminUsersMock = vi.mocked(adminUsers);
const supportTicketsMock = vi.mocked(supportTickets);

function createWrapper({ children }: PropsWithChildren) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe("SupportTicketsPage", () => {
  afterEach(cleanup);

  beforeEach(() => {
    adminUsersMock.mockReset();
    supportTicketsMock.mockReset();
    adminUsersMock.mockResolvedValue({
      content: [
        {
          id: 7,
          displayName: "Support Agent",
          email: "support@bingo.local",
          phoneNumber: "251911000007",
          roles: ["SUPPORT"],
          status: "ACTIVE",
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
        },
      ],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });
    supportTicketsMock.mockResolvedValue({
      content: [
        {
          id: 18,
          ticketNumber: "ST-TEST-18",
          requesterUserId: 12,
          subject: "Wallet top-up pending",
          category: "WALLET",
          priority: "HIGH",
          status: "OPEN",
          assignedToUserId: 7,
          linkedEntityType: "DEPOSIT",
          linkedEntityId: 32,
          createdAt: "2026-10-20T15:07:22Z",
          updatedAt: "2026-10-20T15:07:22Z",
        },
      ],
      page: 0,
      size: 20,
      totalElements: 42,
      totalPages: 3,
      first: true,
      last: false,
    });
  });

  it("loads tickets into the table and links to ticket details", async () => {
    render(<SupportTicketsPage />, { wrapper: createWrapper });

    expect(await screen.findByTestId("support-ticket-18")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "ST-TEST-18" })).toHaveAttribute("href", "/support/tickets/18");
    expect(supportTicketsMock).toHaveBeenCalledWith(undefined, undefined, undefined, undefined, undefined, 0, 20);
  });

  it("passes search and all filters to the server", async () => {
    const user = userEvent.setup();
    render(<SupportTicketsPage />, { wrapper: createWrapper });

    await screen.findByTestId("support-ticket-18");
    await user.type(screen.getByRole("textbox", { name: "Search tickets, subjects, players, or phone" }), "wallet");
    await waitFor(() =>
      expect(supportTicketsMock).toHaveBeenLastCalledWith("wallet", undefined, undefined, undefined, undefined, 0, 20),
    );

    await user.click(screen.getByRole("combobox", { name: "Status" }));
    await user.click(await screen.findByRole("option", { name: "OPEN" }));
    await waitFor(() =>
      expect(supportTicketsMock).toHaveBeenLastCalledWith("wallet", "OPEN", undefined, undefined, undefined, 0, 20),
    );

    await user.click(screen.getByRole("combobox", { name: "Category" }));
    await user.click(await screen.findByRole("option", { name: "WALLET" }));
    await waitFor(() =>
      expect(supportTicketsMock).toHaveBeenLastCalledWith("wallet", "OPEN", "WALLET", undefined, undefined, 0, 20),
    );

    await user.click(screen.getByRole("combobox", { name: "Priority" }));
    await user.click(await screen.findByRole("option", { name: "HIGH" }));
    await waitFor(() =>
      expect(supportTicketsMock).toHaveBeenLastCalledWith("wallet", "OPEN", "WALLET", undefined, "HIGH", 0, 20),
    );

    await user.click(screen.getByRole("combobox", { name: "Assignee" }));
    await user.click(await screen.findByRole("option", { name: "Support Agent" }));
    await waitFor(() =>
      expect(supportTicketsMock).toHaveBeenLastCalledWith("wallet", "OPEN", "WALLET", "7", "HIGH", 0, 20),
    );
  });

  it("requests the next server page", async () => {
    const user = userEvent.setup();
    render(<SupportTicketsPage />, { wrapper: createWrapper });

    await screen.findByTestId("support-ticket-18");
    await user.click(screen.getByRole("button", { name: "Go to next page" }));

    await waitFor(() =>
      expect(supportTicketsMock).toHaveBeenLastCalledWith(undefined, undefined, undefined, undefined, undefined, 1, 20),
    );
  });
});
