import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { WinningTicketCard } from "./WinningTicketCard";

describe("WinningTicketCard", () => {
  it("highlights only the claimed four-corners pattern", () => {
    render(
      <WinningTicketCard
        t={(key) => key}
        ticket={{
          id: 42,
          sessionId: 15,
          ownerId: 17,
          publicTicketCode: "TK-15-EXAMPLE",
          operationId: "ticket-op",
          purchasedAt: "2026-10-20T00:00:00Z",
          grid: [
            [5, 17, 44, 52, 63],
            [4, 19, 58, 73, 75],
            [6, 21, 0, 57, 71],
            [2, 18, 36, 46, 62],
            [15, 16, 43, 47, 61],
          ],
        }}
        claim={{
          id: 12,
          ticketId: 42,
          sessionId: 15,
          claimantId: 17,
          claimedPattern: "FOUR_CORNERS",
          status: "PENDING_REVIEW",
          serverValidationPassed: true,
          serverValidationCode: "VALID",
          serverValidationMessage: "Validated",
          drawSequenceSnapshot: 20,
          calledNumbersSnapshot: [5, 63, 15, 61, 17],
          evidenceJson: null,
          operationId: "claim-op",
          reviewNote: null,
          reviewedBy: null,
          reviewedAt: null,
          entitlementId: null,
          createdAt: "2026-10-20T00:00:00Z",
          updatedAt: "2026-10-20T00:00:00Z",
        }}
      />,
    );

    expect(screen.getByText("Winning pattern: Four corners")).toBeInTheDocument();
    expect(screen.getAllByLabelText(/Winning cells$/)).toHaveLength(4);
    expect(screen.getByLabelText("17, Called")).toBeInTheDocument();
    expect(screen.queryByLabelText("0, Winning cells")).not.toBeInTheDocument();
  });
});
