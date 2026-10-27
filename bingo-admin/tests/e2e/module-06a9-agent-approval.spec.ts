import { expect, test } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { loginAdmin, qaBaseUrls, requiredQaEnv } from './support/qaHelpers';

type AgentSummary = {
  agentId: number;
  userId: number;
  normalizedPhone: string;
  status: string;
};

async function findAgent(page: import('@playwright/test').Page, phone: string): Promise<AgentSummary> {
  const session = await page.evaluate(() => {
    return JSON.parse(localStorage.getItem('bingo-admin-session') ?? '{}') as { accessToken?: string };
  });
  if (!session.accessToken) {
    throw new Error('Authenticated Admin session is unavailable.');
  }

  const response = await page.request.get(
    `${qaBaseUrls().backend}/api/v1/admin/agents?page=0&size=100`,
    { headers: { Authorization: `Bearer ${session.accessToken}` } },
  );
  expect(response.ok()).toBeTruthy();
  const agents = (await response.json()).content as AgentSummary[];
  const normalizedPhone = phone.startsWith('0') ? `251${phone.slice(1)}` : phone;
  const agent = agents.find((candidate) => candidate.normalizedPhone === normalizedPhone);
  expect(agent).toBeTruthy();
  return agent!;
}

test('Admin approves the synthetic Android-test Agent through the UI and retains a pending fixture', async ({ page }) => {
  const candidatePhone = requiredQaEnv('BINGO_QA_APPROVED_AGENT_PHONE');
  const pendingPhone = requiredQaEnv('BINGO_QA_PENDING_AGENT_PHONE');

  await loginAdmin(page);
  const candidate = await findAgent(page, candidatePhone);
  const pending = await findAgent(page, pendingPhone);
  expect(candidate.status).toBe('PENDING');
  expect(pending.status).toBe('PENDING');

  await page.goto(`/agents/${candidate.agentId}`);
  await expect(page.getByRole('heading', { name: 'QA Android Test Approval Candidate' })).toBeVisible();
  await page.getByRole('button', { name: 'Approve application' }).click();
  await expect(page.getByRole('heading', { name: 'Approve agent application' })).toBeVisible();
  await page.getByRole('button', { name: 'Approve', exact: true }).click();
  await expect(page.getByText('APPROVED', { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByText('APPROVED', { exact: true })).toBeVisible();

  await page.goto(`/agents/${pending.agentId}`);
  await expect(page.getByRole('heading', { name: 'QA Android Test Pending Agent' })).toBeVisible();
  await expect(page.getByText('PENDING', { exact: true })).toBeVisible();

  const evidencePath = path.resolve(
    process.cwd(),
    '../docs/qa/evidence/module-06a9/QA_M06A9_20261027_admin-approval.json',
  );
  await mkdir(path.dirname(evidencePath), { recursive: true });
  await writeFile(
    evidencePath,
    `${JSON.stringify({
      scenario: 'Normal Admin Chromium Agent approval',
      approvalCandidate: {
        userId: candidate.userId,
        agentId: candidate.agentId,
        statusAfterReload: 'APPROVED',
      },
      retainedPendingAgent: {
        userId: pending.userId,
        agentId: pending.agentId,
        status: 'PENDING',
      },
      credentialsRecorded: false,
    }, null, 2)}\n`,
    'utf8',
  );
});
