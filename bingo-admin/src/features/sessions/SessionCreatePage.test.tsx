import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createSession, listRooms } from "../../api/admin";
import i18n from "../../i18n";
import { SessionCreatePage } from "./SessionCreatePage";

vi.mock("../../api/admin", () => ({
  createSession: vi.fn(),
  listRooms: vi.fn(),
}));

const createSessionMock = vi.mocked(createSession);
const listRoomsMock = vi.mocked(listRooms);

function createWrapper({ children }: PropsWithChildren) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/sessions/new"]}>
        <Routes>
          <Route path="/sessions" element={<div>Sessions list</div>} />
          <Route path="/sessions/new" element={children} />
          <Route path="/sessions/:sessionId" element={<div>Created session details</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("SessionCreatePage", () => {
  afterEach(async () => {
    cleanup();
    await i18n.changeLanguage("en");
  });

  beforeEach(async () => {
    await i18n.changeLanguage("en");
    createSessionMock.mockReset();
    listRoomsMock.mockReset();
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
    createSessionMock.mockResolvedValue({
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
      sessionCapacity: 100,
      maxTicketsPerPlayer: 5,
      ticketSalesCloseOnSessionStart: true,
      allowMultipleWinnersPerPrize: true,
      configuredPatterns: ["LINE"],
      configuredPrizes: [],
      ticketCount: 0,
      drawCount: 0,
      createdAt: "2026-10-21T15:00:00Z",
      updatedAt: "2026-10-21T15:00:00Z",
    });
  });

  it("creates a session from its details page and opens the new session", async () => {
    const user = userEvent.setup();
    render(<SessionCreatePage />, { wrapper: createWrapper });

    await user.click(await screen.findByRole("combobox", { name: "Room" }));
    await user.click(await screen.findByRole("option", { name: "Evening Room" }));
    await user.type(screen.getByRole("textbox", { name: "Session name" }), "Friday Draw");
    await user.click(screen.getByRole("button", { name: "Create session" }));

    await waitFor(() =>
      expect(createSessionMock).toHaveBeenCalledWith(
        4,
        expect.objectContaining({
          sessionName: "Friday Draw",
          state: "OPEN",
          scheduledStartAt: expect.any(String),
        }),
      ),
    );
    expect(await screen.findByText("Created session details")).toBeInTheDocument();
  });

  it("offers room creation when no rooms are available", async () => {
    listRoomsMock.mockResolvedValue([]);
    render(<SessionCreatePage />, { wrapper: createWrapper });

    expect(
      await screen.findByText("No rooms are available. Create a room before scheduling a session."),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Create room" })).toHaveAttribute("href", "/rooms/new");
    expect(screen.getByRole("button", { name: "Create session" })).toBeDisabled();
  });

  it("asks before discarding edited fields and returns focus when editing continues", async () => {
    const user = userEvent.setup();
    render(<SessionCreatePage />, { wrapper: createWrapper });
    const nameInput = await screen.findByRole("textbox", { name: "Session name" });
    await user.type(nameInput, "Keep this draft");
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    const dialog = screen.getByRole("dialog", { name: "Discard unsaved changes?" });
    await user.click(within(dialog).getByRole("button", { name: "Keep editing" }));
    expect(nameInput).toHaveValue("Keep this draft");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Cancel" })).toHaveFocus();

    await user.click(screen.getByRole("button", { name: "Cancel" }));
    await user.click(within(screen.getByRole("dialog", { name: "Discard unsaved changes?" })).getByRole("button", { name: "Discard" }));
    expect(await screen.findByText("Sessions list")).toBeInTheDocument();
  });

  it("keeps entered session details after a create API failure", async () => {
    createSessionMock.mockRejectedValueOnce(new Error("Failed"));
    const user = userEvent.setup();
    render(<SessionCreatePage />, { wrapper: createWrapper });

    await user.click(await screen.findByRole("combobox", { name: "Room" }));
    await user.click(await screen.findByRole("option", { name: "Evening Room" }));
    const nameInput = screen.getByRole("textbox", { name: "Session name" });
    await user.type(nameInput, "Retry session");
    await user.click(screen.getByRole("button", { name: "Create session" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Something went wrong. Please try again.");
    expect(nameInput).toHaveValue("Retry session");
  });
});
