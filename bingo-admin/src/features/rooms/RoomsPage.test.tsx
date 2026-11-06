import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { listAdminRooms, updateRoom } from "../../api/admin";
import { RoomsPage } from "./RoomsPage";

vi.mock("../../api/admin", () => ({
  createRoom: vi.fn(),
  listAdminRooms: vi.fn(),
  updateRoom: vi.fn(),
}));

const listAdminRoomsMock = vi.mocked(listAdminRooms);
const updateRoomMock = vi.mocked(updateRoom);

function createWrapper({ children }: PropsWithChildren) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

describe("RoomsPage", () => {
  afterEach(cleanup);

  beforeEach(() => {
    listAdminRoomsMock.mockReset();
    updateRoomMock.mockReset();
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

  it("enables save for an edited room only while fields differ from the original", async () => {
    const user = userEvent.setup();
    render(<RoomsPage />, { wrapper: createWrapper });

    await user.click(await screen.findByRole("button", { name: "Edit" }));
    const dialog = await screen.findByRole("dialog", { name: "Edit room" });
    const saveButton = within(dialog).getByRole("button", { name: "Save" });
    expect(saveButton).toBeDisabled();

    const nameField = within(dialog).getByRole("textbox", { name: "Room name" });
    await user.clear(nameField);
    await user.type(nameField, "Updated Room");
    expect(saveButton).toBeEnabled();
    await user.clear(nameField);
    await user.type(nameField, "Evening Room");
    expect(saveButton).toBeDisabled();
    expect(updateRoomMock).not.toHaveBeenCalled();
  });
});
