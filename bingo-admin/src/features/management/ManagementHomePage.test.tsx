import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { ManagementHomePage } from "./ManagementHomePage";

describe("ManagementHomePage", () => {
  it("renders the management shortcuts", () => {
    render(
      <MemoryRouter>
        <ManagementHomePage />
      </MemoryRouter>,
    );

    expect(screen.getByRole("heading", { name: "Management" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Admin users/ })).toHaveAttribute("href", "/admin-users");
    expect(screen.getByRole("link", { name: /Agents/ })).toHaveAttribute("href", "/agents");
    expect(screen.getByRole("link", { name: /Players/ })).toHaveAttribute("href", "/players");
  });
});
