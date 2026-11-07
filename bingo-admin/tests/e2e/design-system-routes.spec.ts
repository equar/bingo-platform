import { mkdir } from "node:fs/promises";
import { dirname } from "node:path";
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

const adminRoutes = [
  "/dashboard",
  "/players",
  "/players/12",
  "/player-verifications",
  "/agents",
  "/agents/12",
  "/rooms",
  "/rooms/new",
  "/rooms/12/edit",
  "/sessions",
  "/sessions/new",
  "/sessions/12",
  "/session-tickets",
  "/community",
  "/deposits",
  "/claims",
  "/prize-entitlements",
  "/support/tickets",
  "/support/tickets/12",
  "/wallet/signup-reward",
  "/wallet/transfers",
  "/admin-users",
  "/audit-logs",
  "/reports",
  "/profile",
  "/change-password",
];

test("all Admin routes render within the shared shell without touching application data", async ({ page }, testInfo) => {
  const pageErrors: string[] = [];
  page.on("pageerror", (error) => pageErrors.push(error.message));
  await page.route("**/api/v1/**", async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname === "/api/v1/auth/me") {
      await route.fulfill({ json: { user: { ...adminUser, preferredLanguage: "en" } } });
    } else {
      await route.fulfill({
        status: 404,
        json: { status: 404, code: "NOT_FOUND", message: "Visual verification fixture has no record." },
      });
    }
  });
  await page.addInitScript(({ user }) => {
    localStorage.setItem("bingo-admin-session", JSON.stringify({
      accessToken: "mocked-design-system-token",
      tokenType: "Bearer",
      user,
    }));
  }, { user: adminUser });

  for (const path of adminRoutes) {
    await page.goto(path);
    await expect(page.locator("#admin-main-content")).toBeVisible();
    await expect(page.getByRole("heading").first()).toBeVisible();
  }

  await page.goto("/login");
  await expect(page.getByRole("heading", { name: "Sunset Bingo Admin" })).toBeVisible();
  const desktopPath = testInfo.outputPath("login-desktop.png");
  await mkdir(dirname(desktopPath), { recursive: true });
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.screenshot({ path: desktopPath });
  const mobilePath = testInfo.outputPath("login-mobile.png");
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: mobilePath });
  expect(pageErrors).toEqual([]);
});
