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

  it("allows SUPPORT on shared admin pages", () => {
    writeSession({
      accessToken: "support-token",
      tokenType: "Bearer",
      user: {
        id: 5,
        displayName: "Support User",
        phoneNumber: "251911000005",
        roles: ["SUPPORT"],
        status: "ACTIVE",
        createdAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z",
      },
    });

    render(
      <MemoryRouter initialEntries={["/deposits"]}>
        <Routes>
          <Route element={<ProtectedRoute allowedRoles={["ADMIN", "SUPPORT"]} />}>
            <Route path="/deposits" element={<div>deposits page</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText("deposits page")).toBeInTheDocument();
  });

  it("allows SUPPORT to open the promotional reward grants page", () => {
    writeSession({
      accessToken: "support-token",
      tokenType: "Bearer",
      user: {
        id: 8,
        displayName: "Support User",
        phoneNumber: "251911000008",
        roles: ["SUPPORT"],
        status: "ACTIVE",
        createdAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z",
      },
    });

    render(
      <MemoryRouter initialEntries={["/wallet/signup-reward-grants"]}>
        <Routes>
          <Route element={<ProtectedRoute allowedRoles={["ADMIN", "SUPPORT"]} />}>
            <Route path="/wallet/signup-reward-grants" element={<div>promotional reward grants page</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText("promotional reward grants page")).toBeInTheDocument();
  });

  it("keeps the restricted admin pages inaccessible to SUPPORT", () => {
    writeSession({
      accessToken: "support-token",
      tokenType: "Bearer",
      user: {
        id: 6,
        displayName: "Support User",
        phoneNumber: "251911000006",
        roles: ["SUPPORT"],
        status: "ACTIVE",
        createdAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z",
      },
    });

    render(
      <MemoryRouter initialEntries={["/rooms"]}>
        <Routes>
          <Route element={<ProtectedRoute allowedRoles={["ADMIN"]} />}>
            <Route path="/rooms" element={<div>rooms page</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText("Access denied")).toBeInTheDocument();
    expect(screen.queryByText("rooms page")).not.toBeInTheDocument();
  });

  it("keeps audit logs inaccessible to SUPPORT", () => {
    writeSession({
      accessToken: "support-token",
      tokenType: "Bearer",
      user: {
        id: 7,
        displayName: "Support User",
        phoneNumber: "251911000007",
        roles: ["SUPPORT"],
        status: "ACTIVE",
        createdAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z",
      },
    });

    render(
      <MemoryRouter initialEntries={["/audit-logs"]}>
        <Routes>
          <Route element={<ProtectedRoute allowedRoles={["ADMIN"]} />}>
            <Route path="/audit-logs" element={<div>audit logs page</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText("Access denied")).toBeInTheDocument();
    expect(screen.queryByText("audit logs page")).not.toBeInTheDocument();
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

  it("denies protected routes after the server removes a cached admin role", async () => {
    refreshProfileMock.mockResolvedValue({
      id: 4,
      displayName: "Former Admin",
      phoneNumber: "251911000004",
      roles: ["PLAYER"],
      status: "ACTIVE",
      createdAt: "2026-01-01T00:00:00.000Z",
      updatedAt: "2026-01-01T00:00:00.000Z",
    });
    writeSession({
      accessToken: "stale-role-token",
      tokenType: "Bearer",
      user: {
        id: 4,
        displayName: "Former Admin",
        phoneNumber: "251911000004",
        roles: ["ADMIN"],
        status: "ACTIVE",
        createdAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z",
      },
    });

    render(
      <MemoryRouter initialEntries={["/deposits"]}>
        <Routes>
          <Route element={<ProtectedRoute allowedRoles={["ADMIN"]} refreshSession />}>
            <Route path="/deposits" element={<div>admin dashboard</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText("Access denied")).toBeInTheDocument();
    expect(screen.queryByText("admin dashboard")).not.toBeInTheDocument();
    expect(readSession()?.user.roles).toEqual(["PLAYER"]);
    expect(refreshProfileMock).toHaveBeenCalledTimes(1);
  });
});
