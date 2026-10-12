import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { StatusState } from "./StatusState";

describe("StatusState", () => {
  it("renders loading and action content", () => {
    render(<StatusState title="Loading" message="Working" actionLabel="Retry" onAction={vi.fn()} loading />);

    expect(screen.getByRole("status")).toBeInTheDocument();
    expect(screen.getByText("Loading")).toBeInTheDocument();
    expect(screen.getByText("Working")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
  });
});
