import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { ManagementHomePage } from "./ManagementHomePage";

describe("ManagementHomePage", () => {
  it("redirects to the dashboard", () => {
    render(
      <MemoryRouter initialEntries={["/management"]}>
        <Routes>
          <Route path="/management" element={<ManagementHomePage />} />
          <Route path="/dashboard" element={<div>Dashboard content</div>} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText("Dashboard content")).toBeInTheDocument();
  });
});
