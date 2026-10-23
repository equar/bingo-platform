import { expect, test, type APIRequestContext } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { loginAdmin, qaBaseUrls, requiredQaEnv } from './support/qaHelpers';

const evidenceDir = path.resolve(process.cwd(), '../docs/qa/evidence');

async function signInAgent(request: APIRequestContext, phone: string, password: string, pin: string) {
  return request.post(`${qaBaseUrls().backend}/api/v1/agent/login`, {
    data: { phoneNumber: phone, password, pin },
  });
}

async function findAgent(page: import('@playwright/test').Page, phone: string) {
  const session = await page.evaluate(() => JSON.parse(localStorage.getItem('bingo-admin-session') ?? '{}') as { accessToken: string });
  const response = await page.request.get(`${qaBaseUrls().backend}/api/v1/admin/agents?page=0&size=100`, {
    headers: { Authorization: `Bearer ${session.accessToken}` },
  });
  expect(response.ok()).toBeTruthy();
  const agents = (await response.json()).content as Array<{ agentId: number; userId: number; normalizedPhone: string; status: string }>;
  const normalizedPhone = phone.startsWith('0') ? `251${phone.slice(1)}` : phone;
  const agent = agents.find((item) => item.normalizedPhone === normalizedPhone);
  expect(agent, `agent application ${normalizedPhone} should appear in the admin roster`).toBeTruthy();
  return { agent: agent!, token: session.accessToken as string };
}

async function saveEvidence(name: string, value: unknown) {
  await mkdir(evidenceDir, { recursive: true });
  await writeFile(path.join(evidenceDir, name), JSON.stringify(value, null, 2) + '\n', 'utf8');
}

test('agent roster displays retained lifecycle statuses', async ({ page }) => {
  await loginAdmin(page);
  await page.goto('/agents');
  await expect(page.getByRole('heading', { name: 'Agents', exact: true })).toBeVisible();
  const search = page.getByLabel('Search agents');
  await search.fill('251911000601');
  const approvedRow = page.getByRole('row').filter({ hasText: '251911000601' });
  await expect(approvedRow).toHaveCount(1);
  await expect(approvedRow.getByText('APPROVED', { exact: true })).toBeVisible();
  await search.fill('251911000602');
  const suspendedRow = page.getByRole('row').filter({ hasText: '251911000602' });
  await expect(suspendedRow).toHaveCount(1);
  await expect(suspendedRow.getByText('SUSPENDED', { exact: true })).toBeVisible();
  await mkdir(evidenceDir, { recursive: true });
  await page.screenshot({ path: path.join(evidenceDir, 'module-02a-agent-roster.png'), fullPage: true });
});

test('admin reviews and approves both retained agent applications', async ({ page, request }) => {
  const phoneOne = requiredQaEnv('BINGO_M02A_AGENT_ONE_PHONE');
  const phoneTwo = requiredQaEnv('BINGO_M02A_AGENT_TWO_PHONE');
  const passwordOne = requiredQaEnv('BINGO_M02A_AGENT_ONE_PASSWORD');
  const passwordTwo = requiredQaEnv('BINGO_M02A_AGENT_TWO_PASSWORD');
  const pinOne = requiredQaEnv('BINGO_M02A_AGENT_ONE_PIN');
  const pinTwo = requiredQaEnv('BINGO_M02A_AGENT_TWO_PIN');
  const backend = qaBaseUrls().backend;

  await loginAdmin(page);
  const { agent: applicantOne, token: adminToken } = await findAgent(page, phoneOne);
  const { agent: applicantTwo } = await findAgent(page, phoneTwo);
  expect(applicantOne.status).toBe('PENDING');
  expect(applicantTwo.status).toBe('PENDING');

  await page.goto(`/agents/${applicantOne.agentId}`);
  await expect(page.getByRole('heading', { name: 'QA M02A Agent One' })).toBeVisible();
  await expect(page.getByText('M02A-NATIONAL-ID')).toBeVisible();
  await expect(page.getByText('M02A-AGENT-001')).toBeVisible();
  await page.getByRole('button', { name: 'Approve application' }).click();
  await expect(page.getByRole('heading', { name: 'Approve agent application' })).toBeVisible();
  await page.getByRole('button', { name: 'Approve', exact: true }).click();
  await expect(page.getByText('APPROVED', { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByText('APPROVED', { exact: true })).toBeVisible();

  await page.goto(`/agents/${applicantTwo.agentId}`);
  await expect(page.getByRole('heading', { name: 'QA M02A Agent Two' })).toBeVisible();
  await page.getByRole('button', { name: 'Approve application' }).click();
  await page.getByRole('button', { name: 'Approve', exact: true }).click();
  await expect(page.getByText('APPROVED', { exact: true })).toBeVisible();

  const firstLogin = await signInAgent(request, phoneOne, passwordOne, pinOne);
  expect(firstLogin.status()).toBe(200);
  const firstSession = await firstLogin.json();
  const authorizedDashboard = await request.get(`${backend}/api/v1/agent/dashboard`, {
    headers: { Authorization: `Bearer ${firstSession.accessToken}` },
  });
  expect(authorizedDashboard.status()).toBe(200);
  const secondLogin = await signInAgent(request, phoneTwo, passwordTwo, pinTwo);
  expect(secondLogin.status()).toBe(200);

  const playerLogin = await request.post(`${backend}/api/v1/auth/login`, {
    data: { phoneNumber: requiredQaEnv('BINGO_QA_PLAYER_001_PHONE'), password: requiredQaEnv('BINGO_QA_PLAYER_001_PIN') },
  });
  expect(playerLogin.status()).toBe(200);
  const playerToken = (await playerLogin.json()).accessToken as string;
  const playerApproval = await request.post(`${backend}/api/v1/admin/agents/${applicantOne.agentId}/approve`, {
    headers: { Authorization: `Bearer ${playerToken}` },
  });
  expect(playerApproval.status()).toBe(403);
  const agentApproval = await request.post(`${backend}/api/v1/admin/agents/${applicantOne.agentId}/approve`, {
    headers: { Authorization: `Bearer ${firstSession.accessToken}` },
  });
  expect(agentApproval.status()).toBe(403);
  const duplicateApproval = await request.post(`${backend}/api/v1/admin/agents/${applicantOne.agentId}/approve`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  expect(duplicateApproval.status()).toBe(409);
  const attemptedUserMutation = await request.put(`${backend}/api/v1/admin/users/${applicantOne.userId}`, {
    headers: { Authorization: `Bearer ${adminToken}` },
    data: { displayName: 'Changed from user API', email: 'agent@example.test', phoneNumber: phoneOne, status: 'SUSPENDED', roles: ['SUPPORT'] },
  });
  expect(attemptedUserMutation.status()).toBe(409);

  await saveEvidence('module-02a-agent-approval-browser.json', {
    agentOne: { agentId: applicantOne.agentId, statusAfterReload: 'APPROVED', login: firstLogin.status(), authorizedDashboard: authorizedDashboard.status() },
    agentTwo: { agentId: applicantTwo.agentId, statusAfterApproval: 'APPROVED', login: secondLogin.status() },
    authorization: { playerApproval: playerApproval.status(), agentApproval: agentApproval.status(), duplicateApproval: duplicateApproval.status(), genericUserMutation: attemptedUserMutation.status() },
  });
});

test('admin suspends an approved agent and invalidates login, token and float operations', async ({ page, request }) => {
  const phone = requiredQaEnv('BINGO_M02A_AGENT_TWO_PHONE');
  const password = requiredQaEnv('BINGO_M02A_AGENT_TWO_PASSWORD');
  const pin = requiredQaEnv('BINGO_M02A_AGENT_TWO_PIN');
  const backend = qaBaseUrls().backend;

  await loginAdmin(page);
  const { agent, token: adminToken } = await findAgent(page, phone);
  expect(agent.status).toBe('APPROVED');
  const agentLogin = await signInAgent(request, phone, password, pin);
  expect(agentLogin.status()).toBe(200);
  const agentSession = await agentLogin.json();
  const preSuspendDashboard = await request.get(`${backend}/api/v1/agent/dashboard`, {
    headers: { Authorization: `Bearer ${agentSession.accessToken}` },
  });
  expect(preSuspendDashboard.status()).toBe(200);
  const preSuspendFloat = await request.get(`${backend}/api/v1/agent/float`, {
    headers: { Authorization: `Bearer ${agentSession.accessToken}` },
  });
  expect(preSuspendFloat.status()).toBe(200);

  await page.goto(`/agents/${agent.agentId}`);
  await expect(page.getByRole('heading', { name: 'QA M02A Agent Two' })).toBeVisible();
  await page.getByRole('button', { name: 'Suspend agent' }).click();
  await expect(page.getByRole('heading', { name: 'Suspend agent' })).toBeVisible();
  await page.getByRole('button', { name: 'Suspend', exact: true }).click();
  await expect(page.getByText('SUSPENDED', { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByText('SUSPENDED', { exact: true })).toBeVisible();

  const deniedLogin = await signInAgent(request, phone, password, pin);
  expect(deniedLogin.status()).toBe(401);
  const oldTokenDashboard = await request.get(`${backend}/api/v1/agent/dashboard`, {
    headers: { Authorization: `Bearer ${agentSession.accessToken}` },
  });
  expect(oldTokenDashboard.status()).toBe(401);
  const oldTokenFloat = await request.get(`${backend}/api/v1/agent/float`, {
    headers: { Authorization: `Bearer ${agentSession.accessToken}` },
  });
  expect(oldTokenFloat.status()).toBe(401);
  const oldTokenFloatMutation = await request.post(`${backend}/api/v1/agent/float/transfers`, {
    headers: { Authorization: `Bearer ${agentSession.accessToken}` }, data: {},
  });
  expect(oldTokenFloatMutation.status()).toBe(401);
  const unauthApproval = await request.post(`${backend}/api/v1/admin/agents/${agent.agentId}/approve`);
  expect(unauthApproval.status()).toBe(401);
  const statusAfterRefresh = await request.get(`${backend}/api/v1/admin/agents/${agent.agentId}`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  expect(statusAfterRefresh.status()).toBe(200);
  expect((await statusAfterRefresh.json()).status).toBe('SUSPENDED');

  await saveEvidence('module-02a-agent-suspension-browser.json', {
    agent: { agentId: agent.agentId, statusAfterRefresh: 'SUSPENDED' },
    beforeSuspend: { login: agentLogin.status(), dashboard: preSuspendDashboard.status(), float: preSuspendFloat.status() },
    afterSuspend: { newLogin: deniedLogin.status(), oldTokenDashboard: oldTokenDashboard.status(), oldTokenFloat: oldTokenFloat.status(), oldTokenFloatMutation: oldTokenFloatMutation.status() },
    authorization: { unauthenticatedApproval: unauthApproval.status() },
  });
});
