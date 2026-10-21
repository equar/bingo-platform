import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { readManifest, writeManifest } from './support/localE2e';

test.describe.configure({ mode: 'serial' });

const runtimeEnv = (globalThis as any)['process']?.env ?? {};

const phase = runtimeEnv.BINGO_E2E_PHASE ?? 'all';
const runId = runtimeEnv.BINGO_E2E_RUN_ID ?? 'local';
const backendBaseUrl = runtimeEnv.BINGO_E2E_BACKEND_URL ?? 'http://localhost:8080';
const adminEmail = requiredEnv('BINGO_E2E_ADMIN_EMAIL');
const adminPassword = requiredEnv('BINGO_E2E_ADMIN_PASSWORD');
const primaryPhone = requiredEnv('BINGO_E2E_PRIMARY_PHONE');

function requiredEnv(name: string): string {
  const value = runtimeEnv[name];
  if (typeof value !== 'string' || !value.trim()) {
    throw new Error(`${name} must be set to run the admin E2E tests`);
  }
  return value;
}

function phaseEnabled(expectedPhase: string) {
  return phase === 'all' || phase === expectedPhase;
}

async function loginUi(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Email address').fill(adminEmail);
  await page.locator('input[name="password"]').fill(adminPassword);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
}

async function adminToken(request: APIRequestContext) {
  const response = await request.post(`${backendBaseUrl}/api/v1/admin/login`, {
    data: { email: adminEmail, password: adminPassword },
  });
  expect(response.ok()).toBeTruthy();
  const payload = (await response.json()) as { accessToken: string };
  return payload.accessToken;
}

async function apiJson(request: APIRequestContext, token: string, method: string, url: string, data?: unknown) {
  const response = await request.fetch(`${backendBaseUrl}${url}`, {
    method,
    data,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(data == null ? {} : { 'Content-Type': 'application/json' }),
      ...(method === 'GET' ? {} : { 'Idempotency-Key': `${runId}:${method}:${url}` }),
    },
  });
  if (!response.ok()) {
    throw new Error(`${method} ${url} failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

function roomDraft(runName: string) {
  return {
    name: `${runName} Room`,
    description: `Automated test room for ${runName}`,
    ticketPrice: 25,
    currency: 'ETB',
    timezone: 'Africa/Addis_Ababa',
    scheduleRule: 'WEEKLY',
    sessionCapacity: 100,
    maxTicketsPerPlayer: 3,
    ticketSalesCloseOnSessionStart: true,
    allowMultipleWinnersPerPrize: true,
    active: true,
    configuredPatterns: ['LINE', 'TWO_LINES', 'FULL_HOUSE'],
    configuredPrizes: [{ prizeType: 'LINE', amount: 100, settlementPath: 'PLAYER_WALLET', maxWinners: 1 }],
  };
}

test('setup local session', async ({ page, request }) => {
  if (!phaseEnabled('setup')) {
    return;
  }

  await loginUi(page);
  const token = await adminToken(request);
  const rooms = (await apiJson(request, token, 'GET', '/api/v1/rooms')) as Array<{ id: number; name: string }>;
  const room = rooms.find((item) => item.name === `${runId} Room`) ?? rooms[0] ?? (await apiJson(request, token, 'POST', '/api/v1/admin/rooms', roomDraft(runId))) as { id: number; name: string };
  const session = (await apiJson(request, token, 'POST', `/api/v1/admin/rooms/${room.id}/sessions`, {
    sessionName: `${runId} Session`,
    scheduledStartAt: new Date(Date.now() + 5 * 60 * 1000).toISOString(),
    state: 'OPEN',
  })) as { id: number; sessionName: string };

  await writeManifest({
    runId,
    backendBaseUrl: runtimeEnv.BINGO_E2E_BACKEND_URL ?? 'http://localhost:8080',
    adminBaseUrl: runtimeEnv.BINGO_E2E_ADMIN_URL ?? 'http://localhost:5173',
    room: { id: room.id, name: room.name },
    session: { id: session.id, name: session.sessionName },
    player: { phoneNumber: primaryPhone, displayName: 'Dev Player' },
    notes: ['session created through admin api'],
  });

  await page.goto(`/sessions/${session.id}`);
  await expect(page.getByText(session.sessionName)).toBeVisible();
});

test('review deposits', async ({ page, request }) => {
  if (!phaseEnabled('review-deposits')) {
    return;
  }

  const manifest: any = (await readManifest()) ?? {};
  const deposits = (manifest.deposits ?? []) as Array<{ id?: number; telebirrReference?: string; amount?: string }>;
  const depositRefs = deposits.map((item) => item.telebirrReference).filter((value): value is string => Boolean(value));
  expect(depositRefs.length).toBeGreaterThanOrEqual(2);

  await loginUi(page);
  const token = await adminToken(request);
  const depositPage = (await apiJson(request, token, 'GET', '/api/v1/admin/deposits?size=50')) as {
    content: Array<{ id: number; normalizedTelebirrReference: string; status: string }>;
  };

  const first = depositPage.content.find((item) => item.normalizedTelebirrReference === depositRefs[0]);
  const second = depositPage.content.find((item) => item.normalizedTelebirrReference === depositRefs[1]);
  expect(first).toBeTruthy();
  expect(second).toBeTruthy();

  await apiJson(request, token, 'POST', `/api/v1/admin/deposits/${first!.id}/approve`, {
    reviewNote: `Approved by local E2E ${runId}`,
  });
  await apiJson(request, token, 'POST', `/api/v1/admin/deposits/${second!.id}/reject`, {
    reviewNote: `Rejected by local E2E ${runId}`,
  });

  await page.goto('/deposits');
  await expect(page.getByText(depositRefs[0])).toBeVisible();
  await expect(page.getByText(depositRefs[1])).toBeVisible();

  await writeManifest({
    deposits: [
      { id: first!.id, telebirrReference: depositRefs[0], amount: deposits[0]?.amount ?? '0', status: 'APPROVED' },
      { id: second!.id, telebirrReference: depositRefs[1], amount: deposits[1]?.amount ?? '0', status: 'REJECTED' },
    ],
  });
});

test('post winning draws', async ({ page, request }) => {
  if (!phaseEnabled('draw-session')) {
    return;
  }

  const manifest: any = (await readManifest()) ?? {};
  const sessionId = manifest.session?.id;
  const winningDraws = manifest.winningDraws ?? [];
  expect(sessionId).toBeTruthy();
  expect(winningDraws.length).toBe(5);

  await loginUi(page);
  const token = await adminToken(request);

  const session = (await apiJson(request, token, 'GET', `/api/v1/sessions/${sessionId}`)) as { state: string };
  if (session.state === 'OPEN') {
    await apiJson(request, token, 'PUT', `/api/v1/admin/sessions/${sessionId}/state`, { state: 'STARTED' });
    await apiJson(request, token, 'PUT', `/api/v1/admin/sessions/${sessionId}/state`, { state: 'DRAWING' });
  } else if (session.state === 'STARTED') {
    await apiJson(request, token, 'PUT', `/api/v1/admin/sessions/${sessionId}/state`, { state: 'DRAWING' });
  }

  const existingDraws = (await apiJson(request, token, 'GET', `/api/v1/sessions/${sessionId}/draws`)) as Array<{
    numberDrawn: number;
  }>;
  const drawnNumbers = new Set(existingDraws.map((draw) => draw.numberDrawn));

  for (const numberDrawn of winningDraws) {
    if (!drawnNumbers.has(numberDrawn)) {
      await apiJson(request, token, 'POST', `/api/v1/admin/sessions/${sessionId}/draws`, { numberDrawn });
    }
  }

  await page.goto(`/sessions/${sessionId}`);
  await expect(page.getByText('Draw count')).toBeVisible();
  for (let index = 0; index < winningDraws.length; index += 1) {
    await expect(page.getByText(`#${index + 1} · ${winningDraws[index]}`)).toBeVisible();
  }

  await writeManifest({
    session: { id: sessionId, name: manifest.session?.name ?? `${runId} Session` },
    notes: [...(manifest.notes ?? []), 'winning draws posted'],
  });
});

test('review claim and settle prize', async ({ page, request }) => {
  if (!phaseEnabled('review-claim')) {
    return;
  }

  const manifest: any = (await readManifest()) ?? {};
  const sessionId = manifest.session?.id;
  expect(sessionId).toBeTruthy();

  await loginUi(page);
  const token = await adminToken(request);
  const claims = (await apiJson(request, token, 'GET', '/api/v1/admin/win-claims?size=50')) as {
    content: Array<{ id: number; sessionId: number; status: string; ticketId: number; entitlementId: number | null }>;
  };
  const claim = claims.content.find((item) => item.sessionId === sessionId && item.status === 'PENDING_REVIEW');
  expect(claim).toBeTruthy();

  await apiJson(request, token, 'POST', `/api/v1/admin/win-claims/${claim!.id}/approve`, {
    reviewNote: `Approved by local E2E ${runId}`,
  });

  const entitlements = (await apiJson(request, token, 'GET', '/api/v1/admin/prize-entitlements?size=50')) as {
    content: Array<{ id: number; winClaimId: number; status: string; prizeAmount: string }>;
  };
  const entitlement = entitlements.content.find((item) => item.winClaimId === claim!.id);
  expect(entitlement).toBeTruthy();

  await apiJson(request, token, 'POST', `/api/v1/admin/prize-entitlements/${entitlement!.id}/settle`, {
    settlementReference: `SETTLEMENT-${runId}`,
    settlementNote: `Settled by local E2E ${runId}`,
  });

  await page.goto('/claims');
  await expect(page.getByTestId(`claim-card-${claim!.id}`)).toBeVisible();
  await page.goto('/prize-entitlements');
  await expect(page.getByTestId(`entitlement-card-${entitlement!.id}`)).toBeVisible();

  await writeManifest({
    claim: { id: claim!.id, ticketId: claim!.ticketId, status: 'APPROVED' },
    settlement: { reference: `SETTLEMENT-${runId}` },
    notes: [...(manifest.notes ?? []), 'claim approved and prize settled'],
  });
});
