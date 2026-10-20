import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { listRooms, listSessions } from "../../api/admin";
import { SessionsPage } from "./SessionsPage";

vi.mock("../../api/admin", () => ({
  createSession: vi.fn(),
  listRooms: vi.fn(),
  listSessions: vi.fn(),
  updateSessionState: vi.fn(),
}));

const listRoomsMock = vi.mocked(listRooms);
const listSessionsMock = vi.mocked(listSessions);

function createWrapper({ children }: PropsWithChildren) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe("SessionsPage", () => {
  afterEach(cleanup);

  beforeEach(() => {
    listRoomsMock.mockReset();
    listSessionsMock.mockReset();
    listRoomsMock.mockResolvedValue([
      {
        id: 4,
        name: "Evening Room",
        description: null,
        ticketPrice: "10.00",
        currency: "ETB",
        timezone: "Africa/Addis_Ababa",
        scheduleRule: "DAILY",
        sessionCapacity: 100,
        maxTicketsPerPlayer: 5,
        ticketSalesCloseOnSessionStart: true,
        allowMultipleWinnersPerPrize: true,
        active: true,
        configuredPatterns: ["LINE"],
        configuredPrizes: [],
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
      },
    ]);
    listSessionsMock.mockResolvedValue({
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
      totalElements: 42,
      totalPages: 3,
      first: true,
      last: false,
    });
  });

  it("loads session rows and forwards search and server-side filters", async () => {
    const user = userEvent.setup();
    render(<SessionsPage />, { wrapper: createWrapper });

    expect(await screen.findByRole("link", { name: "Friday Draw" })).toBeInTheDocument();
    expect(listSessionsMock).toHaveBeenCalledWith(0, 20, undefined, undefined, undefined, undefined);

    await user.type(screen.getByRole("textbox", { name: "Search sessions" }), "Friday");
    await waitFor(() =>
      expect(listSessionsMock).toHaveBeenLastCalledWith(0, 20, "Friday", undefined, undefined, undefined),
    );

    await user.click(screen.getByRole("combobox", { name: "Status" }));
    await user.click(await screen.findByRole("option", { name: "OPEN" }));
    await waitFor(() =>
      expect(listSessionsMock).toHaveBeenLastCalledWith(0, 20, "Friday", "OPEN", undefined, undefined),
    );

    await user.click(screen.getByRole("combobox", { name: "Room" }));
    await user.click(await screen.findByRole("option", { name: "Evening Room" }));
    await waitFor(() => expect(listSessionsMock).toHaveBeenLastCalledWith(0, 20, "Friday", "OPEN", 4, undefined));

    await user.click(screen.getByRole("combobox", { name: "Start date" }));
    await user.click(await screen.findByRole("option", { name: "Upcoming" }));
    await waitFor(() => expect(listSessionsMock).toHaveBeenLastCalledWith(0, 20, "Friday", "OPEN", 4, true));
  });

  it("requests the next server page", async () => {
    const user = userEvent.setup();
    render(<SessionsPage />, { wrapper: createWrapper });

    await screen.findByRole("link", { name: "Friday Draw" });
    await user.click(screen.getByRole("button", { name: "Go to next page" }));

    await waitFor(() =>
      expect(listSessionsMock).toHaveBeenLastCalledWith(1, 20, undefined, undefined, undefined, undefined),
    );
  });
});
