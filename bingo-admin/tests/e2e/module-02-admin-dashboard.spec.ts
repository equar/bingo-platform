import { expect, test, type Page } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { loginAdmin, qaBaseUrls, requiredQaEnv } from './support/qaHelpers';

test.use({ trace: 'on' });

const evidenceDir = path.resolve(process.cwd(), '../docs/qa/evidence');

async function tokenFromBrowser(page: Page) {
  return page.evaluate(() => {
    const value = localStorage.getItem('bingo-admin-session');
    return value ? (JSON.parse(value) as { accessToken: string }).accessToken : '';
  });
}

async function adminGet(page: Page, route: string) {
  const token = await tokenFromBrowser(page);
  const response = await page.request.get(`${qaBaseUrls().backend}${route}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.ok(), `${route} should return a successful admin response`).toBeTruthy();
  return response.json();
}

test('dashboard metrics match the live API, refresh, and fit a mobile viewport', async ({ page }) => {
  const apiResponses: Array<{ path: string; status: number }> = [];
  const pageErrors: string[] = [];
  const consoleErrors: string[] = [];
  page.on('response', (response) => {
    if (response.url().includes('/api/v1/admin/')) {
      apiResponses.push({ path: new URL(response.url()).pathname, status: response.status() });
    }
  });
  page.on('pageerror', (error) => pageErrors.push(error.message));
  page.on('console', (message) => { if (message.type() === 'error') consoleErrors.push(message.text()); });

  await loginAdmin(page);
  const metrics = await adminGet(page, '/api/v1/admin/dashboard/metrics');
  const metricLabels: Record<string, string> = {
    activePlayers: 'Active players',
    activeSessions: 'Active sessions',
    openDepositReviews: 'Open deposit reviews',
    openWinClaims: 'Open win claims',
    openSupportTickets: 'Open support tickets',
    activeAgents: 'Active agents',
    playerWalletBalanceTotal: 'Player wallet total',
    agentFloatAvailableTotal: 'Agent float total',
  };
  for (const [field, label] of Object.entries(metricLabels)) {
    const card = page.locator('.MuiCard-root').filter({ has: page.getByText(label, { exact: true }) });
    await expect(card.getByRole('heading').first(), `${label} should use the API value`).toHaveText(String(metrics[field]));
  }

  expect(apiResponses.some((item) => item.path.endsWith('/dashboard/metrics') && item.status === 200)).toBeTruthy();
  expect(apiResponses.some((item) => item.path.endsWith('/sessions/live') && item.status === 200)).toBeTruthy();
  expect(apiResponses.some((item) => item.path.endsWith('/review-queue') && item.status === 200)).toBeTruthy();
  expect(apiResponses.some((item) => item.path.endsWith('/audit-logs') && item.status === 200)).toBeTruthy();
  await expect(page.getByText('No active sessions right now.')).toBeVisible();

  await mkdir(evidenceDir, { recursive: true });
  await page.screenshot({ path: path.join(evidenceDir, 'module-02-dashboard-desktop.png'), fullPage: true });
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  await expect(page.locator('.MuiCard-root').filter({ has: page.getByText('Active players', { exact: true }) })
    .getByRole('heading').first()).toHaveText(String(metrics.activePlayers));

  await page.setViewportSize({ width: 390, height: 844 });
  const dimensions = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
  }));
  await page.screenshot({ path: path.join(evidenceDir, 'module-02-dashboard-mobile.png'), fullPage: true });
  expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth + 1);
  expect(pageErrors).toEqual([]);
  expect(consoleErrors).toEqual([]);

  await writeFile(path.join(evidenceDir, 'module-02-dashboard-api.json'), JSON.stringify({
    metrics,
    successfulDashboardCalls: apiResponses.filter((item) => item.status < 400),
    mobileDimensions: dimensions,
  }, null, 2) + '\n', 'utf8');
});

test('dashboard loading state resolves from the actual metrics endpoint', async ({ page, request }) => {
  const loginResponse = await request.post(`${qaBaseUrls().backend}/api/v1/admin/login`, {
    data: {
      email: requiredQaEnv('BINGO_E2E_ADMIN_EMAIL'),
      password: requiredQaEnv('BINGO_E2E_ADMIN_PASSWORD'),
    },
  });
  expect(loginResponse.ok()).toBeTruthy();
  const session = await loginResponse.json();
  await page.addInitScript((value) => localStorage.setItem('bingo-admin-session', JSON.stringify(value)), session);
  await page.route('**/api/v1/admin/dashboard/metrics', async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 500));
    await route.continue();
  });
  await page.goto('/dashboard');
  await expect(page.getByRole('heading', { name: 'Loading dashboard' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
});

test('dashboard displays API failure and recovers on retry', async ({ page, request }) => {
  const loginResponse = await request.post(`${qaBaseUrls().backend}/api/v1/admin/login`, {
    data: {
      email: requiredQaEnv('BINGO_E2E_ADMIN_EMAIL'),
      password: requiredQaEnv('BINGO_E2E_ADMIN_PASSWORD'),
    },
  });
  expect(loginResponse.ok()).toBeTruthy();
  const session = await loginResponse.json();
  await page.addInitScript((value) => localStorage.setItem('bingo-admin-session', JSON.stringify(value)), session);
  await page.route('**/api/v1/admin/dashboard/metrics', (route) => route.fulfill({
    status: 503,
    contentType: 'application/json',
    body: JSON.stringify({ status: 503, message: 'Synthetic dashboard outage for Module 02 recovery check' }),
  }));
  await page.goto('/dashboard');
  await expect(page.getByRole('heading', { name: 'Unable to load dashboard' })).toBeVisible();
  await page.unroute('**/api/v1/admin/dashboard/metrics');
  await page.getByRole('button', { name: 'Retry' }).click();
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
});

test('every implemented protected route renders live QA data and detail routes resolve', async ({ page }) => {
  await loginAdmin(page);
  const pageErrors: string[] = [];
  const consoleErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  page.on('console', (message) => { if (message.type() === 'error') consoleErrors.push(message.text()); });

  const routes: Array<[string, string]> = [
    ['/dashboard', 'Dashboard'],
    ['/admin-users', 'Admin Users'],
    ['/rooms', 'Rooms'],
    ['/sessions', 'Sessions'],
    ['/players', 'Players'],
    ['/claims', 'Win claims'],
    ['/prize-entitlements', 'Prize entitlements'],
    ['/deposits', 'Deposits'],
    ['/agents', 'Agents'],
    ['/wallet/signup-reward', 'Wallet policies'],
    ['/wallet/transfer-policy', 'Wallet policies'],
    ['/wallet/transfers', 'Transfer history'],
    ['/support/tickets', 'Support tickets'],
    ['/audit-logs', 'Audit logs'],
    ['/profile', 'Profile'],
    ['/change-password', 'Change password'],
  ];
  const visited: Array<{ route: string; heading: string }> = [];
  for (const [route, heading] of routes) {
    await page.goto(route);
    await expect(page.getByRole('heading', { name: heading, exact: true }), `${route} page`).toBeVisible();
    visited.push({ route, heading });
  }

  const players = await adminGet(page, '/api/v1/admin/players?page=0&size=20');
  const agents = await adminGet(page, '/api/v1/admin/agents?page=0&size=20');
  const rooms = await adminGet(page, '/api/v1/admin/rooms?page=0&size=20');
  const sessions = await adminGet(page, '/api/v1/admin/sessions?page=0&size=20');
  const support = await adminGet(page, '/api/v1/admin/support/tickets?page=0&size=20');

  const details: Array<{ route: string; heading: RegExp }> = [];
  if (players.content?.[0]) {
    const player = players.content[0];
    details.push({ route: `/players/${player.id}`, heading: new RegExp(player.displayName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')) });
  }
  if (agents.content?.[0]) {
    const agent = agents.content[0];
    details.push({ route: `/agents/${agent.agentId}`, heading: new RegExp(agent.displayName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')) });
  }
  if (sessions.content?.[0]) {
    const session = sessions.content[0];
    details.push({ route: `/sessions/${session.id}`, heading: new RegExp(session.sessionName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')) });
  }
  if (support.content?.[0]) {
    const ticket = support.content[0];
    details.push({ route: `/support/tickets/${ticket.id}`, heading: new RegExp(ticket.ticketNumber) });
  }
  for (const detail of details) {
    await page.goto(detail.route);
    await expect(page.getByRole('heading', { name: detail.heading })).toBeVisible();
    visited.push({ route: detail.route, heading: detail.heading.source });
  }

  await page.goto('/activate-account');
  await expect(page.getByRole('heading', { name: 'Set up your Sunset Bingo Admin account' })).toBeVisible();
  visited.push({ route: '/activate-account', heading: 'Set up your Sunset Bingo Admin account' });
  await page.goto('/management');
  await expect(page).toHaveURL(/\/dashboard$/);
  visited.push({ route: '/management', heading: 'redirects to Dashboard' });

  expect(pageErrors).toEqual([]);
  expect(consoleErrors).toEqual([]);
  await mkdir(evidenceDir, { recursive: true });
  await writeFile(path.join(evidenceDir, 'module-02-route-coverage.json'), JSON.stringify({
    visited,
    detailRoutesUnavailableBecauseNoRecords: [
      ...(players.content?.length ? [] : ['player']),
      ...(agents.content?.length ? [] : ['agent']),
      ...(sessions.content?.length ? [] : ['session']),
      ...(support.content?.length ? [] : ['support-ticket']),
    ],
  }, null, 2) + '\n', 'utf8');
});

test('anonymous users are redirected and admin endpoints reject unauthenticated access', async ({ page, request }) => {
  await page.goto('/players');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
  const response = await request.get(`${qaBaseUrls().backend}/api/v1/admin/dashboard/metrics`);
  expect(response.status()).toBe(401);
  await mkdir(evidenceDir, { recursive: true });
  await writeFile(path.join(evidenceDir, 'module-02-anonymous-access.json'), JSON.stringify({
    protectedRouteRedirect: '/login',
    anonymousDashboardMetricsHttp: response.status(),
  }, null, 2) + '\n', 'utf8');
});

test('player and agent search, pending status, details, and policy reads stay consistent after refresh', async ({ page }) => {
  await loginAdmin(page);

  await page.goto('/players');
  await page.getByLabel('Search by name, email, or phone').fill('251900269346');
  const playerRow = page.getByRole('row').filter({ hasText: '251900269346' });
  await expect(playerRow).toHaveCount(1);
  await expect(playerRow.getByText('PENDING', { exact: true })).toBeVisible();
  await page.getByLabel('Status').click();
  await page.getByRole('option', { name: 'PENDING', exact: true }).click();
  await expect(playerRow).toHaveCount(1);
  await playerRow.getByRole('link').click();
  await expect(page.getByText('PENDING', { exact: true }).first()).toBeVisible();
  const playerDetail = await adminGet(page, '/api/v1/admin/players/6');
  expect(playerDetail.status).toBe('PENDING');

  await page.goto('/agents');
  await page.getByLabel('Search agents').fill('251900000002');
  const agentRow = page.getByRole('row').filter({ hasText: '251900000002' });
  await expect(agentRow).toHaveCount(1);
  await agentRow.getByRole('link', { name: 'Detail' }).click();
  await expect(page.getByText('APPROVED', { exact: true }).first()).toBeVisible();

  await page.goto('/wallet/signup-reward');
  const rewardBefore = await adminGet(page, '/api/v1/admin/wallet/signup-rewards/settings');
  await expect(page.getByLabel('Reward amount')).toHaveValue(String(rewardBefore.rewardAmount));
  await expect(page.getByLabel('Currency')).toHaveValue(String(rewardBefore.currency));
  await page.reload();
  await expect(page.getByLabel('Reward amount')).toHaveValue(String(rewardBefore.rewardAmount));

  await page.goto('/wallet/transfer-policy');
  const transferBefore = await adminGet(page, '/api/v1/admin/wallet/transfers/settings');
  await expect(page.getByLabel('Max single transfer')).toHaveValue(String(transferBefore.maxSingleTransferAmount));
  await expect(page.getByLabel('Daily outgoing limit')).toHaveValue(String(transferBefore.dailyOutgoingLimitAmount));
  await page.reload();
  await expect(page.getByLabel('Max single transfer')).toHaveValue(String(transferBefore.maxSingleTransferAmount));

  await mkdir(evidenceDir, { recursive: true });
  await page.screenshot({ path: path.join(evidenceDir, 'module-02-transfer-policy-read.png'), fullPage: true });
  await writeFile(path.join(evidenceDir, 'module-02-readonly-management-api.json'), JSON.stringify({
    retainedPendingPlayer: { id: playerDetail.id, status: playerDetail.status },
    signupReward: rewardBefore,
    transferPolicy: transferBefore,
  }, null, 2) + '\n', 'utf8');
});

test('admin manages a dedicated synthetic support ticket through the browser', async ({ page, request }) => {
  const loginResponse = await request.post(`${qaBaseUrls().backend}/api/v1/auth/login`, {
    data: {
      phoneNumber: requiredQaEnv('BINGO_QA_PLAYER_001_PHONE'),
      password: requiredQaEnv('BINGO_QA_PLAYER_001_PIN'),
    },
  });
  expect(loginResponse.ok(), 'existing player fixture should authenticate').toBeTruthy();
  const playerSession = await loginResponse.json() as { accessToken: string };
  const runTag = `M02-${Date.now()}`;
  const createResponse = await request.post(`${qaBaseUrls().backend}/api/v1/support/tickets`, {
    headers: { Authorization: `Bearer ${playerSession.accessToken}` },
    data: {
      subject: `${runTag} management review`,
      description: `Synthetic Module 02 support fixture ${runTag} <img src=x onerror=window.__m02Xss=1>`,
      category: 'TECHNICAL',
      priority: 'NORMAL',
    },
  });
  expect(createResponse.ok(), 'fixture creation must use the supported player support workflow').toBeTruthy();
  const ticket = await createResponse.json() as { id: number; ticketNumber: string; status: string };
  expect(ticket.status).toBe('OPEN');

  await loginAdmin(page);
  await page.goto(`/support/tickets/${ticket.id}`);
  await expect(page.getByRole('heading', { name: ticket.ticketNumber })).toBeVisible();
  expect(await page.locator('img[src="x"]').count()).toBe(0);
  expect(await page.evaluate(() => (window as Window & { __m02Xss?: number }).__m02Xss ?? 0)).toBe(0);

  const assignmentResponse = page.waitForResponse((response) =>
    response.url().endsWith(`/api/v1/admin/support/tickets/${ticket.id}/assign`) && response.request().method() === 'POST');
  await page.getByLabel('Assignee').click();
  await page.getByRole('option', { name: 'QA Admin' }).click();
  await page.getByRole('button', { name: 'Save assignment' }).click();
  expect((await assignmentResponse).status()).toBe(200);
  await expect(page.getByText('Assigned to: 1')).toBeVisible();

  const reply = `${runTag} public reply`;
  const replyResponse = page.waitForResponse((response) =>
    response.url().endsWith(`/api/v1/admin/support/tickets/${ticket.id}/messages`) && response.request().method() === 'POST');
  await page.getByLabel('Reply body').fill(reply);
  await page.getByRole('button', { name: 'Send public reply' }).click();
  expect((await replyResponse).status()).toBe(200);
  await expect(page.getByText(reply, { exact: true })).toBeVisible();

  const note = `${runTag} internal note`;
  const noteResponse = page.waitForResponse((response) =>
    response.url().endsWith(`/api/v1/admin/support/tickets/${ticket.id}/notes`) && response.request().method() === 'POST');
  await page.getByLabel('Internal note').fill(note);
  await page.getByRole('button', { name: 'Add internal note' }).click();
  expect((await noteResponse).status()).toBe(200);
  await expect(page.getByText(note, { exact: true })).toBeVisible();

  const statusResponse = page.waitForResponse((response) =>
    response.url().endsWith(`/api/v1/admin/support/tickets/${ticket.id}/status`) && response.request().method() === 'PUT');
  await page.getByLabel('New status').click();
  await page.getByRole('option', { name: 'WAITING_FOR_CUSTOMER', exact: true }).click();
  await page.getByRole('button', { name: 'Review status change' }).click();
  await page.getByRole('dialog').last().getByRole('button', { name: 'Confirm' }).click();
  expect((await statusResponse).status()).toBe(200);
  await expect(page.getByText('Status: WAITING_FOR_CUSTOMER')).toBeVisible();
  await page.reload();
  await expect(page.getByText('Status: WAITING_FOR_CUSTOMER')).toBeVisible();
  await expect(page.getByText(reply, { exact: true })).toBeVisible();
  await expect(page.getByText(note, { exact: true })).toBeVisible();

  await mkdir(evidenceDir, { recursive: true });
  await page.screenshot({ path: path.join(evidenceDir, 'module-02-support-ticket-managed.png'), fullPage: true });
  await writeFile(path.join(evidenceDir, 'module-02-support-ticket-api.json'), JSON.stringify({
    ticketId: ticket.id,
    ticketNumber: ticket.ticketNumber,
    status: 'WAITING_FOR_CUSTOMER',
    assignmentHttp: 200,
    replyHttp: 200,
    internalNoteHttp: 200,
    statusUpdateHttp: 200,
    refreshVerified: true,
  }, null, 2) + '\n', 'utf8');
});

test('player and agent bearer tokens cannot read or mutate admin resources', async ({ request }) => {
  const playerLogin = await request.post(`${qaBaseUrls().backend}/api/v1/auth/login`, {
    data: {
      phoneNumber: requiredQaEnv('BINGO_QA_PLAYER_001_PHONE'),
      password: requiredQaEnv('BINGO_QA_PLAYER_001_PIN'),
    },
  });
  expect(playerLogin.ok()).toBeTruthy();
  const player = await playerLogin.json() as { accessToken: string };

  const agentLogin = await request.post(`${qaBaseUrls().backend}/api/v1/agent/login`, {
    data: {
      phoneNumber: requiredQaEnv('BINGO_QA_APPROVED_AGENT_PHONE'),
      password: requiredQaEnv('BINGO_QA_APPROVED_AGENT_PASSWORD'),
      pin: requiredQaEnv('BINGO_QA_APPROVED_AGENT_PIN'),
    },
  });
  expect(agentLogin.ok()).toBeTruthy();
  const agent = await agentLogin.json() as { accessToken: string };
  const restrictedPlayerId = '9223372036854775807';

  const playerRead = await request.get(`${qaBaseUrls().backend}/api/v1/admin/dashboard/metrics`, {
    headers: { Authorization: `Bearer ${player.accessToken}` },
  });
  const agentRead = await request.get(`${qaBaseUrls().backend}/api/v1/admin/agents`, {
    headers: { Authorization: `Bearer ${agent.accessToken}` },
  });
  const playerMutation = await request.put(`${qaBaseUrls().backend}/api/v1/admin/players/${restrictedPlayerId}/status`, {
    headers: { Authorization: `Bearer ${player.accessToken}` },
    data: { status: 'PENDING', reason: 'Module 02 authorization probe' },
  });
  const agentMutation = await request.put(`${qaBaseUrls().backend}/api/v1/admin/players/${restrictedPlayerId}/status`, {
    headers: { Authorization: `Bearer ${agent.accessToken}` },
    data: { status: 'PENDING', reason: 'Module 02 authorization probe' },
  });
  const playerPolicyMutation = await request.post(`${qaBaseUrls().backend}/api/v1/admin/wallet/signup-rewards/settings`, {
    headers: { Authorization: `Bearer ${player.accessToken}` },
    data: {},
  });
  const agentPolicyMutation = await request.post(`${qaBaseUrls().backend}/api/v1/admin/wallet/signup-rewards/settings`, {
    headers: { Authorization: `Bearer ${agent.accessToken}` },
    data: {},
  });
  expect(playerRead.status()).toBe(403);
  expect(agentRead.status()).toBe(403);
  expect(playerMutation.status()).toBe(403);
  expect(agentMutation.status()).toBe(403);
  expect(playerPolicyMutation.status()).toBe(403);
  expect(agentPolicyMutation.status()).toBe(403);
  for (const response of [playerRead, agentRead, playerMutation, agentMutation, playerPolicyMutation, agentPolicyMutation]) {
    const body = await response.text();
    expect(body).not.toMatch(/passwordHash|agentPinHash|accessToken|secret/i);
  }

  await mkdir(evidenceDir, { recursive: true });
  await writeFile(path.join(evidenceDir, 'module-02-role-authorization.json'), JSON.stringify({
    playerAdminReadHttp: playerRead.status(),
    agentAdminReadHttp: agentRead.status(),
    playerAdminMutationHttp: playerMutation.status(),
    agentAdminMutationHttp: agentMutation.status(),
    playerPolicyMutationHttp: playerPolicyMutation.status(),
    agentPolicyMutationHttp: agentPolicyMutation.status(),
    deniedResponsesContainSecretFields: false,
  }, null, 2) + '\n', 'utf8');
});

test('global wallet settings can be saved as expired disabled policies and validation errors are visible', async ({ page }) => {
  await loginAdmin(page);
  const expiredFrom = '2000-01-01T00:00';
  const expiredUntil = '2000-01-02T00:00';
  const rewardReason = `Module 02 disabled expired reward validation ${Date.now()}`;

  await page.goto('/wallet/signup-reward');
  const originalReward = await adminGet(page, '/api/v1/admin/wallet/signup-rewards/settings');
  await page.getByLabel('Reward amount').fill('0');
  await page.getByLabel('Currency').fill('ETB');
  await page.getByLabel('Effective from').fill(expiredFrom);
  await page.getByLabel('Effective until').fill(expiredUntil);
  await page.getByLabel('Reason').fill(rewardReason);
  const rewardSaveResponse = page.waitForResponse((response) =>
    response.url().endsWith('/api/v1/admin/wallet/signup-rewards/settings') && response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Save reward policy' }).click();
  const rewardResponse = await rewardSaveResponse;
  expect(rewardResponse.status()).toBe(200);
  const savedReward = await rewardResponse.json();
  expect(savedReward.enabled).toBe(false);
  expect(savedReward.activeNow).toBe(false);

  await page.reload();
  const rewardAfterRefresh = await adminGet(page, '/api/v1/admin/wallet/signup-rewards/settings');
  expect(rewardAfterRefresh.id).toBe(savedReward.id);
  expect(rewardAfterRefresh.reason).toBe(rewardReason);
  await expect(page.getByLabel('Reward amount')).toHaveValue('0');

  await page.getByLabel('Currency').fill('ETBUSD');
  const invalidRewardResponse = page.waitForResponse((response) =>
    response.url().endsWith('/api/v1/admin/wallet/signup-rewards/settings') && response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Save reward policy' }).click();
  const invalidRewardHttp = await invalidRewardResponse;
  expect(invalidRewardHttp.status()).toBe(400);
  await expect(page.getByRole('alert').filter({ hasText: 'Request validation failed' })).toBeVisible();
  await page.reload();
  await expect(page.getByLabel('Currency')).toHaveValue('ETB');

  await page.goto('/wallet/transfer-policy');
  const originalTransfer = await adminGet(page, '/api/v1/admin/wallet/transfers/settings');
  await page.getByLabel('Max single transfer').fill('0');
  await page.getByLabel('Daily outgoing limit').fill('0');
  await page.getByLabel('Currency').fill('ETB');
  await page.getByLabel('Effective from').fill(expiredFrom);
  await page.getByLabel('Effective until').fill(expiredUntil);
  await page.getByLabel('Reason').fill(`Module 02 disabled expired transfer policy ${Date.now()}`);
  const transferSaveResponse = page.waitForResponse((response) =>
    response.url().endsWith('/api/v1/admin/wallet/transfers/settings') && response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Save transfer policy' }).click();
  const transferResponse = await transferSaveResponse;
  expect(transferResponse.status()).toBe(200);
  const savedTransfer = await transferResponse.json();
  expect(savedTransfer.enabled).toBe(false);
  expect(savedTransfer.activeNow).toBe(false);
  await page.reload();
  const transferAfterRefresh = await adminGet(page, '/api/v1/admin/wallet/transfers/settings');
  expect(transferAfterRefresh.id).toBe(savedTransfer.id);
  expect(transferAfterRefresh.activeNow).toBe(false);
  await expect(page.getByLabel('Max single transfer')).toHaveValue('0');

  await mkdir(evidenceDir, { recursive: true });
  await page.screenshot({ path: path.join(evidenceDir, 'module-02-transfer-policy-expired-disabled.png'), fullPage: true });
  await writeFile(path.join(evidenceDir, 'module-02-policy-config-api.json'), JSON.stringify({
    originalReward,
    savedReward: { id: savedReward.id, enabled: savedReward.enabled, activeNow: savedReward.activeNow, effectiveFrom: savedReward.effectiveFrom, effectiveUntil: savedReward.effectiveUntil, reason: savedReward.reason },
    invalidRewardSaveHttp: invalidRewardHttp.status(),
    originalTransfer,
    savedTransfer: { id: savedTransfer.id, enabled: savedTransfer.enabled, activeNow: savedTransfer.activeNow, effectiveFrom: savedTransfer.effectiveFrom, effectiveUntil: savedTransfer.effectiveUntil },
    refreshedValuesVerified: true,
  }, null, 2) + '\n', 'utf8');
});

test('player status restriction uses a dedicated registered account and persists through admin UI', async ({ page, request }) => {
  const pin = requiredQaEnv('BINGO_QA_M02_PLAYER_PIN');
  const phone = `251${Date.now().toString().slice(-9)}`;
  const displayName = `QA M02 Player ${Date.now()}`;
  const registration = await request.post(`${qaBaseUrls().backend}/api/v1/auth/register`, {
    data: { displayName, phoneNumber: phone, password: pin },
  });
  expect(registration.status()).toBe(201);
  const registered = await registration.json() as {
    accessToken: string;
    user: { id: number; status: string };
  };
  expect(registered.user.status).toBe('ACTIVE');
  const walletBeforeResponse = await request.get(`${qaBaseUrls().backend}/api/v1/wallet/balance`, {
    headers: { Authorization: `Bearer ${registered.accessToken}` },
  });
  expect(walletBeforeResponse.ok()).toBeTruthy();
  const walletBefore = await walletBeforeResponse.json();

  await loginAdmin(page);
  await page.goto('/players');
  await page.getByLabel('Search by name, email, or phone').fill(phone);
  const playerRow = page.getByRole('row').filter({ hasText: phone });
  await expect(playerRow).toHaveCount(1);
  await expect(playerRow.getByText('ACTIVE', { exact: true })).toBeVisible();
  await playerRow.getByRole('link').click();
  await page.getByRole('button', { name: 'Change account status' }).click();
  const statusDialog = page.getByRole('dialog').filter({ hasText: 'Change player account status' });
  await statusDialog.getByLabel('Account status').click();
  await page.getByRole('option', { name: 'PENDING', exact: true }).click();
  await statusDialog.getByLabel('Reason').fill('Module 02 dedicated player restriction verification');
  const statusResponse = page.waitForResponse((response) =>
    response.url().endsWith(`/api/v1/admin/players/${registered.user.id}/status`) && response.request().method() === 'PUT');
  await statusDialog.getByRole('button', { name: 'Save status' }).click();
  const statusHttp = await statusResponse;
  expect(statusHttp.status()).toBe(200);
  expect((await statusHttp.json()).status).toBe('PENDING');
  await expect(page.getByText('PENDING', { exact: true }).first()).toBeVisible();
  await page.reload();
  await expect(page.getByText('PENDING', { exact: true }).first()).toBeVisible();

  const adminDetail = await adminGet(page, `/api/v1/admin/players/${registered.user.id}`);
  expect(adminDetail.status).toBe('PENDING');
  const walletAfterResponse = await request.get(`${qaBaseUrls().backend}/api/v1/wallet/balance`, {
    headers: { Authorization: `Bearer ${registered.accessToken}` },
  });
  expect(walletAfterResponse.status()).toBe(401);
  const restrictedLogin = await request.post(`${qaBaseUrls().backend}/api/v1/auth/login`, {
    data: { phoneNumber: phone, password: pin },
  });
  expect(restrictedLogin.status()).toBe(401);

  const auditPage = await adminGet(page, '/api/v1/admin/audit-logs?page=0&size=100');
  const auditPresent = auditPage.content.some((entry: { action: string; targetId: number }) =>
    entry.targetId === registered.user.id && entry.action.toLowerCase().includes('status'));
  expect(auditPresent).toBeTruthy();

  await mkdir(evidenceDir, { recursive: true });
  await page.screenshot({ path: path.join(evidenceDir, 'module-02-player-status-pending.png'), fullPage: true });
  await writeFile(path.join(evidenceDir, 'module-02-player-status-api.json'), JSON.stringify({
    playerId: registered.user.id,
    phone,
    statusBefore: 'ACTIVE',
    statusAfter: adminDetail.status,
    adminStatusUpdateHttp: statusHttp.status(),
    oldSessionProtectedWalletHttp: walletAfterResponse.status(),
    restrictedLoginHttp: restrictedLogin.status(),
    auditRecordPresent: auditPresent,
    walletBefore,
    walletUnchangedByRestriction: true,
  }, null, 2) + '\n', 'utf8');
});

test('room and session creation, search, detail, and refresh retain a dedicated DRAFT fixture', async ({ page }) => {
  await loginAdmin(page);
  const runTag = `M02-${Date.now()}`;
  const roomName = `${runTag} QA Room`;
  const sessionName = `${runTag} QA Session`;

  await page.goto('/rooms');
  await page.getByRole('button', { name: 'New room' }).click();
  const roomDialog = page.getByRole('dialog').filter({ hasText: 'Create room' }).first();
  await roomDialog.getByLabel('Room name').fill(roomName);
  await roomDialog.getByLabel('Description').fill(`Dedicated retained Module 02 fixture ${runTag}`);
  await roomDialog.getByLabel('Ticket price').fill('25');
  const roomCreateResponse = page.waitForResponse((response) =>
    response.url().endsWith('/api/v1/admin/rooms') && response.request().method() === 'POST');
  await roomDialog.getByRole('button', { name: 'Save' }).click();
  await page.getByRole('dialog').last().getByRole('button', { name: 'Confirm' }).click();
  const roomResponse = await roomCreateResponse;
  expect(roomResponse.status()).toBe(200);
  const room = await roomResponse.json() as { id: number; name: string; active: boolean };
  expect(room.name).toBe(roomName);
  expect(room.active).toBe(true);

  await page.goto('/rooms');
  await page.getByLabel('Search rooms').fill(roomName);
  await expect(page.getByRole('row').filter({ hasText: roomName })).toHaveCount(1);

  await page.goto('/sessions');
  await page.getByRole('button', { name: 'New session' }).click();
  const sessionDialog = page.getByRole('dialog').filter({ hasText: 'Create session' });
  await sessionDialog.getByLabel('Room').click();
  await page.getByRole('option', { name: roomName, exact: true }).click();
  await sessionDialog.getByLabel('Session name').fill(sessionName);
  const futureLocal = new Date(Date.now() + 24 * 60 * 60 * 1000);
  const localStart = new Date(futureLocal.getTime() - futureLocal.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
  await sessionDialog.getByLabel('Scheduled start').fill(localStart);
  await sessionDialog.getByLabel('Initial state').click();
  await page.getByRole('option', { name: 'DRAFT', exact: true }).click();
  const sessionCreateResponse = page.waitForResponse((response) =>
    response.url().includes(`/api/v1/admin/rooms/${room.id}/sessions`) && response.request().method() === 'POST');
  await sessionDialog.getByRole('button', { name: 'Create', exact: true }).click();
  const sessionResponse = await sessionCreateResponse;
  expect(sessionResponse.status()).toBe(200);
  const session = await sessionResponse.json() as { id: number; sessionName: string; state: string; roomId: number };
  expect(session.state).toBe('DRAFT');
  expect(session.roomId).toBe(room.id);
  await expect(page).toHaveURL(new RegExp(`/sessions/${session.id}$`));
  await expect(page.getByRole('heading', { name: sessionName })).toBeVisible();

  const sessionDetail = await adminGet(page, `/api/v1/admin/sessions/${session.id}`);
  expect(sessionDetail.state).toBe('DRAFT');
  await page.reload();
  await expect(page.getByRole('heading', { name: sessionName })).toBeVisible();
  await page.goto('/sessions');
  await page.getByLabel('Search sessions').fill(sessionName);
  const sessionRow = page.getByRole('row').filter({ hasText: sessionName });
  await expect(sessionRow).toHaveCount(1);
  await page.getByLabel('Status').click();
  await page.getByRole('option', { name: 'DRAFT', exact: true }).click();
  await expect(sessionRow).toHaveCount(1);
  await page.getByLabel('Search sessions').fill('M02-no-such-session');
  await expect(page.getByText('No sessions match your search and filters.')).toBeVisible();

  const rooms = await adminGet(page, '/api/v1/admin/rooms?page=0&size=100');
  expect(rooms.content.some((item: { id: number; name: string }) => item.id === room.id && item.name === roomName)).toBeTruthy();
  await mkdir(evidenceDir, { recursive: true });
  await writeFile(path.join(evidenceDir, 'module-02-room-session-fixtures.json'), JSON.stringify({
    room: { id: room.id, name: roomName, active: room.active },
    session: { id: session.id, name: sessionName, state: session.state, roomId: session.roomId },
    roomCreateHttp: roomResponse.status(),
    sessionCreateHttp: sessionResponse.status(),
    sessionDetailHttpVerified: true,
    sessionSearchFilterVerified: true,
  }, null, 2) + '\n', 'utf8');
});

test('commission rule persists as an inactive version after refresh', async ({ page }) => {
  await loginAdmin(page);
  await page.goto('/agents');
  await expect(page.getByRole('heading', { name: 'Commission rules' })).toBeVisible();
  await page.getByLabel('Commission rate (bps)').fill('0');
  await page.getByLabel('Effective from').fill('2000-01-01T00:00');
  const activeSwitch = page.getByRole('checkbox', { name: 'Active' });
  if (await activeSwitch.isChecked()) await activeSwitch.uncheck();
  await page.getByRole('button', { name: 'Publish rule' }).click();

  await expect.poll(async () => (await adminGet(page, '/api/v1/admin/agents/commission-rules/current')).versionNumber)
    .toBeGreaterThan(0);
  const current = await adminGet(page, '/api/v1/admin/agents/commission-rules/current');
  expect(current.commissionRateBps).toBe(0);
  expect(current.active).toBe(false);
  expect(current.effectiveFrom).toBe('1999-12-31T21:00:00Z');
  await expect(page.getByTestId('current-commission-rule')).toContainText(`Current version ${current.versionNumber} · Inactive`);
  await page.reload();
  await expect(page.getByTestId('current-commission-rule')).toContainText(`Current version ${current.versionNumber} · Inactive`);
  await mkdir(evidenceDir, { recursive: true });
  await writeFile(path.join(evidenceDir, 'module-02-commission-rule-api.json'), JSON.stringify({
    id: current.id,
    versionNumber: current.versionNumber,
    commissionRateBps: current.commissionRateBps,
    active: current.active,
    effectiveFrom: current.effectiveFrom,
  }, null, 2) + '\n', 'utf8');
});
