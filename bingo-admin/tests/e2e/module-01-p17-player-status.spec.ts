import { expect, test } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { loginAdmin, qaBaseUrls, requiredQaEnv } from './support/qaHelpers';

test('P17: admin restricts a registered player and protected wallet access is denied', async ({ page }, testInfo) => {
  const evidenceDirectory = path.resolve(process.cwd(), '../docs/qa/evidence');
  const phone = requiredQaEnv('BINGO_E2E_P17_PHONE');
  const pin = requiredQaEnv('BINGO_E2E_P17_PIN');
  const backend = qaBaseUrls().backend;

  await loginAdmin(page);
  const adminSession = await page.evaluate(() => JSON.parse(localStorage.getItem('bingo-admin-session') ?? '{}'));
  await page.goto('/players');
  const search = page.getByRole('textbox', { name: 'Search by name, email, or phone' });
  await search.fill(phone);
  const matchingRow = page.getByRole('row').filter({ hasText: phone });
  await expect(matchingRow).toBeVisible();
  const playerLink = matchingRow.getByRole('link').first();
  const playerHref = await playerLink.getAttribute('href');
  const playerId = Number(playerHref?.split('/').pop());
  expect(Number.isSafeInteger(playerId) && playerId > 0).toBe(true);
  await playerLink.click();
  await expect(page.getByRole('heading', { name: 'QA Module 01 Restricted Player' })).toBeVisible();

  const adminHeaders = { Authorization: `Bearer ${adminSession.accessToken}` };
  const currentPlayerResponse = await page.request.get(`${backend}/api/v1/admin/players/${playerId}`, { headers: adminHeaders });
  expect(currentPlayerResponse.status()).toBe(200);
  const currentPlayer = await currentPlayerResponse.json();
  if (currentPlayer.status !== 'ACTIVE') {
    const activateResponse = await setPlayerStatus(page, playerId, 'ACTIVE', 'Prepare Module 01 restriction verification');
    expect(activateResponse.status()).toBe(200);
  }

  const playerLogin = await page.request.post(`${backend}/api/v1/auth/login`, {
    data: { phoneNumber: phone, password: pin },
  });
  expect(playerLogin.status()).toBe(200);
  const playerAuth = await playerLogin.json();
  const walletBeforeResponse = await page.request.get(`${backend}/api/v1/wallet/balance`, {
    headers: { Authorization: `Bearer ${playerAuth.accessToken}` },
  });
  expect(walletBeforeResponse.status()).toBe(200);
  const walletBefore = await walletBeforeResponse.json();

  const statusResponse = await setPlayerStatus(page, playerId, 'PENDING', 'Module 01 restricted-player authentication verification');
  expect(statusResponse.status()).toBe(200);
  expect((await statusResponse.json()).status).toBe('PENDING');
  await expect(page.getByText('PENDING', { exact: true })).toBeVisible();

  const oldSessionWalletResponse = await page.request.get(`${backend}/api/v1/wallet/balance`, {
    headers: { Authorization: `Bearer ${playerAuth.accessToken}` },
  });
  const noSessionWalletResponse = await page.request.get(`${backend}/api/v1/wallet/balance`);
  expect(oldSessionWalletResponse.status()).toBe(401);
  expect(noSessionWalletResponse.status()).toBe(401);

  const restrictedPlayer = await page.request.get(`${backend}/api/v1/admin/players/${playerId}`, {
    headers: { Authorization: `Bearer ${adminSession.accessToken}` },
  });
  expect(restrictedPlayer.status()).toBe(200);
  const playerAfter = await restrictedPlayer.json();
  expect(playerAfter.status).toBe('PENDING');
  expect(playerAfter.walletAvailableBalance).toBe(walletBefore.availableBalance);
  expect(playerAfter.walletPromotionalBalance).toBe(walletBefore.promotionalBalance);
  const auditResponse = await page.request.get(`${backend}/api/v1/admin/audit-logs?page=0&size=50`, { headers: adminHeaders });
  expect(auditResponse.status()).toBe(200);
  const auditPage = await auditResponse.json();
  const statusAuditPresent = auditPage.content.some((entry: { action: string; targetId: number }) =>
    entry.action === 'UPDATE_PLAYER_STATUS' && entry.targetId === playerId,
  );
  expect(statusAuditPresent).toBe(true);

  await mkdir(evidenceDirectory, { recursive: true });
  await page.screenshot({ path: path.join(evidenceDirectory, 'module-01-p17-admin-player-pending.png'), fullPage: true });
  const evidencePath = path.join(evidenceDirectory, 'module-01-p17-status-api.json');
  await writeFile(evidencePath, JSON.stringify({
    playerId,
    statusBefore: 'ACTIVE',
    statusAfter: playerAfter.status,
    adminStatusUpdateHttp: statusResponse.status(),
    oldSessionProtectedWalletHttp: oldSessionWalletResponse.status(),
    anonymousProtectedWalletHttp: noSessionWalletResponse.status(),
    walletBalancesUnchanged: true,
    adminAuditRecordPresent: statusAuditPresent,
  }, null, 2), 'utf8');
  await testInfo.attach('p17-status-api-evidence', { path: evidencePath, contentType: 'application/json' });
});

async function setPlayerStatus(page: import('@playwright/test').Page, playerId: number, status: string, reason: string) {
  await page.getByRole('button', { name: 'Change account status' }).click();
  await page.getByRole('combobox', { name: 'Account status' }).click();
  await page.getByRole('option', { name: status, exact: true }).click();
  await page.getByRole('textbox', { name: 'Reason' }).fill(reason);
  const responsePromise = page.waitForResponse((response) =>
    response.url().endsWith(`/api/v1/admin/players/${playerId}/status`) && response.request().method() === 'PUT',
  );
  await page.getByRole('button', { name: 'Save status' }).click();
  return responsePromise;
}
