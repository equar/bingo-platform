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
  await page.getByRole("heading", { name: "ዳሽቦርድ" }).click();
  await expect(page.getByRole("combobox", { name: "ቋንቋ" })).toHaveAttribute("aria-expanded", "false");
  await expect(page.getByRole("listbox")).not.toBeVisible();
  await expect(page.locator(".MuiMenu-paper")).not.toBeVisible();
  await expect(page.getByRole("option", { name: "English" })).not.toBeVisible();
}

async function switchToEnglish(page: import("@playwright/test").Page) {
  await page.getByRole("combobox", { name: "ቋንቋ" }).click();
  await page.getByRole("option", { name: "English" }).click();
  await expect(page.getByRole("heading", { name: "Deposits", exact: true })).toBeVisible();
  await page.getByRole("heading", { name: "Deposits", exact: true }).click();
  await expect(page.getByRole("combobox", { name: "Language" })).toHaveAttribute("aria-expanded", "false");
  await expect(page.getByRole("listbox")).not.toBeVisible();
  await expect(page.locator(".MuiMenu-paper")).not.toBeVisible();
  await expect(page.getByRole("option", { name: "አማርኛ" })).not.toBeVisible();
}

async function captureScreenshot(
  page: import("@playwright/test").Page,
  testInfo: import("@playwright/test").TestInfo,
  filename: string,
) {
  const screenshotPath = testInfo.outputPath(filename);
  await mkdir(dirname(screenshotPath), { recursive: true });
  await page.screenshot({ path: screenshotPath });
}

async function dashboardLayout(page: import("@playwright/test").Page) {
  return page.evaluate(() => {
    const main = document.querySelector<HTMLElement>("#admin-main-content");
    const content = document.querySelector<HTMLElement>('[data-testid="dashboard-content"]');
    if (!main || !content) {
      throw new Error("Dashboard layout landmarks are missing.");
    }

    const mainRect = main.getBoundingClientRect();
    const contentRect = content.getBoundingClientRect();
    const mainStyle = getComputedStyle(main);
    return {
      viewportWidth: document.documentElement.clientWidth,
      documentWidth: document.documentElement.scrollWidth,
      mainLeft: mainRect.left,
      mainRight: mainRect.right,
      contentLeft: contentRect.left,
      contentRight: contentRect.right,
      contentWidth: contentRect.width,
      paddingLeft: Number.parseFloat(mainStyle.paddingLeft),
      paddingRight: Number.parseFloat(mainStyle.paddingRight),
    };
  });
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

  await page.setViewportSize({ width: 1920, height: 1080 });
  await page.goto("/dashboard");
  await expect(page.getByRole("heading", { name: "Needs attention" })).toBeVisible();
  await expect(page.getByText("QA live session")).toBeVisible();
  await expect(page.getByText("Update session state")).toBeVisible();

  const dashboardLink = page.getByRole("link", { name: "Dashboard" });
  await expect(dashboardLink).toHaveAttribute("aria-current", "page");
  await captureScreenshot(page, testInfo, "dashboard-en-1920x1080-expanded.png");

  const expandedWidth = (await dashboardLayout(page)).contentWidth;
  await page.getByRole("button", { name: "Collapse navigation" }).click();
  await expect(page.getByRole("button", { name: "Expand navigation" })).toHaveAttribute("aria-pressed", "true");
  await expect.poll(() => page.evaluate(() => localStorage.getItem("bingo-admin-nav-collapsed"))).toBe("true");
  await expect.poll(async () => (await dashboardLayout(page)).contentWidth).toBeGreaterThan(expandedWidth + 150);
  await page.getByRole("link", { name: "Dashboard" }).hover();
  await expect(page.getByRole("tooltip", { name: "Dashboard" })).toBeVisible();
  await page.mouse.move(1800, 1000);
  await captureScreenshot(page, testInfo, "dashboard-en-1920x1080-collapsed.png");
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
      { name: "1920x1080", width: 1920, height: 1080 },
      { name: "1440x900", width: 1440, height: 900 },
      { name: "1024x768", width: 1024, height: 768 },
      { name: "mobile", width: 390, height: 844 },
    ]) {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      if (viewport.name === "mobile") {
        const openButtonName = language === "en" ? "Open navigation" : "የአሰሳ ምናሌን ክፈት";
        const menuButton = page.getByRole("button", { name: openButtonName });
        await menuButton.click();
        const navigationName = language === "en" ? "Primary navigation" : "ዋና የአሰሳ ምናሌ";
        const navigation = page.getByRole("navigation", { name: navigationName });
        const drawerPaper = page.locator(".MuiDrawer-paper");
        await expect(navigation).toBeVisible();
        await expect.poll(() => drawerPaper.evaluate((paper) => paper.getBoundingClientRect().left)).toBe(0);
        await captureScreenshot(page, testInfo, `dashboard-${language}-${viewport.name}-drawer-open.png`);
        await page.keyboard.press("Escape");
        await expect(menuButton).toBeFocused();
        await expect(navigation).not.toBeVisible();
        await expect.poll(() => drawerPaper.evaluate((paper) => paper.getBoundingClientRect().left)).toBeLessThan(-200);
      }

      const layout = await dashboardLayout(page);
      expect(layout.documentWidth, `${language} ${viewport.name} horizontal overflow`).toBe(layout.viewportWidth);
      expect(layout.contentLeft, `${language} ${viewport.name} left padding`).toBeCloseTo(
        layout.mainLeft + layout.paddingLeft,
        0,
      );
      expect(layout.contentRight, `${language} ${viewport.name} right padding`).toBeCloseTo(
        layout.mainRight - layout.paddingRight,
        0,
      );

      const attentionSection = page.locator('[aria-labelledby="needs-attention-title"]');
      const attentionHeading = await page.locator("#needs-attention-title").boundingBox();
      const firstAttentionCardLeft = await attentionSection.getByRole("link").first().evaluate(
        (link) => link.parentElement?.getBoundingClientRect().left ?? Number.NaN,
      );
      expect(firstAttentionCardLeft).toBeCloseTo(attentionHeading?.x ?? 0, 0);

      const operationsSection = page.locator('[aria-labelledby="operations-title"]');
      const operationLinks = operationsSection.getByRole("link");
      await expect(operationLinks).toHaveCount(6);
      const operationBoxes = await operationLinks.evaluateAll((links) => links.map((link) => {
        const { x, y } = link.getBoundingClientRect();
        return { x, y };
      }));
      const columns = viewport.width >= 1200 ? 6 : viewport.width >= 900 ? 3 : viewport.width >= 600 ? 2 : 1;
      const rows = new Set(operationBoxes.map(({ y }) => Math.round(y)));
      expect(rows.size, `${language} ${viewport.name} operations rows`).toBe(Math.ceil(6 / columns));

      await captureScreenshot(page, testInfo, `dashboard-${language}-${viewport.name}-expanded.png`);
    }

    if (language === "am") {
      await page.setViewportSize({ width: 1920, height: 1080 });
      await page.evaluate(() => localStorage.removeItem("bingo-admin-nav-collapsed"));
      await page.reload();
      await expect(page.getByRole("heading", { name: "ዳሽቦርድ" })).toBeVisible();
      const expandedAmharicWidth = (await dashboardLayout(page)).contentWidth;
      await page.getByRole("button", { name: "የአሰሳ ምናሌን አጠብብ" }).click();
      await expect.poll(async () => (await dashboardLayout(page)).contentWidth).toBeGreaterThan(
        expandedAmharicWidth + 150,
      );
      await captureScreenshot(page, testInfo, "dashboard-am-1920x1080-collapsed.png");
      await page.getByRole("button", { name: "የአሰሳ ምናሌን ዘርጋ" }).click();
    }
  }

  await page.setViewportSize({ width: 1440, height: 900 });
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

test("shows mocked zero and empty states without inventing list data", async ({ page }, testInfo) => {
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

  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/dashboard");
  await expect(page.getByText("No active sessions right now.")).toBeVisible();
  await expect(page.getByText("No audit history.")).toBeVisible();
  await expect(page.getByText("ETB 0.00").first()).toBeVisible();
  await captureScreenshot(page, testInfo, "dashboard-empty-1440x900.png");
});

test("shows a mocked metrics error with a retry instead of a zero-filled dashboard", async ({ page }, testInfo) => {
  let allowMetrics = false;
  await mockAdminApi(page, { metricsUnavailable: () => !allowMetrics });
  await page.addInitScript(({ user }) => {
    localStorage.setItem("bingo-admin-session", JSON.stringify({
      accessToken: "mocked-error-test-token",
      tokenType: "Bearer",
      user,
    }));
  }, { user: adminUser });

  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/dashboard");
  await expect(page.getByRole("heading", { name: "Unable to load dashboard" })).toBeVisible();
  await captureScreenshot(page, testInfo, "dashboard-error-1440x900.png");
  allowMetrics = true;
  await page.getByRole("button", { name: "Retry" }).click();
  await expect(page.getByRole("heading", { name: "Needs attention" })).toBeVisible();
});

test("keeps long ETB totals readable without page overflow (mocked)", async ({ page }, testInfo) => {
  await mockAdminApi(page, {
    metrics: {
      ...metrics,
      playerWalletBalanceTotal: "123456789012.99",
      agentFloatAvailableTotal: "9876543210.50",
      agentFloatClearingTotal: "7654321098.25",
    },
  });
  await page.addInitScript(({ user }) => {
    localStorage.setItem("bingo-admin-session", JSON.stringify({
      accessToken: "mocked-long-amounts-token",
      tokenType: "Bearer",
      user,
    }));
  }, { user: adminUser });

  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/dashboard");
  await expect(page.getByText("ETB 123,456,789,012.99")).toBeVisible();
  await expect(page.getByText("ETB 9,876,543,210.50")).toBeVisible();
  expect((await dashboardLayout(page)).documentWidth).toBe(1440);
  await captureScreenshot(page, testInfo, "dashboard-long-amounts-1440x900.png");

  await switchToAmharic(page);
  expect((await dashboardLayout(page)).documentWidth).toBe(1440);
  await captureScreenshot(page, testInfo, "dashboard-long-amounts-am-1440x900.png");
});
