import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { searchSessionTicketSessions, searchSessionTickets } from "../../api/admin";
import { SessionTicketsPage } from "./SessionTicketsPage";

vi.mock("../../api/admin", () => ({
  searchSessionTicketSessions: vi.fn(),
  searchSessionTickets: vi.fn(),
}));

const searchSessionTicketSessionsMock = vi.mocked(searchSessionTicketSessions);
const searchSessionTicketsMock = vi.mocked(searchSessionTickets);

function createWrapper({ children }: PropsWithChildren) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/session-tickets"]}>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe("SessionTicketsPage", () => {
  afterEach(cleanup);

  beforeEach(() => {
    searchSessionTicketSessionsMock.mockReset();
    searchSessionTicketSessionsMock.mockResolvedValue({
      content: [
        {
          id: 15,
          roomId: 4,
          roomName: "Evening Room",
          sessionName: "Friday Draw",
          state: "OPEN",
          scheduledStartAt: "2026-10-21T15:00:00Z",
          startedAt: null,
          endedAt: null,
          ticketPrice: "10.00",
          currency: "ETB",
          ticketCount: 18,
          drawCount: 0,
          winClaimCount: 0,
          supportTicketCount: 0,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });
    searchSessionTicketsMock.mockReset();
    searchSessionTicketsMock.mockResolvedValue({
      content: [
        {
          ticketId: 81,
          sessionId: 15,
          playerId: 6,
          playerName: "Alice Player",
          playerPhone: "251911000006",
          publicTicketCode: "BT-15-081",
          purchasedAt: "2026-10-21T14:30:00Z",
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });
  });

  it("filters tickets by the selected session and searches player or ticket details", async () => {
    const user = userEvent.setup();
    render(<SessionTicketsPage />, { wrapper: createWrapper });

    const sessionRow = await screen.findByRole("row", { name: /Friday Draw/ });
    await user.click(within(sessionRow).getByRole("button", { name: "View tickets" }));

    expect(await screen.findByText("Alice Player")).toBeInTheDocument();
    expect(screen.getByText("BT-15-081")).toBeInTheDocument();
    expect(searchSessionTicketsMock).toHaveBeenLastCalledWith(15, undefined, 0, 20);

    await user.type(screen.getByRole("textbox", { name: "Search tickets" }), "Alice");
    await waitFor(() => {
      expect(searchSessionTicketsMock).toHaveBeenLastCalledWith(15, "Alice", 0, 20);
    });
  });
});
