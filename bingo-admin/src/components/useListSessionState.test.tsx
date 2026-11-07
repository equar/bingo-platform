import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import { useListSessionState } from "./useListSessionState";

function ListStateProbe() {
  const [page, setPage] = useListSessionState("probe:page", 0);
  return (
    <>
      <output aria-label="current page">{page}</output>
      <button onClick={() => setPage((current) => current + 1)}>Next page</button>
    </>
  );
}

describe("useListSessionState", () => {
  afterEach(() => {
    cleanup();
    window.sessionStorage.clear();
  });

  it("restores list controls after the route unmounts and mounts again", async () => {
    const user = userEvent.setup();
    const firstRoute = render(<ListStateProbe />);
    await user.click(screen.getByRole("button", { name: "Next page" }));
    expect(screen.getByLabelText("current page")).toHaveTextContent("1");
    firstRoute.unmount();

    render(<ListStateProbe />);
    expect(screen.getByLabelText("current page")).toHaveTextContent("1");
  });
});
