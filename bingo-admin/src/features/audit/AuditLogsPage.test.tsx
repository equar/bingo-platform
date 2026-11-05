import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AuditLogsPage } from "./AuditLogsPage";

const searchAuditLogsMock = vi.fn();
const auditLogDetailMock = vi.fn();

vi.mock("../../api/admin", () => ({
  searchAuditLogs: (...args: unknown[]) => searchAuditLogsMock(...args),
  auditLogDetail: (...args: unknown[]) => auditLogDetailMock(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

describe("AuditLogsPage", () => {
  afterEach(() => cleanup());

  beforeEach(() => {
    vi.clearAllMocks();
    searchAuditLogsMock.mockResolvedValue({
      content: [
        {
          id: 42,
          actorUserId: 7,
          actorDisplayName: "Admin Example",
          action: "UPDATE_SESSION_STATE",
          targetType: "GameSession",
          targetId: 91,
          reason: "Operator closed the draw",
          operationId: "op-42",
          createdAt: "2026-01-01T12:00:00.000Z",
        },
      ],
      page: 0,
      size: 100,
      totalElements: 45,
      totalPages: 3,
      first: true,
      last: false,
    });
    auditLogDetailMock.mockResolvedValue({
      id: 42,
      actorUserId: 7,
      actorDisplayName: "Admin Example",
      action: "UPDATE_SESSION_STATE",
      targetType: "GameSession",
      targetId: 91,
      reason: "Operator closed the draw",
      operationId: "op-42",
      metadataJson: JSON.stringify({
        schemaVersion: 1,
        outcome: "SUCCESS",
        method: "updateSessionState",
        arguments: { sessionId: 91, request: { state: "CLOSED" } },
        result: { id: 91, state: "CLOSED" },
        request: { correlationId: "request-42", httpMethod: "PUT", path: "/api/v1/admin/sessions/91/state" },
      }),
      createdAt: "2026-01-01T12:00:00.000Z",
    });
  });

  it("shows captured request, argument, and result context in the details dialog", async () => {
    const user = userEvent.setup();
    render(<AuditLogsPage />, { wrapper: createWrapper() });

    const viewButton = await screen.findByRole("button", { name: "View details" });
    await user.click(viewButton);

    const dialog = await screen.findByRole("dialog", { name: "Audit log details" });
    expect(within(dialog).getByText("Recorded context")).toBeInTheDocument();
    expect(within(dialog).getByText(/"correlationId": "request-42"/)).toBeInTheDocument();
    expect(within(dialog).getByText(/"state": "CLOSED"/)).toBeInTheDocument();
    expect(auditLogDetailMock).toHaveBeenCalledWith(42);
  });

  it("requests the next server page when pagination advances", async () => {
    const user = userEvent.setup();
    render(<AuditLogsPage />, { wrapper: createWrapper() });

    expect(await screen.findByRole("button", { name: "Go to next page" })).toBeEnabled();
    expect(searchAuditLogsMock).toHaveBeenCalledWith(0, 20, {});

    await user.click(screen.getByRole("button", { name: "Go to next page" }));

    await waitFor(() => expect(searchAuditLogsMock).toHaveBeenLastCalledWith(1, 20, {}));
  });

  it("sends search filters to the server instead of filtering only the current page", async () => {
    const user = userEvent.setup();
    render(<AuditLogsPage />, { wrapper: createWrapper() });

    await screen.findByText(/45 matching entries across all pages/);
    await user.type(screen.getByRole("textbox", { name: "Search all audit fields" }), "transfer");
    await user.click(screen.getByRole("button", { name: "Search audit logs" }));

    await waitFor(() => {
      expect(searchAuditLogsMock).toHaveBeenLastCalledWith(0, 20, { query: "transfer" });
    });
  });

  it("keeps search controls available when the audit history is empty", async () => {
    searchAuditLogsMock.mockResolvedValueOnce({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    });
    render(<AuditLogsPage />, { wrapper: createWrapper() });

    expect(await screen.findByText("No audit entries found.")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Search all audit fields" })).toBeInTheDocument();
  });
});
