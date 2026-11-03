import { mkdir, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import { expect, test } from "@playwright/test";

const playerName = "LongPlayerNameWithoutBreaks_".repeat(5);

test("keeps the player table scroll inside its container at mobile widths in both languages", async ({ page }, testInfo) => {
  const mutationMethods: string[] = [];
  const responsiveMeasurements: Array<{
    language: "en" | "am";
    viewportWidth: number;
    documentWidth: number;
    bodyWidth: number;
    tableContainerClientWidth: number;
    tableContainerScrollWidth: number;
  }> = [];
  page.on("request", (request) => {
    if (request.url().includes("/api/") && !["GET", "HEAD"].includes(request.method())) {
      mutationMethods.push(request.method());
    }
  });
  await page.route("**/api/v1/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/v1/auth/me") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          user: {
            id: 1,
            displayName: "QA Admin",
            phoneNumber: "251911000001",
            roles: ["ADMIN"],
            status: "ACTIVE",
            createdAt: "2026-11-03T00:00:00Z",
            updatedAt: "2026-11-03T00:00:00Z",
          },
        }),
      });
      return;
    }
    if (path === "/api/v1/admin/players") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          content: [{
            id: 2,
            displayName: playerName,
            normalizedPhone: "251911000002",
            roles: ["PLAYER"],
            status: "ACTIVE",
            createdAt: "2026-11-01T00:00:00Z",
            updatedAt: "2026-11-01T00:00:00Z",
            walletTransactionCount: 123,
            depositClaimCount: 45,
            supportTicketCount: 6,
          }],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        }),
      });
      return;
    }
    await route.fulfill({ status: 404, contentType: "application/json", body: "{}" });
  });
  await page.addInitScript(() => {
    if (!localStorage.getItem("bingo-admin-locale")) localStorage.setItem("bingo-admin-locale", "en");
    localStorage.setItem("bingo-admin-session", JSON.stringify({
      accessToken: "responsive-test-token",
      tokenType: "Bearer",
      user: {
        id: 1,
        displayName: "QA Admin",
        phoneNumber: "251911000001",
        roles: ["ADMIN"],
        status: "ACTIVE",
        createdAt: "2026-11-03T00:00:00Z",
        updatedAt: "2026-11-03T00:00:00Z",
      },
    }));
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/players");
  await expect(page.getByRole("heading", { name: "Players", exact: true })).toBeVisible();
  await expect(page.getByText(playerName)).toBeVisible();

  for (const language of ["en", "am"] as const) {
    if (language === "am") {
      await page.getByRole("combobox", { name: "Language" }).click();
      await page.getByRole("option", { name: "አማርኛ" }).click();
      await expect(page.getByRole("heading", { name: "ተጫዋቾች", exact: true })).toBeVisible();
      await expect.poll(() => page.evaluate(() => localStorage.getItem("bingo-admin-locale"))).toBe("am");
      await page.reload();
      await expect(page.getByText(playerName)).toBeVisible();
    }
    await expect(page.getByRole("heading", {
      name: language === "en" ? "Players" : "ተጫዋቾች",
      exact: true,
    })).toBeVisible();

    for (const width of [360, 390]) {
      await page.setViewportSize({ width, height: 844 });
      const measurements = await page.evaluate(() => {
        const table = document.querySelector("table");
        const tableContainer = table?.parentElement;
        return {
          viewportWidth: window.innerWidth,
          documentWidth: document.documentElement.scrollWidth,
          bodyWidth: document.body.scrollWidth,
          tableContainerClientWidth: tableContainer?.clientWidth ?? 0,
          tableContainerScrollWidth: tableContainer?.scrollWidth ?? 0,
        };
      });
      responsiveMeasurements.push({ language, ...measurements });
      expect(measurements.documentWidth, `${language} at ${width}px`).toBe(width);
      expect(measurements.bodyWidth, `${language} at ${width}px`).toBe(width);
      expect(measurements.tableContainerScrollWidth, `${language} at ${width}px`).toBeGreaterThan(
        measurements.tableContainerClientWidth,
      );
    }
  }

  expect(mutationMethods).toEqual([]);
  const evidencePath = testInfo.outputPath("player-responsive-widths.json");
  await mkdir(dirname(evidencePath), { recursive: true });
  await writeFile(evidencePath, JSON.stringify(responsiveMeasurements, null, 2), "utf8");
});
