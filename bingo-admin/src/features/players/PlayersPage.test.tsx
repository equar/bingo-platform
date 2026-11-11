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
  afterEach(() => {
    cleanup();
    window.sessionStorage.removeItem("admin-list-search:players");
  });

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
    await user.click(await screen.findByRole("option", { name: "Suspended" }));

    await waitFor(() => expect(searchPlayersMock).toHaveBeenLastCalledWith("", 0, 20, "SUSPENDED"));
  });

  it("requests the next server page", async () => {
    const user = userEvent.setup();
    render(<PlayersPage />, { wrapper: createWrapper });

    await screen.findByRole("link", { name: "E2E Player One" });
    await user.click(screen.getByRole("button", { name: "Go to next page" }));

    await waitFor(() => expect(searchPlayersMock).toHaveBeenLastCalledWith("", 1, 20, undefined));
  });

  it("restores safe filters and page from the URL while keeping search private", async () => {
    window.sessionStorage.setItem("admin-list-search:players", "Alice");
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/players?status=SUSPENDED&page=2&size=10"]}>
          <PlayersPage />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    const playerLink = await screen.findByRole("link", { name: "E2E Player One" });
    expect(playerLink).toHaveAttribute("href", "/players/10?status=SUSPENDED&page=2&size=10");
    expect(searchPlayersMock).toHaveBeenCalledWith("Alice", 2, 10, "SUSPENDED");
  });

  it("refreshes the current URL-backed page and preserves its search and filters", async () => {
    const user = userEvent.setup();
    window.sessionStorage.setItem("admin-list-search:players", "Alice");
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter initialEntries={["/players?status=SUSPENDED&page=2&size=10"]}>
          <PlayersPage />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await screen.findByRole("link", { name: "E2E Player One" });
    const refresh = screen.getByRole("button", { name: "Refresh" });
    await user.click(refresh);

    await waitFor(() => expect(searchPlayersMock).toHaveBeenLastCalledWith("Alice", 2, 10, "SUSPENDED"));
    expect(screen.getByRole("link", { name: "E2E Player One" })).toHaveAttribute(
      "href", "/players/10?status=SUSPENDED&page=2&size=10",
    );
    expect(refresh).toHaveFocus();
  });

  it("prevents duplicate refresh requests while the current request is pending", async () => {
    const user = userEvent.setup();
    let finishRefresh: ((value: Awaited<ReturnType<typeof searchPlayers>>) => void) | undefined;
    render(<PlayersPage />, { wrapper: createWrapper });
    await screen.findByRole("link", { name: "E2E Player One" });
    searchPlayersMock.mockImplementationOnce(() => new Promise((resolve) => { finishRefresh = resolve; }));
    const refresh = screen.getByRole("button", { name: "Refresh" });

    await user.click(refresh);
    expect(refresh).toBeDisabled();
    expect(searchPlayersMock).toHaveBeenCalledTimes(2);

    finishRefresh?.({
      content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true,
    });
    await waitFor(() => expect(refresh).toBeEnabled());
  });

  it("retains the current rows after a refresh error and retries the same list", async () => {
    const user = userEvent.setup();
    render(<PlayersPage />, { wrapper: createWrapper });
    await screen.findByRole("link", { name: "E2E Player One" });
    searchPlayersMock.mockRejectedValueOnce(new Error("temporary network failure"));

    await user.click(screen.getByRole("button", { name: "Refresh" }));
    const retry = await screen.findByRole("button", { name: "Retry" });
    expect(screen.getByRole("link", { name: "E2E Player One" })).toBeInTheDocument();

    await user.click(retry);
    await waitFor(() => expect(searchPlayersMock).toHaveBeenCalledTimes(3));
    expect(screen.getByRole("link", { name: "E2E Player One" })).toBeInTheDocument();
  });

  it("does not let an older filtered response replace the newer filter results", async () => {
    const user = userEvent.setup();
    let finishAlice: ((value: Awaited<ReturnType<typeof searchPlayers>>) => void) | undefined;
    render(<PlayersPage />, { wrapper: createWrapper });
    await screen.findByRole("link", { name: "E2E Player One" });
    searchPlayersMock.mockImplementation((query) => {
      if (query === "Alice") return new Promise((resolve) => { finishAlice = resolve; });
      if (query === "Bob") return Promise.resolve({
        content: [{
          id: 20, displayName: "Bob Current", normalizedPhone: "251900000020", roles: ["PLAYER"], status: "ACTIVE",
          createdAt: "2026-10-20T15:07:22Z", updatedAt: "2026-10-20T15:07:22Z",
          walletTransactionCount: 0, depositClaimCount: 0, supportTicketCount: 0,
        }], page: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true,
      });
      return Promise.resolve({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true });
    });

    const search = screen.getByRole("textbox", { name: "Search by name, email, or phone" });
    await user.type(search, "Alice");
    await waitFor(() => expect(searchPlayersMock).toHaveBeenLastCalledWith("Alice", 0, 20, undefined));
    await user.clear(search);
    await user.type(search, "Bob");
    expect(await screen.findByRole("link", { name: "Bob Current" })).toBeInTheDocument();

    finishAlice?.({
      content: [{
        id: 30, displayName: "Alice Stale", normalizedPhone: "251900000030", roles: ["PLAYER"], status: "ACTIVE",
        createdAt: "2026-10-20T15:07:22Z", updatedAt: "2026-10-20T15:07:22Z",
        walletTransactionCount: 0, depositClaimCount: 0, supportTicketCount: 0,
      }], page: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true,
    });
    await waitFor(() => expect(screen.queryByRole("link", { name: "Alice Stale" })).not.toBeInTheDocument());
    expect(screen.getByRole("link", { name: "Bob Current" })).toBeInTheDocument();
  });

  it("moves to the last valid page when a refresh removes the current page", async () => {
    const user = userEvent.setup();
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter initialEntries={["/players?page=2&size=10"]}>
          <PlayersPage />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    await screen.findByRole("link", { name: "E2E Player One" });
    searchPlayersMock.mockResolvedValueOnce({
      content: [], page: 2, size: 10, totalElements: 11, totalPages: 2, first: false, last: true,
    });

    await user.click(screen.getByRole("button", { name: "Refresh" }));

    await waitFor(() => expect(searchPlayersMock).toHaveBeenLastCalledWith("", 1, 10, undefined));
    await waitFor(() => expect(screen.getByRole("link", { name: "E2E Player One" })).toHaveAttribute(
      "href", "/players/10?page=1&size=10",
    ));
  });
});
