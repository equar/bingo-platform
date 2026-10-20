import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { winClaims } from "../../api/admin";
import { ClaimsPage } from "./ClaimsPage";

vi.mock("../../api/admin", () => ({
  approveWinClaim: vi.fn(),
  rejectWinClaim: vi.fn(),
  winClaims: vi.fn(),
}));

const winClaimsMock = vi.mocked(winClaims);

function createWrapper({ children }: PropsWithChildren) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe("ClaimsPage", () => {
  afterEach(cleanup);

  beforeEach(() => {
    winClaimsMock.mockReset();
    winClaimsMock.mockResolvedValue({
      content: [
        {
          id: 21,
          ticketId: 81,
          sessionId: 15,
          claimantId: 10,
          claimedPattern: "LINE",
          status: "PENDING_REVIEW",
          serverValidationPassed: true,
          serverValidationCode: null,
          serverValidationMessage: null,
          drawSequenceSnapshot: 42,
          calledNumbersSnapshot: [1, 2, 3],
          evidenceJson: "{}",
          operationId: "claim-operation-21",
          reviewNote: null,
          reviewedBy: null,
          reviewedAt: null,
          entitlementId: null,
          createdAt: "2026-10-20T15:07:22Z",
          updatedAt: "2026-10-20T15:07:22Z",
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

  it("loads claims into the table and preserves review actions", async () => {
    render(<ClaimsPage />, { wrapper: createWrapper });

    expect(await screen.findByTestId("claim-row-21")).toBeInTheDocument();
    expect(screen.getByText("Ticket 81")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Approve" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Reject" })).toBeEnabled();
    expect(winClaimsMock).toHaveBeenCalledWith(0, 20, undefined, undefined, undefined, undefined);
  });

  it("passes search and selected filters to the server and resets to page zero", async () => {
    const user = userEvent.setup();
    render(<ClaimsPage />, { wrapper: createWrapper });

    await screen.findByTestId("claim-row-21");
    await user.type(screen.getByRole("textbox", { name: "Search claim, player, ticket, or session" }), "player");
    await waitFor(() =>
      expect(winClaimsMock).toHaveBeenLastCalledWith(0, 20, "player", undefined, undefined, undefined),
    );

    await user.click(screen.getByRole("combobox", { name: "Status" }));
    await user.click(await screen.findByRole("option", { name: "APPROVED" }));
    await waitFor(() =>
      expect(winClaimsMock).toHaveBeenLastCalledWith(0, 20, "player", "APPROVED", undefined, undefined),
    );

    await user.click(screen.getByRole("combobox", { name: "Pattern" }));
    await user.click(await screen.findByRole("option", { name: "FULL HOUSE" }));
    await waitFor(() =>
      expect(winClaimsMock).toHaveBeenLastCalledWith(0, 20, "player", "APPROVED", "FULL_HOUSE", undefined),
    );

    await user.click(screen.getByRole("combobox", { name: "Validation" }));
    await user.click(await screen.findByRole("option", { name: "Passed" }));
    await waitFor(() =>
      expect(winClaimsMock).toHaveBeenLastCalledWith(0, 20, "player", "APPROVED", "FULL_HOUSE", true),
    );
  });

  it("requests the next server page", async () => {
    const user = userEvent.setup();
    render(<ClaimsPage />, { wrapper: createWrapper });

    await screen.findByTestId("claim-row-21");
    await user.click(screen.getByRole("button", { name: "Go to next page" }));

    await waitFor(() =>
      expect(winClaimsMock).toHaveBeenLastCalledWith(1, 20, undefined, undefined, undefined, undefined),
    );
  });
});
