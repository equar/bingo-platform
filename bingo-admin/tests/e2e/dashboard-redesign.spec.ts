import { mkdir } from "node:fs/promises";
import { dirname } from "node:path";
import { expect, test } from "@playwright/test";

const adminUser = {
  id: 1,
  displayName: "QA Admin",
  phoneNumber: "251911000001",
  roles: ["ADMIN"],
  status: "ACTIVE",
  createdAt: "2026-11-03T00:00:00Z",
  updatedAt: "2026-11-03T00:00:00Z",
};

const metrics = {
  generatedAt: new Date().toISOString(),
  businessTimezone: "Africa/Addis_Ababa",
  activePlayers: 18,
  activeAgents: 4,
  openSupportTickets: 3,
  openDepositReviews: 2,
  openAgentFloatReviews: 0,
  openWinClaims: 1,
  activeSessions: 1,
  playerWalletBalanceTotal: "1234.50",
  agentFloatAvailableTotal: "500.00",
  agentFloatClearingTotal: "20.25",
};

const pageResponse = {
  content: [],
  page: 0,
  size: 5,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
};

async function mockAdminApi(
  page: import("@playwright/test").Page,
  options: {
    metrics?: typeof metrics;
    metricsUnavailable?: () => boolean;
    liveSessions?: unknown[];
    auditLogs?: typeof pageResponse & { content: unknown[] };
  } = {},
) {
  await page.route("**/api/v1/**", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === "/api/v1/auth/me") {
      await route.fulfill({ json: { user: adminUser } });
    } else if (url.pathname === "/api/v1/admin/dashboard/metrics") {
      if (options.metricsUnavailable?.()) {
        await route.fulfill({ status: 503, json: { message: "Service temporarily unavailable." } });
      } else {
        await route.fulfill({ json: options.metrics ?? metrics });
      }
    } else if (url.pathname === "/api/v1/admin/sessions/live") {
      await route.fulfill({
        json: options.liveSessions ?? [{
          id: 101,
          roomId: 7,
          roomName: "QA room",
          sessionName: "QA live session",
          state: "DRAWING",
          scheduledStartAt: "2026-11-03T09:00:00Z",
          startedAt: "2026-11-03T09:00:00Z",
          endedAt: null,
          ticketPrice: "10.00",
          currency: "ETB",
          ticketCount: 24,
          drawCount: 8,
          winClaimCount: 0,
          supportTicketCount: 0,
        }],
      });
    } else if (url.pathname === "/api/v1/admin/audit-logs") {
      await route.fulfill({
        json: options.auditLogs ?? {
          ...pageResponse,
          content: [{
            id: 12,
            actorUserId: 1,
            action: "UPDATE_SESSION_STATE",
            targetType: "SESSION",
            targetId: 101,
            reason: "QA visual fixture",
            operationId: "qa-dashboard-fixture",
            createdAt: "2026-11-03T10:00:00Z",
          }],
          totalElements: 12,
          totalPages: 3,
          last: false,
        },
      });
    } else if (url.pathname === "/api/v1/admin/deposits" || url.pathname === "/api/v1/admin/win-claims") {
      await route.fulfill({ json: { ...pageResponse, size: 20 } });
    } else {
      await route.fulfill({ status: 404, json: { message: "No visual fixture for this request." } });
    }
  });
}

async function switchToAmharic(page: import("@playwright/test").Page) {
  await page.getByRole("combobox", { name: "Language" }).click();
  await page.getByRole("option", { name: "አማርኛ" }).click();
  await expect(page.getByRole("heading", { name: "ዳሽቦርድ" })).toBeVisible();
}

async function switchToEnglish(page: import("@playwright/test").Page) {
  await page.getByRole("combobox", { name: "ቋንቋ" }).click();
  await page.getByRole("option", { name: "English" }).click();
  await expect(page.getByRole("heading", { name: "Deposits", exact: true })).toBeVisible();
}

test("renders mocked dashboard data and responsive navigation in English and Amharic", async ({ page }, testInfo) => {
  await mockAdminApi(page);
  await page.addInitScript(({ user }) => {
    if (!localStorage.getItem("bingo-admin-locale")) localStorage.setItem("bingo-admin-locale", "en");
    localStorage.setItem("bingo-admin-session", JSON.stringify({
      accessToken: "mocked-visual-test-token",
      tokenType: "Bearer",
      user,
    }));
    localStorage.removeItem("bingo-admin-nav-collapsed");
  }, { user: adminUser });

  await page.setViewportSize({ width: 1440, height: 960 });
  await page.goto("/dashboard");
  await expect(page.getByRole("heading", { name: "Needs attention" })).toBeVisible();
  await expect(page.getByText("QA live session")).toBeVisible();
  await expect(page.getByText("Update session state")).toBeVisible();

  const dashboardLink = page.getByRole("link", { name: "Dashboard" });
  await expect(dashboardLink).toHaveAttribute("aria-current", "page");
  await page.getByRole("button", { name: "Collapse navigation" }).click();
  await expect(page.getByRole("button", { name: "Expand navigation" })).toHaveAttribute("aria-pressed", "true");
  await expect.poll(() => page.evaluate(() => localStorage.getItem("bingo-admin-nav-collapsed"))).toBe("true");
  await page.getByRole("link", { name: "Dashboard" }).hover();
  await expect(page.getByRole("tooltip", { name: "Dashboard" })).toBeVisible();
  await page.getByRole("button", { name: "Expand navigation" }).click();
  await expect(page.getByRole("link", { name: "Dashboard" })).toHaveAttribute("aria-current", "page");
  await page.getByRole("button", { name: /QA Admin/ }).click();
  await expect(page.getByRole("menuitem", { name: "Profile" })).toBeVisible();
  await page.keyboard.press("Escape");

  for (const language of ["en", "am"] as const) {
    if (language === "am") {
      await switchToAmharic(page);
    }
    for (const viewport of [
      { name: "desktop", width: 1440, height: 960 },
      { name: "tablet", width: 768, height: 1024 },
      { name: "mobile", width: 390, height: 844 },
    ]) {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      if (viewport.name === "mobile") {
        const openButtonName = language === "en" ? "Open navigation" : "የአሰሳ ምናሌን ክፈት";
        const menuButton = page.getByRole("button", { name: openButtonName });
        await menuButton.click();
        await expect(page.getByRole("navigation", {
          name: language === "en" ? "Primary navigation" : "ዋና የአሰሳ ምናሌ",
        })).toBeVisible();
        await page.keyboard.press("Escape");
        await expect(menuButton).toBeFocused();
      }

      const dimensions = await page.evaluate(() => ({
        viewport: window.innerWidth,
        document: document.documentElement.scrollWidth,
      }));
      expect(dimensions.document, `${language} ${viewport.name}`).toBe(dimensions.viewport);

      const screenshotPath = testInfo.outputPath(`dashboard-${language}-${viewport.name}.png`);
      await mkdir(dirname(screenshotPath), { recursive: true });
      await page.screenshot({ path: screenshotPath, fullPage: true });
    }
  }

  await page.setViewportSize({ width: 1440, height: 960 });
  await page.goto("/deposits?status=PENDING");
  await expect(page.getByRole("heading", { name: "ተቀማጭ ገንዘቦች" })).toBeVisible();
  const searchBox = page.getByRole("textbox", { name: "ተቀማጭ፣ ተጫዋች፣ የክፍያ ማጣቀሻ ወይም ክዋኔ ፈልግ" });
  await searchBox.fill("QA filter");
  await switchToEnglish(page);
  await expect(page.getByRole("textbox", { name: "Search deposit, player, payment reference, or operation" })).toHaveValue("QA filter");
  expect(await page.evaluate(() => localStorage.getItem("bingo-admin-session"))).toContain("mocked-visual-test-token");
  await expect.poll(() => page.url()).toContain("/deposits?status=PENDING");
  await expect(page.getByRole("combobox", { name: "Status" })).toHaveText("Pending");
});

test("shows mocked zero, empty, and error states without inventing list data", async ({ page }) => {
  await mockAdminApi(page, {
    metrics: {
        ...metrics,
        activePlayers: 0,
        activeAgents: 0,
        activeSessions: 0,
        openDepositReviews: 0,
        openSupportTickets: 0,
        openWinClaims: 0,
        playerWalletBalanceTotal: "0.00",
        agentFloatAvailableTotal: "0.00",
        agentFloatClearingTotal: "0.00",
    },
    liveSessions: [],
    auditLogs: pageResponse,
  });
  await page.addInitScript(({ user }) => {
    localStorage.setItem("bingo-admin-session", JSON.stringify({
      accessToken: "mocked-empty-test-token",
      tokenType: "Bearer",
      user,
    }));
  }, { user: adminUser });

  await page.goto("/dashboard");
  await expect(page.getByText("No active sessions right now.")).toBeVisible();
  await expect(page.getByText("No audit history.")).toBeVisible();
  await expect(page.getByText("ETB 0.00").first()).toBeVisible();
});

test("shows a mocked metrics error with a retry instead of a zero-filled dashboard", async ({ page }) => {
  let allowMetrics = false;
  await mockAdminApi(page, { metricsUnavailable: () => !allowMetrics });
  await page.addInitScript(({ user }) => {
    localStorage.setItem("bingo-admin-session", JSON.stringify({
      accessToken: "mocked-error-test-token",
      tokenType: "Bearer",
      user,
    }));
  }, { user: adminUser });

  await page.goto("/dashboard");
  await expect(page.getByRole("heading", { name: "Unable to load dashboard" })).toBeVisible();
  allowMetrics = true;
  await page.getByRole("button", { name: "Retry" }).click();
  await expect(page.getByRole("heading", { name: "Needs attention" })).toBeVisible();
});
