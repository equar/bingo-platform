import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type PropsWithChildren } from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  createPromotionalRewardCampaign,
  currentSignupRewardSettings,
  currentWalletTransferSettings,
  promotionalRewardCampaigns,
  promotionalRewardGrants,
  walletTransferSettingsHistory,
} from "../../api/admin";
import { SignupRewardGrantsPage } from "./SignupRewardGrantsPage";
import { WalletPoliciesPage } from "./WalletPoliciesPage";

vi.mock("../../api/admin", () => ({
  createPromotionalRewardCampaign: vi.fn(),
  currentSignupRewardSettings: vi.fn(),
  currentWalletTransferSettings: vi.fn(),
  promotionalRewardCampaigns: vi.fn(),
  promotionalRewardGrants: vi.fn(),
  awardPromotionalReward: vi.fn(),
  saveWalletTransferSettings: vi.fn(),
  setSignupRewardPolicyStatus: vi.fn(),
  walletTransferSettingsHistory: vi.fn(),
}));

const createCampaignMock = vi.mocked(createPromotionalRewardCampaign);
const currentSignupSettingsMock = vi.mocked(currentSignupRewardSettings);
const currentTransferSettingsMock = vi.mocked(currentWalletTransferSettings);
const campaignsMock = vi.mocked(promotionalRewardCampaigns);
const grantsMock = vi.mocked(promotionalRewardGrants);
const transferHistoryMock = vi.mocked(walletTransferSettingsHistory);

function createWrapper({ children }: PropsWithChildren) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

function renderAt(path: string) {
  return render(<MemoryRouterWithPolicyRoute path={path} />, { wrapper: createWrapper });
}

function MemoryRouterWithPolicyRoute({ path }: { path: string }) {
  return <MemoryRouter initialEntries={[path]}><Routes><Route path="/wallet/:tab" element={<><LocationDisplay /><WalletPoliciesPage /></>} /><Route path="/wallet/:tab/new" element={<><LocationDisplay /><WalletPoliciesPage /></>} /></Routes></MemoryRouter>;
}

function LocationDisplay() {
  const location = useLocation();
  return <output data-testid="current-location">{location.pathname}</output>;
}

describe("WalletPoliciesPage", () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.clearAllMocks();
    currentSignupSettingsMock.mockResolvedValue({
      id: 1, enabled: true, rewardAmount: "20.00", currency: "ETB",
      effectiveFrom: new Date().toISOString(), effectiveUntil: null,
      changedByUserId: 2, reason: null, createdAt: "2026-10-01T00:00:00Z",
      updatedAt: "2026-10-01T00:00:00Z", activeNow: true,
    });
    currentTransferSettingsMock.mockResolvedValue({
      id: 2, enabled: true, maxSingleTransferAmount: "100.00", dailyOutgoingLimitAmount: "500.00",
      currency: "ETB", effectiveFrom: "2026-10-01T00:00:00Z", effectiveUntil: null,
      changedByUserId: 2, reason: null, createdAt: "2026-10-01T00:00:00Z",
      updatedAt: "2026-10-01T00:00:00Z", activeNow: true,
    });
    campaignsMock.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true });
    grantsMock.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true });
    transferHistoryMock.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true });
  });

  it("loads the campaign list for the promotional rewards route and transfer history for its route", async () => {
    const { unmount } = renderAt("/wallet/signup-reward");
    expect(await screen.findByRole("heading", { name: "Promotional rewards" })).toBeInTheDocument();
    expect(currentSignupSettingsMock).toHaveBeenCalledOnce();
    expect(campaignsMock).toHaveBeenCalledWith(0, 20, undefined, undefined);
    expect(currentTransferSettingsMock).not.toHaveBeenCalled();

    unmount();
    renderAt("/wallet/transfer-policy");
    expect(await screen.findByRole("heading", { name: "Transfer policy" })).toBeInTheDocument();
    await waitFor(() => expect(currentTransferSettingsMock).toHaveBeenCalledOnce());
    expect(screen.getByTestId("current-location")).toHaveTextContent("/wallet/transfer-policy");
    expect(transferHistoryMock).toHaveBeenCalledOnce();
  });

  it("loads grants from the promotional reward grants page", async () => {
    render(<SignupRewardGrantsPage />, { wrapper: createWrapper });
    expect(await screen.findByRole("heading", { name: "Promotional reward grants" })).toBeInTheDocument();
    expect(grantsMock).toHaveBeenCalledWith(0, 20, undefined);
  });

  it("defaults a new campaign to today and accepts an inclusive same-day end date", async () => {
    currentSignupSettingsMock.mockResolvedValue({
      id: null, enabled: false, rewardAmount: "0.00", currency: "ETB", effectiveFrom: "1970-01-01T00:00:00Z",
      effectiveUntil: null, changedByUserId: null, reason: null, createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(), activeNow: false,
    });
    renderAt("/wallet/signup-reward");
    await screen.findByRole("heading", { name: "Promotional rewards" });
    await userEvent.setup().click(screen.getByRole("button", { name: "Add promotional campaign" }));
    const from = screen.getByLabelText("Effective from") as HTMLInputElement;
    const until = screen.getByLabelText("Effective until") as HTMLInputElement;
    expect(from.type).toBe("date");
    expect(until.type).toBe("date");
    expect(from.value).toBe(from.min);
    expect(until.min).toBe(from.value);
    fireEvent.change(until, { target: { value: from.value } });
    expect(screen.getByRole("button", { name: "Create campaign" })).toBeEnabled();
  });

  it("replaces past saved campaign dates with current future defaults", async () => {
    const yesterday = new Date(Date.now() - 24 * 60 * 60_000).toISOString();
    currentSignupSettingsMock.mockResolvedValue({
      id: 1, enabled: false, rewardAmount: "25.00", currency: "ETB", effectiveFrom: yesterday,
      effectiveUntil: yesterday, changedByUserId: 2, reason: null, createdAt: yesterday, updatedAt: yesterday, activeNow: false,
    });
    renderAt("/wallet/signup-reward");
    await screen.findByRole("heading", { name: "Promotional rewards" });
    await userEvent.setup().click(screen.getByRole("button", { name: "Add promotional campaign" }));
    const from = screen.getByLabelText("Effective from") as HTMLInputElement;
    const until = screen.getByLabelText("Effective until") as HTMLInputElement;
    expect(from.value >= from.min).toBe(true);
    expect(until.value).toBe("");
  });

  it("shows safe backend validation failures when campaign creation fails", async () => {
    const user = userEvent.setup();
    createCampaignMock.mockRejectedValue({
      response: { status: 400, data: { code: "VALIDATION_ERROR", message: "Internal validation detail" } },
    });
    renderAt("/wallet/signup-reward");
    await screen.findByRole("heading", { name: "Promotional rewards" });
    await user.click(screen.getByRole("button", { name: "Add promotional campaign" }));
    await user.click(screen.getByRole("button", { name: "Create campaign" }));
    expect(await screen.findByText("Check the highlighted fields and try again.")).toBeInTheDocument();
    expect(createCampaignMock).toHaveBeenCalledOnce();
    expect(createCampaignMock).toHaveBeenCalledWith(expect.objectContaining({ name: "Signup reward", triggerType: "SIGNUP", enabled: true }));
  });
});
