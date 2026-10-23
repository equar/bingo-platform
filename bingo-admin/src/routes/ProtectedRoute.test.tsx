import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi, beforeEach } from "vitest";
import { clearSession, readSession, writeSession } from "../api/session";
import { ProtectedRoute } from "./ProtectedRoute";

const refreshProfileMock = vi.fn();
vi.mock("../api/admin", () => ({ refreshAdminUserProfile: (...args: unknown[]) => refreshProfileMock(...args) }));

describe("ProtectedRoute", () => {
  afterEach(() => {
    clearSession();
  });

  beforeEach(() => refreshProfileMock.mockReset());

  it("redirects anonymous users to login", () => {
    render(
      <MemoryRouter initialEntries={["/dashboard"]}>
        <Routes>
          <Route path="/login" element={<div>login</div>} />
          <Route element={<ProtectedRoute />}>
            <Route path="/dashboard" element={<div>dashboard</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText("login")).toBeInTheDocument();
  });

  it("renders an access denied state for authenticated users without the right role", () => {
    writeSession({
      accessToken: "token",
      tokenType: "Bearer",
      user: {
        id: 2,
        displayName: "Player",
        phoneNumber: "251911000002",
        roles: ["PLAYER"],
        status: "ACTIVE",
        createdAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z",
      },
    });

    render(
      <MemoryRouter initialEntries={["/dashboard"]}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/dashboard" element={<div>dashboard</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText("Access denied")).toBeInTheDocument();
  });

  it("requires temporary-password users to change their password before opening other pages", () => {
    writeSession({
      accessToken: "temporary-token",
      tokenType: "Bearer",
      user: {
        id: 3,
        displayName: "Temporary Admin",
        phoneNumber: "251911000003",
        roles: ["ADMIN"],
        status: "ACTIVE",
        createdAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z",
        mustChangePassword: true,
      },
    });

    render(
      <MemoryRouter initialEntries={["/dashboard"]}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/dashboard" element={<div>dashboard</div>} />
            <Route path="/change-password" element={<div>change password</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText("change password")).toBeInTheDocument();
  });

  it("reconciles cached roles with the server before showing protected routes", async () => {
    refreshProfileMock.mockResolvedValue({
      id: 4,
      displayName: "Finance Support",
      phoneNumber: "251911000004",
      roles: ["SUPPORT"],
      status: "ACTIVE",
      createdAt: "2026-01-01T00:00:00.000Z",
      updatedAt: "2026-01-01T00:00:00.000Z",
    });
    writeSession({
      accessToken: "stale-role-token",
      tokenType: "Bearer",
      user: {
        id: 4,
        displayName: "Finance Support",
        phoneNumber: "251911000004",
        roles: ["SUPPORT", "FINANCE_VERIFIER"],
        status: "ACTIVE",
        createdAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z",
      },
    });

    render(
      <MemoryRouter initialEntries={["/deposits"]}>
        <Routes>
          <Route element={<ProtectedRoute allowedRoles={["FINANCE_VERIFIER"]} refreshSession />}>
            <Route path="/deposits" element={<div>finance deposits</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText("Access denied")).toBeInTheDocument();
    expect(screen.queryByText("finance deposits")).not.toBeInTheDocument();
    expect(readSession()?.user.roles).toEqual(["SUPPORT"]);
    expect(refreshProfileMock).toHaveBeenCalledTimes(1);
  });
});
