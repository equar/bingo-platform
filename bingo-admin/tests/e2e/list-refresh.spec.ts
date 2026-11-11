import { expect, test } from "@playwright/test";

test("refresh reloads the current paginated list on desktop and narrow layouts", async ({ page }) => {
  let playerFetches = 0;
  let depositFetches = 0;
  await page.route("**/api/v1/**", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === "/api/v1/auth/me") {
      await route.fulfill({ json: { user: {
        id: 9001, displayName: "Visual QA Admin", phoneNumber: "251900000001",
        roles: ["ADMIN"], status: "ACTIVE", preferredLanguage: "en",
      } } });
      return;
    }
    if (url.pathname === "/api/v1/admin/players") {
      playerFetches += 1;
      await route.fulfill({ json: {
        content: [{
          id: 10, displayName: "Refresh QA Player", normalizedPhone: "251900000010",
          roles: ["PLAYER"], status: "SUSPENDED", createdAt: "2026-11-01T00:00:00Z",
          updatedAt: "2026-11-01T00:00:00Z", walletTransactionCount: 1,
          depositClaimCount: 0, supportTicketCount: 0,
        }], page: Number(url.searchParams.get("page") ?? 0),
        size: Number(url.searchParams.get("size") ?? 20), totalElements: 21,
        totalPages: 3, first: false, last: false,
      } });
      return;
    }
    if (url.pathname === "/api/v1/admin/players/10") {
      await route.fulfill({ json: {
        id: 10, displayName: "Refresh QA Player", normalizedPhone: "251900000010",
        email: null, roles: ["PLAYER"], status: "ACTIVE", createdAt: "2026-11-01T00:00:00Z",
        updatedAt: "2026-11-01T00:00:00Z", walletCurrency: "ETB", walletAvailableBalance: "100.00",
        walletPromotionalBalance: "0.00", walletTransactionCount: 1, depositClaimCount: 0,
        supportTicketCount: 0, ticketCount: 0, winClaimCount: 0,
      } });
      return;
    }
    if (url.pathname === "/api/v1/admin/players/10/wallet-adjustments/requests") {
      await route.fulfill({ json: [] });
      return;
    }
    if (url.pathname === "/api/v1/admin/players/10/deposits") {
      depositFetches += 1;
      await route.fulfill({ json: {
        content: [], page: Number(url.searchParams.get("page") ?? 0),
        size: Number(url.searchParams.get("size") ?? 10), totalElements: 0,
        totalPages: 0, first: true, last: true,
      } });
      return;
    }
    await route.fulfill({ status: 404, json: { message: "No QA fixture" } });
  });
  await page.addInitScript(() => {
    localStorage.setItem("bingo-admin-session", JSON.stringify({
      accessToken: "synthetic-browser-test-token", tokenType: "Bearer",
      user: { id: 9001, displayName: "Visual QA Admin", roles: ["ADMIN"] },
    }));
  });

  await page.goto("/players?status=SUSPENDED&page=1&size=10");
  await expect(page.getByRole("link", { name: "Refresh QA Player" })).toHaveAttribute(
    "href", "/players/10?status=SUSPENDED&page=1&size=10",
  );
  const refresh = page.getByRole("button", { name: "Refresh" });
  await refresh.click();
  await expect.poll(() => playerFetches).toBe(2);
  await expect(page.getByRole("link", { name: "Refresh QA Player" })).toHaveAttribute(
    "href", "/players/10?status=SUSPENDED&page=1&size=10",
  );

  await page.goto("/players/10");
  await page.getByRole("button", { name: "View deposits details" }).click();
  await page.getByRole("tab", { name: "Deposits" }).click();
  await expect.poll(() => depositFetches).toBe(1);
  await page.getByRole("button", { name: "Refresh" }).click();
  await expect.poll(() => depositFetches).toBe(2);
  await expect(page.getByRole("tab", { name: "Deposits" })).toHaveAttribute("aria-selected", "true");

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/players?status=SUSPENDED&page=1&size=10");
  const narrowRefresh = page.getByRole("button", { name: "Refresh" });
  await expect(narrowRefresh).toBeVisible();
  const playerFetchesBeforeNarrowRefresh = playerFetches;
  await narrowRefresh.click();
  await expect.poll(() => playerFetches).toBe(playerFetchesBeforeNarrowRefresh + 1);
  await expect(narrowRefresh).toBeFocused();
});

test("independent refresh actions on Agents reload only their own lists", async ({ page }) => {
  let accountFetches = 0;
  let requestFetches = 0;
  await page.route("**/api/v1/**", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === "/api/v1/auth/me") {
      await route.fulfill({ json: { user: {
        id: 9001, displayName: "Visual QA Admin", phoneNumber: "251900000001",
        roles: ["ADMIN"], status: "ACTIVE", preferredLanguage: "en",
      } } });
    } else if (url.pathname === "/api/v1/admin/agents") {
      await route.fulfill({ json: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true } });
    } else if (url.pathname === "/api/v1/admin/agents/commission-rules/current") {
      await route.fulfill({ json: { versionNumber: 0, commissionRateBps: 0, effectiveFrom: "2026-11-01T00:00:00Z", active: false } });
    } else if (url.pathname === "/api/v1/admin/float-bank-accounts") {
      accountFetches += 1;
      await route.fulfill({ json: [] });
    } else if (url.pathname === "/api/v1/admin/agents/float-top-up-requests") {
      requestFetches += 1;
      await route.fulfill({ json: { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true } });
    } else {
      await route.fulfill({ status: 404, json: { message: "No QA fixture" } });
    }
  });
  await page.addInitScript(() => {
    localStorage.setItem("bingo-admin-session", JSON.stringify({
      accessToken: "synthetic-browser-test-token", tokenType: "Bearer",
      user: { id: 9001, displayName: "Visual QA Admin", roles: ["ADMIN"] },
    }));
  });

  await page.goto("/agents");
  await expect(page.getByRole("heading", { name: "Receiving bank accounts" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Float top-up review" })).toBeVisible();
  await expect.poll(() => accountFetches).toBe(1);
  await expect.poll(() => requestFetches).toBe(1);

  await page.getByRole("heading", { name: "Receiving bank accounts" }).locator("..").getByRole("button", { name: "Refresh" }).click();
  await expect.poll(() => accountFetches).toBe(2);
  expect(requestFetches).toBe(1);

  await page.getByRole("heading", { name: "Float top-up review" }).locator("..").getByRole("button", { name: "Refresh" }).first().click();
  await expect.poll(() => requestFetches).toBe(2);
  expect(accountFetches).toBe(2);
});
