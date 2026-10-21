import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { adminAgents, reviewFloatTopUpRequest } from "../../api/admin";
import { AgentsPage } from "./AgentsPage";

vi.mock("../../api/admin", () => ({
  adminAgents: vi.fn(),
  approveAgent: vi.fn(),
  commissionRule: vi.fn(),
  reviewFloatTopUpRequest: vi.fn(),
  suspendAgent: vi.fn(),
}));

const adminAgentsMock = vi.mocked(adminAgents);
const reviewFloatTopUpRequestMock = vi.mocked(reviewFloatTopUpRequest);

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
});
