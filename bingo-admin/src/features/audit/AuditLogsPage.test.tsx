import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuditLogsPage } from "./AuditLogsPage";

const auditLogsMock = vi.fn();
const auditLogDetailMock = vi.fn();

vi.mock("../../api/admin", () => ({
  auditLogs: (...args: unknown[]) => auditLogsMock(...args),
  auditLogDetail: (...args: unknown[]) => auditLogDetailMock(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

describe("AuditLogsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    auditLogsMock.mockResolvedValue({
      content: [
        {
          id: 42,
          actorUserId: 7,
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
    expect(auditLogsMock).toHaveBeenCalledWith(0, 20);

    await user.click(screen.getByRole("button", { name: "Go to next page" }));

    await waitFor(() => expect(auditLogsMock).toHaveBeenLastCalledWith(1, 20));
  });
});
