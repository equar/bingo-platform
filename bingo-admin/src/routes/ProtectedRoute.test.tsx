import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it } from "vitest";
import { clearSession, writeSession } from "../api/session";
import { ProtectedRoute } from "./ProtectedRoute";

describe("ProtectedRoute", () => {
  afterEach(() => {
    clearSession();
  });

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
});
