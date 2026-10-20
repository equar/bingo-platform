import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { searchPlayers } from "../../api/admin";
import { PlayersPage } from "./PlayersPage";

vi.mock("../../api/admin", () => ({ searchPlayers: vi.fn() }));

const searchPlayersMock = vi.mocked(searchPlayers);

function createWrapper({ children }: PropsWithChildren) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe("PlayersPage", () => {
  afterEach(cleanup);

  beforeEach(() => {
    searchPlayersMock.mockReset();
    searchPlayersMock.mockResolvedValue({
      content: [
        {
          id: 10,
          displayName: "E2E Player One",
          normalizedPhone: "251918072175",
          roles: ["PLAYER"],
          status: "ACTIVE",
          createdAt: "2026-10-20T15:07:22Z",
          updatedAt: "2026-10-20T15:07:22Z",
          walletTransactionCount: 1,
          depositClaimCount: 0,
          supportTicketCount: 0,
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

  it("loads and shows players before a search term is entered", async () => {
    render(<PlayersPage />, { wrapper: createWrapper });

    expect(await screen.findByRole("link", { name: "E2E Player One" })).toBeInTheDocument();
    expect(searchPlayersMock).toHaveBeenCalledWith("", 0, 20, undefined);
  });

  it("passes the entered search text to the player query", async () => {
    const user = userEvent.setup();
    render(<PlayersPage />, { wrapper: createWrapper });

    const search = screen.getByRole("textbox", { name: "Search by name, email, or phone" });
    await user.type(search, "E2E");

    await waitFor(() => expect(searchPlayersMock).toHaveBeenLastCalledWith("E2E", 0, 20, undefined));
  });

  it("filters by status and resets to the first page", async () => {
    const user = userEvent.setup();
    render(<PlayersPage />, { wrapper: createWrapper });

    await user.click(screen.getByRole("combobox", { name: "Status" }));
    await user.click(await screen.findByRole("option", { name: "SUSPENDED" }));

    await waitFor(() => expect(searchPlayersMock).toHaveBeenLastCalledWith("", 0, 20, "SUSPENDED"));
  });

  it("requests the next server page", async () => {
    const user = userEvent.setup();
    render(<PlayersPage />, { wrapper: createWrapper });

    await screen.findByRole("link", { name: "E2E Player One" });
    await user.click(screen.getByRole("button", { name: "Go to next page" }));

    await waitFor(() => expect(searchPlayersMock).toHaveBeenLastCalledWith("", 1, 20, undefined));
  });
});
