import { randomBytes } from 'node:crypto';
import { expect, test, type Page } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { loginAdmin, qaBaseUrls } from './support/qaHelpers';

const backend = qaBaseUrls().backend;
const evidenceDir = path.resolve(process.cwd(), '../docs/qa/evidence');
const suffix = `${Date.now()}-${randomBytes(3).toString('hex')}`;
let pageErrors: string[] = [];
let consoleErrors: string[] = [];

test.beforeEach(async ({ page }) => {
  pageErrors = [];
  consoleErrors = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') consoleErrors.push(message.text()); });
});

test.afterEach(() => {
  expect(pageErrors, 'browser page errors').toEqual([]);
  expect(consoleErrors, 'browser console errors').toEqual([]);
});

async function token(page: Page) {
  return page.evaluate(() => JSON.parse(localStorage.getItem('bingo-admin-session') ?? '{}').accessToken as string);
}

async function screenshot(page: Page, name: string) {
  await mkdir(evidenceDir, { recursive: true });
  await page.screenshot({ path: path.join(evidenceDir, `module-02e-${name}.png`), fullPage: true });
}

test('G01-G07: admin creates a synthetic room configuration and verifies it after refresh', async ({ page }) => {
  await loginAdmin(page);
  const accessToken = await token(page);
  await page.goto('/rooms');
  await expect(page.getByRole('heading', { name: 'Rooms' })).toBeVisible();
  const name = `QA M02E Config ${suffix}`;
  await page.getByRole('button', { name: 'New room' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Room name').fill(name);
  await dialog.getByLabel('Ticket price').fill('2.50');
  await dialog.getByLabel('Schedule rule').click();
  await page.getByRole('option', { name: 'Daily', exact: true }).click();
  const responsePromise = page.waitForResponse(response =>
    response.url().endsWith('/api/v1/admin/rooms') && response.request().method() === 'POST');
  await dialog.getByRole('button', { name: 'Save', exact: true }).click();
  await page.getByRole('button', { name: 'Confirm', exact: true }).click();
  const response = await responsePromise;
  expect(response.status()).toBe(200);
  const room = await response.json() as { id: number; name: string; ticketPrice: number; currency: string; scheduleRule: string; active: boolean; version: number };
  expect(room).toMatchObject({ name, ticketPrice: 2.5, currency: 'ETB', scheduleRule: 'DAILY', active: true });

  await page.getByLabel('Search rooms').fill(name);
  const row = page.getByRole('row').filter({ hasText: name });
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: 'Edit', exact: true }).click();
  await page.getByRole('dialog').getByLabel('Ticket price').fill('2.75');
  const updatePromise = page.waitForResponse(item =>
    item.url().endsWith(`/api/v1/admin/rooms/${room.id}`) && item.request().method() === 'PUT');
  await page.getByRole('dialog').getByRole('button', { name: 'Save', exact: true }).click();
  await page.getByRole('button', { name: 'Confirm', exact: true }).click();
  const update = await updatePromise;
  expect(update.status()).toBe(200);
  const updatedRoom = await update.json() as typeof room;
  expect(updatedRoom.ticketPrice).toBe(2.75);
  expect(updatedRoom.version).toBe(room.version + 1);

  const stale = await page.request.put(`${backend}/api/v1/admin/rooms/${room.id}`, {
    headers: { Authorization: `Bearer ${accessToken}` }, data: room,
  });
  expect(stale.status()).toBe(409);
  expect((await stale.json()).code).toBe('ROOM_VERSION_CONFLICT');
  const invalid = await page.request.put(`${backend}/api/v1/admin/rooms/${room.id}`, {
    headers: { Authorization: `Bearer ${accessToken}` }, data: { ...updatedRoom, ticketPrice: '0.00' },
  });
  expect(invalid.status()).toBe(400);
  const reread = await page.request.get(`${backend}/api/v1/admin/rooms?query=${encodeURIComponent(name)}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  expect(reread.status()).toBe(200);
  expect(((await reread.json()).content as Array<{ id: number; ticketPrice: number; version: number }>)[0])
    .toMatchObject({ id: room.id, ticketPrice: 2.75, version: updatedRoom.version });
  await screenshot(page, 'room-updated');
  await page.reload();
  await expect(page.getByText(name, { exact: true })).toBeVisible();
  await expect(row.getByText('2.75 ETB')).toBeVisible();
  const audit = await page.request.get(`${backend}/api/v1/admin/audit-logs?page=0&size=100`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  expect(audit.status()).toBe(200);
  const auditRows = (await audit.json()).content as Array<{ action: string; targetId: number }>;
  expect(auditRows.some(entry => entry.action === 'CREATE_ROOM' && entry.targetId === room.id)).toBeTruthy();
});

test('S01-S04, S08, S11: admin creates and refreshes a DRAFT session with rule snapshots', async ({ page }) => {
  await loginAdmin(page);
  const accessToken = await token(page);
  const roomName = `QA M02E Session Room ${suffix}`;
  const roomResponse = await page.request.post(`${backend}/api/v1/admin/rooms`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: {
      name: roomName, description: 'Synthetic Module 02E test room', ticketPrice: '3.25', currency: 'ETB',
      timezone: 'Africa/Addis_Ababa', scheduleRule: 'DAILY', sessionCapacity: 10, maxTicketsPerPlayer: 2,
      ticketSalesCloseOnSessionStart: true, allowMultipleWinnersPerPrize: false, active: true,
      configuredPatterns: ['LINE'],
      configuredPrizes: [{ prizeType: 'LINE', amount: '5.00', settlementPath: 'PLAYER_WALLET', maxWinners: 1 }],
    },
  });
  expect(roomResponse.status()).toBe(200);
  const room = await roomResponse.json() as { id: number };

  await page.goto('/sessions');
  await expect(page.getByRole('heading', { name: 'Sessions' })).toBeVisible();
  await page.getByRole('button', { name: 'New session' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Room').click();
  await page.getByRole('option', { name: roomName, exact: true }).click();
  const name = `QA M02E DRAFT ${suffix}`;
  await dialog.getByLabel('Session name').fill(name);
  const start = new Date(Date.now() + 60 * 60_000);
  await dialog.getByLabel('Scheduled start').fill(new Date(start.getTime() - start.getTimezoneOffset() * 60_000).toISOString().slice(0, 16));
  await dialog.getByLabel('Initial state').click();
  await page.getByRole('option', { name: 'DRAFT', exact: true }).click();
  const responsePromise = page.waitForResponse(response =>
    response.url().includes(`/api/v1/admin/rooms/${room.id}/sessions`) && response.request().method() === 'POST');
  await dialog.getByRole('button', { name: 'Create', exact: true }).click();
  const response = await responsePromise;
  expect(response.status()).toBe(200);
  const session = await response.json() as { id: number; sessionName: string; state: string; ticketPrice: number; currency: string; configuredPatterns: string[]; drawCount: number };
  expect(session).toMatchObject({ sessionName: name, state: 'DRAFT', ticketPrice: 3.25, currency: 'ETB', configuredPatterns: ['LINE'], drawCount: 0 });
  await expect(page.getByText(name, { exact: true })).toBeVisible();
  await screenshot(page, 'draft-session-created');
  await page.reload();
  await expect(page.getByText(name, { exact: true })).toBeVisible();
  await expect(page.getByText('DRAFT', { exact: true }).first()).toBeVisible();
});

test('G09: backend rejects session creation for a disabled room without creating a session', async ({ page }) => {
  await loginAdmin(page);
  const accessToken = await token(page);
  const roomName = `QA M02E Inactive Room ${suffix}`;
  const createdRoom = await page.request.post(`${backend}/api/v1/admin/rooms`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: {
      name: roomName, ticketPrice: '1.00', currency: 'ETB', timezone: 'Africa/Addis_Ababa',
      scheduleRule: 'DAILY', sessionCapacity: 1, maxTicketsPerPlayer: 1,
      ticketSalesCloseOnSessionStart: true, allowMultipleWinnersPerPrize: false, active: false,
      configuredPatterns: ['LINE'],
      configuredPrizes: [{ prizeType: 'LINE', amount: '1.00', settlementPath: 'PLAYER_WALLET', maxWinners: 1 }],
    },
  });
  expect(createdRoom.status()).toBe(200);
  const room = await createdRoom.json() as { id: number };
  const start = new Date(Date.now() + 90 * 60_000).toISOString();
  const attempt = await page.request.post(`${backend}/api/v1/admin/rooms/${room.id}/sessions`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: { sessionName: `QA M02E Forbidden ${suffix}`, scheduledStartAt: start, state: 'DRAFT' },
  });
  expect(attempt.status()).toBe(409);
  expect((await attempt.json()).code).toBe('ROOM_INACTIVE');
  const list = await page.request.get(`${backend}/api/v1/admin/sessions?query=${encodeURIComponent(`QA M02E Forbidden ${suffix}`)}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  expect(list.status()).toBe(200);
  expect((await list.json()).totalElements).toBe(0);
});

test('S09-S11: admin cancels a synthetic ticket-free DRAFT session and the state survives refresh', async ({ page }) => {
  await loginAdmin(page);
  const accessToken = await token(page);
  const roomName = `QA M02E Cancel Room ${suffix}`;
  const roomResponse = await page.request.post(`${backend}/api/v1/admin/rooms`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: {
      name: roomName, ticketPrice: '1.00', currency: 'ETB', timezone: 'Africa/Addis_Ababa',
      scheduleRule: 'DAILY', sessionCapacity: 1, maxTicketsPerPlayer: 1,
      ticketSalesCloseOnSessionStart: true, allowMultipleWinnersPerPrize: false, active: true,
      configuredPatterns: ['LINE'],
      configuredPrizes: [{ prizeType: 'LINE', amount: '1.00', settlementPath: 'PLAYER_WALLET', maxWinners: 1 }],
    },
  });
  expect(roomResponse.status()).toBe(200);
  const room = await roomResponse.json() as { id: number };
  const sessionName = `QA M02E Cancel DRAFT ${suffix}`;
  const created = await page.request.post(`${backend}/api/v1/admin/rooms/${room.id}/sessions`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: { sessionName, scheduledStartAt: new Date(Date.now() + 2 * 60 * 60_000).toISOString(), state: 'DRAFT' },
  });
  expect(created.status()).toBe(200);
  const session = await created.json() as { id: number; ticketCount: number; state: string };
  expect(session).toMatchObject({ ticketCount: 0, state: 'DRAFT' });

  await page.goto('/sessions');
  await page.getByLabel('Search sessions').fill(sessionName);
  const row = page.getByRole('row').filter({ hasText: sessionName });
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: 'CANCELLED', exact: true }).click();
  const transition = page.waitForResponse(item =>
    item.url().endsWith(`/api/v1/admin/sessions/${session.id}/state`) && item.request().method() === 'PUT');
  await page.getByRole('button', { name: 'Confirm', exact: true }).click();
  const response = await transition;
  expect(response.status()).toBe(200);
  expect((await response.json()).state).toBe('CANCELLED');
  await expect(row.getByText('CANCELLED', { exact: true })).toBeVisible();
  await screenshot(page, 'draft-session-cancelled');
  await page.reload();
  await page.getByLabel('Search sessions').fill(sessionName);
  await expect(page.getByRole('row').filter({ hasText: sessionName }).getByText('CANCELLED', { exact: true })).toBeVisible();

  const audit = await page.request.get(`${backend}/api/v1/admin/audit-logs?page=0&size=100`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  expect(audit.status()).toBe(200);
  const entries = (await audit.json()).content as Array<{ action: string; targetId: number }>;
  expect(entries.some(entry => entry.action === 'UPDATE_SESSION_STATE' && entry.targetId === session.id)).toBeTruthy();
});

test('G02 and S07: anonymous callers cannot view or mutate game administration', async ({ page }) => {
  const anonymous = await page.request.get(`${backend}/api/v1/admin/rooms`);
  expect(anonymous.status()).toBe(401);
  const denied = await page.request.post(`${backend}/api/v1/admin/rooms`, {
    data: { name: `QA M02E Unauthorized ${suffix}`, ticketPrice: '2', currency: 'ETB', timezone: 'Africa/Addis_Ababa', scheduleRule: 'DAILY', sessionCapacity: 1, maxTicketsPerPlayer: 1, active: true, configuredPatterns: ['LINE'], configuredPrizes: [] },
  });
  expect(denied.status()).toBe(401);
});

test('C11: prize settlement remains blocked in the admin UI and API until finance controls are approved', async ({ page }) => {
  await loginAdmin(page);
  const accessToken = await token(page);
  const listPromise = page.waitForResponse(response =>
    response.url().includes('/api/v1/admin/prize-entitlements') && response.request().method() === 'GET');
  await page.goto('/prize-entitlements');
  await expect(page.getByRole('heading', { name: 'Prize entitlements' })).toBeVisible();
  await expect(page.getByText(/Prize settlement is blocked until payout evidence/)).toBeVisible();
  const list = await listPromise;
  expect(list.status()).toBe(200);
  expect((await list.json()).totalElements).toBe(0);
  await screenshot(page, 'prize-settlement-blocked');

  const attempt = await page.request.post(`${backend}/api/v1/admin/prize-entitlements/1/settle`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: { settlementReference: `QA-M02E-BLOCKED-${suffix}`, settlementNote: 'Synthetic guard verification only' },
  });
  expect(attempt.status()).toBe(409);
  expect((await attempt.json()).code).toBe('PRIZE_SETTLEMENT_BLOCKED');
});
