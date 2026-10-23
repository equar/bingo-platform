import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  currentSignupRewardSettings,
  currentWalletTransferSettings,
  saveSignupRewardSettings,
  saveWalletTransferSettings,
  signupRewardGrants,
  signupRewardSettingsHistory,
  walletTransferSettingsHistory,
} from "../../api/admin";
import { WalletPoliciesPage } from "./WalletPoliciesPage";

vi.mock("../../api/admin", () => ({
  currentSignupRewardSettings: vi.fn(),
  currentWalletTransferSettings: vi.fn(),
  saveSignupRewardSettings: vi.fn(),
  saveWalletTransferSettings: vi.fn(),
  signupRewardGrants: vi.fn(),
  signupRewardSettingsHistory: vi.fn(),
  walletTransferSettingsHistory: vi.fn(),
}));

const currentSignupRewardSettingsMock = vi.mocked(currentSignupRewardSettings);
const currentWalletTransferSettingsMock = vi.mocked(currentWalletTransferSettings);
const saveSignupRewardSettingsMock = vi.mocked(saveSignupRewardSettings);
const saveWalletTransferSettingsMock = vi.mocked(saveWalletTransferSettings);
const signupRewardGrantsMock = vi.mocked(signupRewardGrants);
const signupRewardSettingsHistoryMock = vi.mocked(signupRewardSettingsHistory);
const walletTransferSettingsHistoryMock = vi.mocked(walletTransferSettingsHistory);

function createWrapper({ children }: PropsWithChildren) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={queryClient}>
      {children}
    </QueryClientProvider>
  );
}

function renderAt(path: string) {
  return render(<MemoryRouterWithPolicyRoute path={path} />, { wrapper: createWrapper });
}

function MemoryRouterWithPolicyRoute({ path }: { path: string }) {
  return (
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route
          path="/wallet/:tab"
          element={
            <>
              <LocationDisplay />
              <WalletPoliciesPage />
            </>
          }
        />
      </Routes>
    </MemoryRouter>
  );
}

function LocationDisplay() {
  const location = useLocation();
  return <output data-testid="current-location">{location.pathname}</output>;
}

describe("WalletPoliciesPage", () => {
  afterEach(cleanup);

  beforeEach(() => {
    currentSignupRewardSettingsMock.mockReset();
    currentWalletTransferSettingsMock.mockReset();
    saveSignupRewardSettingsMock.mockReset();
    saveWalletTransferSettingsMock.mockReset();
    signupRewardGrantsMock.mockReset();
    signupRewardSettingsHistoryMock.mockReset();
    walletTransferSettingsHistoryMock.mockReset();
    currentSignupRewardSettingsMock.mockResolvedValue({
      id: 1,
      enabled: true,
      rewardAmount: "20.00",
      currency: "ETB",
      effectiveFrom: "2026-10-01T00:00:00Z",
      effectiveUntil: null,
      changedByUserId: 2,
      reason: null,
      createdAt: "2026-10-01T00:00:00Z",
      updatedAt: "2026-10-01T00:00:00Z",
      activeNow: true,
    });
    currentWalletTransferSettingsMock.mockResolvedValue({
      id: 2,
      enabled: true,
      maxSingleTransferAmount: "100.00",
      dailyOutgoingLimitAmount: "500.00",
      currency: "ETB",
      effectiveFrom: "2026-10-01T00:00:00Z",
      effectiveUntil: null,
      changedByUserId: 2,
      reason: null,
      createdAt: "2026-10-01T00:00:00Z",
      updatedAt: "2026-10-01T00:00:00Z",
      activeNow: true,
    });
    signupRewardGrantsMock.mockResolvedValue({ content: [], page: 0, size: 8, totalElements: 0, totalPages: 0, first: true, last: true });
    signupRewardSettingsHistoryMock.mockResolvedValue({ content: [], page: 0, size: 8, totalElements: 0, totalPages: 0, first: true, last: true });
    walletTransferSettingsHistoryMock.mockResolvedValue({ content: [], page: 0, size: 8, totalElements: 0, totalPages: 0, first: true, last: true });
  });

  it("loads only the selected policy and navigates between tabs", async () => {
    const user = userEvent.setup();
    renderAt("/wallet/signup-reward");

    expect(await screen.findByRole("heading", { name: "Signup reward" })).toBeInTheDocument();
    expect(currentSignupRewardSettingsMock).toHaveBeenCalledOnce();
    expect(currentWalletTransferSettingsMock).not.toHaveBeenCalled();
    expect(signupRewardGrantsMock).toHaveBeenCalledOnce();
    expect(walletTransferSettingsHistoryMock).not.toHaveBeenCalled();

    await user.click(screen.getByRole("tab", { name: "Transfer policy" }));

    await waitFor(() => expect(currentWalletTransferSettingsMock).toHaveBeenCalledOnce());
    expect(screen.getByTestId("current-location")).toHaveTextContent("/wallet/transfer-policy");
    expect(await screen.findByRole("heading", { name: "Transfer policy" })).toBeInTheDocument();
    expect(walletTransferSettingsHistoryMock).toHaveBeenCalledOnce();
  });

  it("shows backend validation failures for policy saves", async () => {
    const user = userEvent.setup();
    saveSignupRewardSettingsMock.mockRejectedValue(new Error("Validation failed"));
    renderAt("/wallet/signup-reward");

    await screen.findByRole("heading", { name: "Signup reward" });
    await user.click(screen.getByRole("button", { name: "Save reward policy" }));

    expect(await screen.findByText("Validation failed")).toBeInTheDocument();
    expect(saveSignupRewardSettingsMock).toHaveBeenCalledOnce();
  });
});
