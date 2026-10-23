import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { adminAgents, currentCommissionRule, reviewFloatTopUpRequest } from "../../api/admin";
import { AgentsPage } from "./AgentsPage";

vi.mock("../../api/admin", () => ({
  adminAgents: vi.fn(),
  approveAgent: vi.fn(),
  commissionRule: vi.fn(),
  currentCommissionRule: vi.fn(),
  reviewFloatTopUpRequest: vi.fn(),
  suspendAgent: vi.fn(),
}));

const adminAgentsMock = vi.mocked(adminAgents);
const reviewFloatTopUpRequestMock = vi.mocked(reviewFloatTopUpRequest);
const currentCommissionRuleMock = vi.mocked(currentCommissionRule);

function createWrapper({ children }: PropsWithChildren) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe("AgentsPage float top-up review", () => {
  afterEach(cleanup);

  beforeEach(() => {
    adminAgentsMock.mockReset().mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    });
    reviewFloatTopUpRequestMock.mockReset();
    currentCommissionRuleMock.mockReset().mockResolvedValue({
      id: null, versionNumber: 0, commissionRateBps: 0, effectiveFrom: "1970-01-01T00:00:00Z",
      active: false, createdAt: null, updatedAt: null,
    });
  });

  it("requires a positive whole request id and a review note", async () => {
    const user = userEvent.setup();
    render(<AgentsPage />, { wrapper: createWrapper });
    await screen.findByText("Float top-up review");

    const reviewButton = screen.getByRole("button", { name: "Review request" });
    expect(reviewButton).toBeDisabled();

    await user.type(screen.getByRole("spinbutton", { name: "Request id" }), "1.5");
    expect(screen.getByText("Enter a positive whole number.")).toBeInTheDocument();
    expect(reviewButton).toBeDisabled();

    await user.clear(screen.getByRole("spinbutton", { name: "Request id" }));
    await user.type(screen.getByRole("spinbutton", { name: "Request id" }), "17");
    expect(reviewButton).toBeDisabled();

    await user.type(screen.getByRole("textbox", { name: "Review note" }), "Reviewed");
    expect(reviewButton).toBeEnabled();
  });

  it("submits one review for rapid repeated clicks and disables the action while pending", async () => {
    const user = userEvent.setup();
    reviewFloatTopUpRequestMock.mockReturnValue(new Promise(() => {}));
    render(<AgentsPage />, { wrapper: createWrapper });
    await screen.findByText("Float top-up review");
    await user.type(screen.getByRole("spinbutton", { name: "Request id" }), "17");
    await user.type(screen.getByRole("textbox", { name: "Review note" }), "Verified");

    const reviewButton = screen.getByRole("button", { name: "Review request" });
    await user.click(reviewButton);
    await waitFor(() => expect(reviewButton).toBeDisabled());
    fireEvent.click(reviewButton);

    expect(reviewFloatTopUpRequestMock).toHaveBeenCalledTimes(1);
    expect(reviewFloatTopUpRequestMock).toHaveBeenCalledWith(17, {
      approve: true,
      reviewNote: "Verified",
    });
  });

  it("shows API permission and review errors", async () => {
    const user = userEvent.setup();
    reviewFloatTopUpRequestMock.mockRejectedValue(new Error("Permission denied"));
    render(<AgentsPage />, { wrapper: createWrapper });
    await screen.findByText("Float top-up review");
    await user.type(screen.getByRole("spinbutton", { name: "Request id" }), "17");
    await user.type(screen.getByRole("textbox", { name: "Review note" }), "Verified");
    await user.click(screen.getByRole("button", { name: "Review request" }));

    expect(await screen.findByText("Permission denied")).toBeInTheDocument();
    expect(reviewFloatTopUpRequestMock).toHaveBeenCalledTimes(1);
  });

  it("loads the persisted current commission rule", async () => {
    currentCommissionRuleMock.mockResolvedValue({
      id: 8, versionNumber: 3, commissionRateBps: 0, effectiveFrom: "2000-01-01T00:00:00Z",
      active: false, createdAt: "2000-01-01T00:00:00Z", updatedAt: "2000-01-01T00:00:00Z",
    });
    render(<AgentsPage />, { wrapper: createWrapper });
    expect(await screen.findByText("Current version 3 · Inactive")).toBeInTheDocument();
    expect(screen.getByRole("spinbutton", { name: "Commission rate (bps)" })).toHaveValue(0);
    expect(screen.getByRole("checkbox", { name: "Active" })).not.toBeChecked();
  });
});
