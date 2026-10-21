import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { listAdminRooms } from "../../api/admin";
import { RoomsPage } from "./RoomsPage";

vi.mock("../../api/admin", () => ({
  createRoom: vi.fn(),
  listAdminRooms: vi.fn(),
  updateRoom: vi.fn(),
}));

const listAdminRoomsMock = vi.mocked(listAdminRooms);

function createWrapper({ children }: PropsWithChildren) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

describe("RoomsPage", () => {
  afterEach(cleanup);

  beforeEach(() => {
    listAdminRoomsMock.mockReset();
    listAdminRoomsMock.mockResolvedValue({
      content: [
        {
          id: 7,
          name: "Evening Room",
          description: "Daily bingo",
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
      ],
      page: 0,
      size: 20,
      totalElements: 35,
      totalPages: 2,
      first: true,
      last: false,
    });
  });

  it("forwards search, status, and pagination to the admin rooms API", async () => {
    const user = userEvent.setup();
    render(<RoomsPage />, { wrapper: createWrapper });

    expect(await screen.findByText("Evening Room")).toBeInTheDocument();
    expect(listAdminRoomsMock).toHaveBeenCalledWith(0, 20, undefined, undefined);

    await user.type(screen.getByRole("textbox", { name: "Search rooms" }), "Evening");
    await waitFor(() => expect(listAdminRoomsMock).toHaveBeenLastCalledWith(0, 20, "Evening", undefined));

    await user.click(screen.getByRole("combobox", { name: "Status" }));
    await user.click(await screen.findByRole("option", { name: "Active only" }));
    await waitFor(() => expect(listAdminRoomsMock).toHaveBeenLastCalledWith(0, 20, "Evening", true));

    await user.click(screen.getByRole("button", { name: "Go to next page" }));
    await waitFor(() => expect(listAdminRoomsMock).toHaveBeenLastCalledWith(1, 20, "Evening", true));
  });
});
