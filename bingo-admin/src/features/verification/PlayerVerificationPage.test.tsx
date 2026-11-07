import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  playerVerificationHistory,
  recordPlayerVerificationReview,
  searchPlayerVerifications,
} from "../../api/admin";
import { PlayerVerificationPage } from "./PlayerVerificationPage";

vi.mock("../../api/admin", () => ({
  playerVerificationHistory: vi.fn(),
  recordPlayerVerificationReview: vi.fn(),
  searchPlayerVerifications: vi.fn(),
}));

const player = {
  playerId: 25,
  displayName: "Test Player",
  phoneNumber: "251911000025",
  accountStatus: "ACTIVE",
  verificationStatus: "IN_REVIEW" as const,
  verificationReason: null,
  reviewedByUserId: null,
  reviewedAt: null,
  createdAt: "2026-01-01T00:00:00.000Z",
};

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  };
}

describe("PlayerVerificationPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(searchPlayerVerifications).mockResolvedValue({
      content: [player],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });
    vi.mocked(playerVerificationHistory).mockResolvedValue([]);
    vi.mocked(recordPlayerVerificationReview).mockResolvedValue({
      id: 7,
      playerId: player.playerId,
      reviewerUserId: 1,
      status: "VERIFIED",
      reason: "IDENTITY_AND_AGE_CONFIRMED",
      reviewedAt: "2026-01-02T00:00:00.000Z",
    });
  });

  afterEach(() => {
    cleanup();
    window.sessionStorage.removeItem("admin-list-search:player-verification");
  });

  it("explains manual review, lists players, and records verified status with its matching reason", async () => {
    const user = userEvent.setup();
    render(<PlayerVerificationPage />, { wrapper: createWrapper() });

    expect(await screen.findByRole("heading", { name: "Player verification" })).toBeInTheDocument();
    expect(screen.getByText(/do not enter or upload identity documents/i)).toBeInTheDocument();
    expect(await screen.findByText("Test Player")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Review player" }));
    expect(await screen.findByRole("heading", { name: /Record verification review/ })).toBeInTheDocument();
    expect(screen.getByText("No review history")).toBeInTheDocument();

    await user.click(screen.getByRole("combobox", { name: "Choose status" }));
    await user.click(screen.getByRole("option", { name: "Verified" }));
    expect(screen.getByRole("combobox", { name: "Choose a reason" })).toHaveTextContent(
      "Identity and age confirmed",
    );
    await user.click(screen.getByRole("button", { name: "Save review" }));

    await waitFor(() => {
      expect(recordPlayerVerificationReview).toHaveBeenCalledWith(player.playerId, {
        status: "VERIFIED",
        reason: "IDENTITY_AND_AGE_CONFIRMED",
      });
    });
    expect(await screen.findByText("Review recorded")).toBeInTheDocument();
  });

  it("preserves the form for retry when saving fails", async () => {
    vi.mocked(recordPlayerVerificationReview)
      .mockRejectedValueOnce(new Error("network failure"))
      .mockResolvedValueOnce({
        id: 8,
        playerId: player.playerId,
        reviewerUserId: 1,
        status: "IN_REVIEW",
        reason: "REVIEW_STARTED",
        reviewedAt: "2026-01-02T00:00:00.000Z",
      });

    const user = userEvent.setup();
    render(<PlayerVerificationPage />, { wrapper: createWrapper() });
    await user.click(await screen.findByRole("button", { name: "Review player" }));
    await user.click(screen.getByRole("button", { name: "Save review" }));

    expect(await screen.findByText("Something went wrong. Please try again.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save review" })).toBeEnabled();
    await user.click(screen.getByRole("button", { name: "Save review" }));
    await waitFor(() => expect(recordPlayerVerificationReview).toHaveBeenCalledTimes(2));
  });
});
