import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { postDraw, sessionDetail, sessionDraws } from "../../api/admin";
import { SessionDetailPage } from "./SessionDetailPage";

vi.mock("../../api/admin", () => ({
  postDraw: vi.fn(),
  sessionDetail: vi.fn(),
  sessionDraws: vi.fn(),
  updateSessionState: vi.fn(),
}));

vi.mock("../../api/session", () => ({
  useSession: () => ({
    accessToken: "test-token",
    user: { roles: ["ADMIN"] },
  }),
}));

vi.mock("./liveDrawSocket", () => ({
  connectLiveDrawStream: () => ({ close: vi.fn() }),
}));

describe("SessionDetailPage automatic draws", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(sessionDetail).mockResolvedValue({
      id: 9,
      roomId: 2,
      roomName: "Main Hall",
      sessionName: "Evening Bingo",
      state: "STARTED",
      scheduledStartAt: "2026-10-25T10:00:00Z",
      startedAt: "2026-10-25T10:00:00Z",
      endedAt: null,
      ticketPrice: "4.00",
      currency: "ETB",
      sessionCapacity: 50,
      maxTicketsPerPlayer: 10,
      ticketSalesCloseOnSessionStart: true,
      allowMultipleWinnersPerPrize: false,
      configuredPatterns: ["LINE"],
      configuredPrizes: [],
      ticketCount: 10,
      drawCount: 0,
      createdAt: "2026-10-25T09:00:00Z",
      updatedAt: "2026-10-25T10:00:00Z",
    });
    vi.mocked(sessionDraws).mockResolvedValue([]);
    vi.mocked(postDraw).mockResolvedValue({
      id: 41,
      sessionId: 9,
      sequenceNo: 1,
      numberDrawn: 57,
      operationId: "draw-op-41",
      drawnAt: "2026-10-25T10:00:10Z",
    });
  });

  it("has no number entry and displays the server-selected result", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/sessions/9"]}>
          <Routes>
            <Route path="/sessions/:sessionId" element={<SessionDetailPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    const drawButton = await screen.findByRole("button", { name: "Draw next number" });
    expect(screen.queryByLabelText(/number drawn/i)).not.toBeInTheDocument();
    fireEvent.click(drawButton);

    await waitFor(() => expect(postDraw).toHaveBeenCalledWith(9));
    expect(await screen.findByTestId("automatic-draw-result")).toHaveTextContent("Server drew 57 at sequence 1.");
  });

  it("prevents a second request while the automatic draw is pending", async () => {
    vi.mocked(postDraw).mockReturnValue(new Promise(() => {}));
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/sessions/9"]}>
          <Routes>
            <Route path="/sessions/:sessionId" element={<SessionDetailPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    const drawButton = await screen.findByRole("button", { name: "Draw next number" });
    fireEvent.click(drawButton);
    const pendingButton = await screen.findByRole("button", { name: "Drawing…" });
    expect(pendingButton).toBeDisabled();
    fireEvent.click(pendingButton);
    expect(postDraw).toHaveBeenCalledTimes(1);
  });
});
