import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ChangePasswordPage } from "./ChangePasswordPage";

const changeAdminPasswordMock = vi.fn();
const navigateMock = vi.fn();

vi.mock("../../api/admin", () => ({
  changeAdminPassword: (...args: unknown[]) => changeAdminPasswordMock(...args),
}));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return {
    ...actual,
    useNavigate: () => navigateMock,
  };
});

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  };
}

describe("ChangePasswordPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    changeAdminPasswordMock.mockResolvedValue(undefined);
  });

  it("submits the password change form", async () => {
    const user = userEvent.setup();
    const { container } = render(<ChangePasswordPage />, { wrapper: createWrapper() });

    expect(await screen.findByRole("heading", { name: "Change password" })).toBeInTheDocument();

    const [currentPasswordInput, newPasswordInput, confirmPasswordInput] = container.querySelectorAll(
      'input[type="password"]',
    );

    await user.type(currentPasswordInput, "OldPass123!");
    await user.type(newPasswordInput, "NewPass123!");
    await user.type(confirmPasswordInput, "NewPass123!");
    await user.click(screen.getByRole("button", { name: "Save password" }));

    await waitFor(() => {
      expect(changeAdminPasswordMock).toHaveBeenCalledWith({
        currentPassword: "OldPass123!",
        newPassword: "NewPass123!",
      });
    });

    expect(await screen.findByText("Password updated successfully.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Back to profile" })).toBeInTheDocument();
  });
});
