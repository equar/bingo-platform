import { mkdir, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { expect, test } from "@playwright/test";

const adminUser = {
  id: 9001,
  displayName: "Visual QA Admin",
  phoneNumber: "251900000001",
  roles: ["ADMIN"],
  status: "ACTIVE",
  createdAt: "2026-11-01T00:00:00Z",
  updatedAt: "2026-11-01T00:00:00Z",
};

const routes = [
  "/", "/management", "/wallet/transfer-policy",
  "/login", "/activate-account", "/reset-password",
  "/dashboard", "/players", "/players/12", "/player-verifications", "/agents", "/agents/12",
  "/rooms", "/rooms/new", "/rooms/12/edit", "/sessions", "/sessions/new", "/sessions/12",
  "/session-tickets", "/community", "/deposits", "/claims", "/prize-entitlements",
  "/support/tickets", "/support/tickets/12", "/wallet/signup-reward", "/wallet/transfers",
  "/admin-users", "/audit-logs", "/reports", "/profile", "/change-password",
];
const publicRoutes = new Set(["/login", "/activate-account", "/reset-password"]);

const viewports = [
  { name: "1366x768", width: 1366, height: 768 },
  { name: "1440x900", width: 1440, height: 900 },
  { name: "1920x1080", width: 1920, height: 1080 },
  { name: "2560x1440", width: 2560, height: 1440 },
  { name: "768x1024", width: 768, height: 1024 },
  { name: "390x844", width: 390, height: 844 },
];

test("Admin routes fit the viewport matrix with mocked API data", async ({ page }, testInfo) => {
  test.setTimeout(600_000);
  const errors: string[] = [];
  const measurements: Array<Record<string, string | number>> = [];
  let preferredLanguage = "en";
  const paginatedEndpoints = new Set([
    "/api/v1/admin/players", "/api/v1/admin/rooms", "/api/v1/admin/sessions", "/api/v1/admin/player-verifications",
    "/api/v1/admin/win-claims", "/api/v1/admin/prize-entitlements", "/api/v1/admin/deposits",
    "/api/v1/admin/wallet/transfers", "/api/v1/admin/audit-logs", "/api/v1/admin/review-queue",
    "/api/v1/admin/users", "/api/v1/admin/agents", "/api/v1/admin/support/tickets",
  ]);
  page.on("pageerror", (error) => errors.push(error.message));
  await page.route("**/api/v1/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname === "/api/v1/auth/me") {
      await route.fulfill({ json: { user: { ...adminUser, preferredLanguage } } });
      return;
    }
    if (route.request().method() === "GET" && paginatedEndpoints.has(pathname)) {
      await route.fulfill({ json: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true } });
      return;
    }
    if (pathname === "/api/v1/admin/dashboard/metrics") {
      await route.fulfill({ json: {
        generatedAt: "2026-11-07T09:00:00Z", businessTimezone: "Africa/Nairobi", activePlayers: 0,
        activeAgents: 0, openSupportTickets: 0, openDepositReviews: 0, openAgentFloatReviews: 0,
        openWinClaims: 0, activeSessions: 0, playerWalletBalanceTotal: "0", agentFloatAvailableTotal: "0",
        agentFloatClearingTotal: "0",
      } });
      return;
    }
    if (pathname === "/api/v1/admin/dashboard/trends") {
      await route.fulfill({ json: { businessTimezone: "Africa/Nairobi", playerRegistrations: [], supportTicketsCreated: [] } });
      return;
    }
    if (pathname === "/api/v1/admin/sessions/live" || pathname === "/api/v1/admin/float-bank-accounts") {
      await route.fulfill({ json: [] });
      return;
    }
    await route.fulfill({
      status: 404,
      json: { status: 404, code: "NOT_FOUND", message: "Layout verification fixture has no record." },
    });
  });
  await page.addInitScript(({ user }) => {
    if (!localStorage.getItem("bingo-admin-session")) localStorage.setItem("bingo-admin-session", JSON.stringify({
      accessToken: "mocked-layout-verification-token",
      tokenType: "Bearer",
      user: { ...user, preferredLanguage: "en" },
    }));
  }, { user: adminUser });

  for (const viewport of viewports) {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    for (const route of routes) {
      await page.goto(route);
      if (publicRoutes.has(route)) await expect(page.getByRole("heading").first()).toBeVisible();
      else await expect(page.locator("#admin-main-content")).toBeVisible();
      await expect(page.getByRole("heading").first()).toBeVisible();
      await expect(page.locator("#admin-main-content .MuiCircularProgress-root")).toHaveCount(0);
      const result = await page.evaluate(() => ({
        documentWidth: document.documentElement.scrollWidth,
        bodyWidth: document.body.scrollWidth,
        viewportWidth: window.innerWidth,
        mainWidth: document.querySelector<HTMLElement>("#admin-main-content")?.clientWidth ?? 0,
        mainScrollWidth: document.querySelector<HTMLElement>("#admin-main-content")?.scrollWidth ?? 0,
        visibleButtons: [...document.querySelectorAll<HTMLElement>("button")]
          .filter((button) => button.getClientRects().length > 0)
          .length,
      }));
      measurements.push({ route, viewport: viewport.name, ...result });
      expect(result.documentWidth, `${route} at ${viewport.name}: page horizontal overflow`).toBe(result.viewportWidth);
      expect(result.bodyWidth, `${route} at ${viewport.name}: body horizontal overflow`).toBe(result.viewportWidth);
      if (viewport.width >= 900 && !publicRoutes.has(route)) {
        const navigation = await page.locator("#admin-primary-navigation").boundingBox();
        expect(navigation?.width ?? 0, `${route} at ${viewport.name}: desktop navigation`).toBeGreaterThan(0);
      }
      const screenshotPath = testInfo.outputPath(join("routes", viewport.name, `${route.replaceAll("/", "_").replace(/^_/, "") || "home"}.png`));
      await mkdir(dirname(screenshotPath), { recursive: true });
      await page.screenshot({ path: screenshotPath, fullPage: true });
    }
  }

  preferredLanguage = "am";
  await page.goto("/profile");
  await page.evaluate(() => {
    const session = JSON.parse(localStorage.getItem("bingo-admin-session") ?? "{}") as { user?: Record<string, unknown> };
    localStorage.setItem("bingo-admin-session", JSON.stringify({ ...session, user: { ...session.user, preferredLanguage: "am" } }));
  });
  for (const width of [768, 390]) {
    await page.setViewportSize({ width, height: width === 768 ? 1024 : 844 });
    for (const route of routes) {
      await page.goto(route);
      if (publicRoutes.has(route)) await expect(page.getByRole("heading").first()).toBeVisible();
      else await expect(page.locator("#admin-main-content")).toBeVisible();
      await expect(page.locator("#admin-main-content .MuiCircularProgress-root")).toHaveCount(0);
      const size = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth, body: document.body.scrollWidth }));
      measurements.push({ route, viewport: `${width}x${width === 768 ? 1024 : 844} Amharic`, ...size });
      expect(size.document, `${route} Amharic at ${width}px`).toBe(width);
      expect(size.body, `${route} Amharic at ${width}px`).toBe(width);
    }
  }

  for (const zoom of [1.25, 2]) {
    const physical = { width: 1440, height: 900 };
    const effectiveViewport = {
      width: Math.floor(physical.width / zoom),
      height: Math.floor(physical.height / zoom),
    };
    await page.setViewportSize(effectiveViewport);
    for (const route of ["/dashboard", "/players", "/sessions", "/rooms/new", "/agents/12", "/reports"]) {
      await page.goto(route);
      await expect(page.locator("#admin-main-content")).toBeVisible();
      const width = await page.evaluate(() => ({
        viewport: window.innerWidth,
        document: document.documentElement.scrollWidth,
        body: document.body.scrollWidth,
      }));
      measurements.push({ route, viewport: `1440x900@${zoom * 100}% effective CSS viewport`, ...width });
      expect(width.document, `${route} at ${zoom * 100}% zoom effective viewport`).toBe(width.viewport);
      expect(width.body, `${route} at ${zoom * 100}% zoom effective viewport`).toBe(width.viewport);
    }
  }

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/rooms/new");
  const saveButton = page.locator(".MuiButton-contained").last();
  await expect(saveButton).toBeVisible();
  await saveButton.click();
  const confirmDialog = page.getByRole("dialog");
  await expect(confirmDialog).toBeVisible();
  const dialogBounds = await confirmDialog.boundingBox();
  expect(dialogBounds).not.toBeNull();
  expect(dialogBounds!.x).toBeGreaterThanOrEqual(0);
  expect(dialogBounds!.x + dialogBounds!.width).toBeLessThanOrEqual(390);
  expect(dialogBounds!.y).toBeGreaterThanOrEqual(0);
  expect(dialogBounds!.y + dialogBounds!.height).toBeLessThanOrEqual(844);
  await expect(confirmDialog.locator(".MuiDialogActions-root button")).toHaveCount(2);
  await page.screenshot({ path: testInfo.outputPath("dialogs/room-create-mobile.png"), fullPage: true });
  await confirmDialog.locator(".MuiDialogActions-root button").first().click();

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/dashboard");
  const mobileMenu = page.locator('button[aria-controls="admin-primary-navigation"]');
  await expect(mobileMenu).toBeVisible();
  await mobileMenu.click();
  await expect(page.locator("#admin-primary-navigation")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.locator("#admin-primary-navigation")).toBeHidden();

  const evidencePath = testInfo.outputPath("layout-matrix.json");
  await mkdir(dirname(evidencePath), { recursive: true });
  await writeFile(evidencePath, JSON.stringify({ measurements, pageErrors: errors }, null, 2), "utf8");
  expect(errors).toEqual([]);
});

test("room confirmation dialog stays inside desktop, mobile, and zoom-equivalent viewports", async ({ page }, testInfo) => {
  await page.route("**/api/v1/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname === "/api/v1/auth/me") {
      await route.fulfill({ json: { user: { ...adminUser, preferredLanguage: "en" } } });
    } else if (pathname === "/api/v1/admin/rooms") {
      await route.fulfill({ json: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true } });
    } else {
      await route.fulfill({ status: 404, json: {} });
    }
  });
  await page.addInitScript(({ user }) => {
    localStorage.setItem("bingo-admin-session", JSON.stringify({
      accessToken: "mocked-dialog-layout-token", tokenType: "Bearer", user: { ...user, preferredLanguage: "en" },
    }));
  }, { user: adminUser });

  for (const viewport of [
    { name: "mobile", width: 390, height: 844 },
    { name: "1440x900", width: 1440, height: 900 },
    { name: "1440x900@200%", width: 720, height: 450 },
  ]) {
    await page.setViewportSize(viewport);
    await page.goto("/rooms/new");
    await expect(page.locator("#admin-main-content .MuiCircularProgress-root")).toHaveCount(0);
    await page.locator(".MuiButton-contained").last().click();
    const dialog = page.getByRole("dialog");
    await expect(dialog).toBeVisible();
    await page.waitForTimeout(300);
    const bounds = await dialog.boundingBox();
    expect(bounds, viewport.name).not.toBeNull();
    expect(bounds!.x, viewport.name).toBeGreaterThanOrEqual(0);
    expect(bounds!.y, viewport.name).toBeGreaterThanOrEqual(0);
    expect(bounds!.x + bounds!.width, viewport.name).toBeLessThanOrEqual(viewport.width);
    expect(bounds!.y + bounds!.height, viewport.name).toBeLessThanOrEqual(viewport.height);
    await expect(dialog.locator(".MuiDialogActions-root button")).toHaveCount(2);
    await page.screenshot({ path: testInfo.outputPath(`dialogs/room-confirm-${viewport.name}.png`) });
    await dialog.locator(".MuiDialogActions-root button").first().click();
  }
});
