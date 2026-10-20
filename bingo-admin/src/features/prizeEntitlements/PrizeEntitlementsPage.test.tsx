import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { prizeEntitlements, settlePrizeEntitlement } from "../../api/admin";
import { PrizeEntitlementsPage } from "./PrizeEntitlementsPage";

vi.mock("../../api/admin", () => ({
  prizeEntitlements: vi.fn(),
  settlePrizeEntitlement: vi.fn(),
}));

const prizeEntitlementsMock = vi.mocked(prizeEntitlements);
const settlePrizeEntitlementMock = vi.mocked(settlePrizeEntitlement);

const entitlement = {
  id: 24,
  winClaimId: 12,
  sessionId: 8,
  ticketId: 44,
  claimantId: 31,
  prizeType: "FULL_HOUSE",
  settlementPath: "EXTERNAL_AGENT",
  status: "PENDING",
  prizeAmount: "500.00",
  currency: "ETB",
  operationId: "entitlement-op-24",
  createdAt: "2026-10-20T15:07:22Z",
  settledAt: null,
  settledBy: null,
  settlementReference: null,
  settlementNote: null,
};

function createWrapper({ children }: PropsWithChildren) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

describe("PrizeEntitlementsPage", () => {
  afterEach(cleanup);

  beforeEach(() => {
    prizeEntitlementsMock.mockReset();
    settlePrizeEntitlementMock.mockReset();
    prizeEntitlementsMock.mockResolvedValue({
      content: [entitlement, { ...entitlement, id: 25, status: "SETTLED" }],
      page: 0,
      size: 20,
      totalElements: 42,
      totalPages: 3,
      first: true,
      last: false,
    });
    settlePrizeEntitlementMock.mockResolvedValue({ ...entitlement, status: "SETTLED" });
  });

  it("forwards search, status, and pagination to the server", async () => {
    const user = userEvent.setup();
    render(<PrizeEntitlementsPage />, { wrapper: createWrapper });

    expect(await screen.findByTestId("entitlement-row-24")).toBeInTheDocument();
    expect(prizeEntitlementsMock).toHaveBeenCalledWith(0, 20, undefined, undefined);

    await user.type(screen.getByRole("textbox", { name: "Search entitlements" }), "entitlement-op");
    await waitFor(() => expect(prizeEntitlementsMock).toHaveBeenLastCalledWith(0, 20, "entitlement-op", undefined));

    await user.click(screen.getByRole("combobox", { name: "Status" }));
    await user.click(await screen.findByRole("option", { name: "Pending" }));
    await waitFor(() => expect(prizeEntitlementsMock).toHaveBeenLastCalledWith(0, 20, "entitlement-op", "PENDING"));

    await user.click(screen.getByRole("button", { name: "Go to next page" }));
    await waitFor(() => expect(prizeEntitlementsMock).toHaveBeenLastCalledWith(1, 20, "entitlement-op", "PENDING"));
  });

  it("keeps settlement available only for pending entitlements", async () => {
    const user = userEvent.setup();
    render(<PrizeEntitlementsPage />, { wrapper: createWrapper });

    await screen.findByTestId("entitlement-row-24");
    expect(screen.getByTestId("entitlement-settle-25")).toBeDisabled();
    await user.click(screen.getByTestId("entitlement-settle-24"));
    await user.type(screen.getByRole("textbox", { name: "Settlement reference" }), "PAYOUT-24");
    await user.click(screen.getByRole("button", { name: "Confirm settlement" }));

    await waitFor(() =>
      expect(settlePrizeEntitlementMock).toHaveBeenCalledWith(24, {
        settlementReference: "PAYOUT-24",
        settlementNote: undefined,
      }),
    );
  });
});
