import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AppShell } from "./AppShell";

const useSessionMock = vi.fn();

vi.mock("../api/session", () => ({
  useSession: () => useSessionMock(),
}));

describe("AppShell", () => {
  beforeEach(() => {
    useSessionMock.mockReturnValue({
      accessToken: "token",
      tokenType: "Bearer",
      expiresInSeconds: 3600,
      user: {
        id: 1,
        displayName: "Admin Tester",
        phoneNumber: "251911000001",
        roles: ["ADMIN"],
        status: "ACTIVE",
        createdAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z",
      },
    });
  });

  it("renders grouped navigation and the current user", () => {
    render(
      <MemoryRouter initialEntries={["/management"]}>
        <Routes>
          <Route element={<AppShell />}>
            <Route path="management" element={<div>Management content</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText("Bingo Admin")).toBeInTheDocument();
    expect(screen.getByText("Admin Tester")).toBeInTheDocument();
    expect(screen.getByText("Management")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Admin Users" })).toBeInTheDocument();
    expect(screen.getByText("Operations")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Dashboard" })).toBeInTheDocument();
    expect(screen.getByText("Management content")).toBeInTheDocument();
  });
});