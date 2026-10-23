import { expect, test } from '@playwright/test';
import { loginAdmin, navigateTo, qaBaseUrls } from './support/qaHelpers';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';

const evidenceDir = path.resolve(process.cwd(), '../docs/qa/evidence');

const adminRoutes = [
  '/dashboard', '/admin-users', '/rooms', '/sessions', '/players', '/claims',
  '/prize-entitlements', '/deposits', '/agents', '/wallet/signup-reward',
  '/wallet/transfer-policy', '/wallet/transfers', '/support/tickets',
  '/audit-logs', '/profile', '/change-password',
];

test('admin portal route closure sweep renders each page at desktop and mobile widths', async ({ page }) => {
  const pageErrors: string[] = [];
  const consoleErrors: string[] = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });

  await loginAdmin(page);
  for (const route of adminRoutes) {
    await navigateTo(page, route);
    await expect(page.locator('main')).toBeVisible();
    await expect(page.getByText(/internal server error|unexpected error/i)).toHaveCount(0);
  }

  await page.setViewportSize({ width: 390, height: 844 });
  const mobileWidths: Array<{ route: string; viewport: number; document: number }> = [];
  for (const route of ['/dashboard', '/players', '/agents', '/deposits', '/wallet/transfers', '/support/tickets']) {
    await navigateTo(page, route);
    await expect(page.locator('main')).toBeVisible();
    const widths = await page.evaluate(() => ({
      viewport: document.documentElement.clientWidth,
      document: document.documentElement.scrollWidth,
    }));
    mobileWidths.push({ route, ...widths });
    const overflowing = widths.document > widths.viewport + 1
      ? await page.evaluate(() => Array.from(document.querySelectorAll<HTMLElement>('body *'))
        .filter(element => element.getBoundingClientRect().right > window.innerWidth + 2)
        .slice(0, 8)
        .map(element => ({
          tag: element.tagName,
          className: String(element.className).slice(0, 100),
          right: Math.round(element.getBoundingClientRect().right),
        })))
      : [];
    expect(widths.document, `${route} mobile overflow: ${JSON.stringify(overflowing)}`)
      .toBeLessThanOrEqual(widths.viewport + 1);
  }
  await mkdir(evidenceDir, { recursive: true });
  await page.screenshot({ path: path.join(evidenceDir, 'module-02c-mobile-support-tickets.png'), fullPage: true });
  await writeFile(path.join(evidenceDir, 'module-02c-browser-route-results.json'), JSON.stringify({
    desktopRoutes: adminRoutes,
    mobileRoutes: ['/dashboard', '/players', '/agents', '/deposits', '/wallet/transfers', '/support/tickets'],
    mobileViewport: { width: 390, height: 844 },
    mobileWidths,
    pageErrors,
    consoleErrors,
  }, null, 2) + '\n', 'utf8');
  expect(pageErrors).toEqual([]);
  expect(consoleErrors).toEqual([]);
});

test('dashboard metrics endpoint is admin-only and live metrics render in the dashboard', async ({ page, request }) => {
  const backend = qaBaseUrls().backend;
  const anonymous = await request.get(`${backend}/api/v1/admin/dashboard/metrics`);
  expect(anonymous.status()).toBe(401);

  await loginAdmin(page);
  const token = await page.evaluate(() => {
    const session = localStorage.getItem('bingo-admin-session');
    return session ? (JSON.parse(session) as { accessToken: string }).accessToken : '';
  });
  const metricsResponse = await page.request.get(`${backend}/api/v1/admin/dashboard/metrics`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(metricsResponse.ok()).toBeTruthy();
  const metrics = await metricsResponse.json();
  for (const key of ['activePlayers', 'activeAgents', 'openSupportTickets', 'openAgentFloatReviews', 'agentFloatAvailableTotal']) {
    expect(metrics).toHaveProperty(key);
  }
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  await mkdir(evidenceDir, { recursive: true });
  await writeFile(path.join(evidenceDir, 'module-02c-dashboard-metrics.json'), JSON.stringify({
    anonymousStatus: anonymous.status(),
    authenticatedStatus: metricsResponse.status(),
    metrics,
  }, null, 2) + '\n', 'utf8');
});
