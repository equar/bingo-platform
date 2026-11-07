import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getAdminRoom, listAdminRooms, updateRoom } from "../../api/admin";
import { RoomsPage } from "./RoomsPage";

vi.mock("../../api/admin", () => ({
  createRoom: vi.fn(),
  getAdminRoom: vi.fn(),
  listAdminRooms: vi.fn(),
  updateRoom: vi.fn(),
}));

const getAdminRoomMock = vi.mocked(getAdminRoom);
const listAdminRoomsMock = vi.mocked(listAdminRooms);
const updateRoomMock = vi.mocked(updateRoom);

function createWrapperFor(initialEntry: string) {
  return function Wrapper({ children }: PropsWithChildren) {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return (
      <MemoryRouter initialEntries={[initialEntry]}>
        <QueryClientProvider client={queryClient}>
          <Routes>
            <Route path="/rooms" element={children} />
            <Route path="/rooms/new" element={children} />
            <Route path="/rooms/:roomId/edit" element={children} />
          </Routes>
        </QueryClientProvider>
      </MemoryRouter>
    );
  };
}

const createWrapper = createWrapperFor("/rooms");

describe("RoomsPage", () => {
  afterEach(() => {
    cleanup();
    window.sessionStorage.removeItem("admin-list-search:rooms");
  });

  beforeEach(() => {
    getAdminRoomMock.mockReset();
    listAdminRoomsMock.mockReset();
    updateRoomMock.mockReset();
    getAdminRoomMock.mockResolvedValue({
      id: 7,
      version: 1,
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
    });
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

  it("shows a back link in the editor header", async () => {
    render(<RoomsPage />, { wrapper: createWrapperFor("/rooms/new") });

    expect(await screen.findByRole("button", { name: "← Rooms" })).toBeInTheDocument();
  });

  it("offers common 75-ball patterns in prize setup", async () => {
    const user = userEvent.setup();
    render(<RoomsPage />, { wrapper: createWrapperFor("/rooms/new") });

    await user.click(await screen.findByRole("combobox", { name: "Pattern" }));

    for (const label of [
      "Four corners",
      "Postage stamp",
      "X shape",
      "T shape",
      "L shape",
      "Outer frame",
      "Full house",
    ]) {
      expect(await screen.findByRole("option", { name: label })).toBeInTheDocument();
    }
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
    expect(await screen.findByRole("heading", { name: "Edit room" })).toBeInTheDocument();
    const saveButton = screen.getByRole("button", { name: "Save changes" });
    expect(saveButton).toBeDisabled();

    const nameField = screen.getByRole("textbox", { name: "Room name" });
    await user.clear(nameField);
    await user.type(nameField, "Updated Room");
    expect(saveButton).toBeEnabled();
    await user.clear(nameField);
    await user.type(nameField, "Evening Room");
    expect(saveButton).toBeDisabled();
    expect(updateRoomMock).not.toHaveBeenCalled();
  });
});
