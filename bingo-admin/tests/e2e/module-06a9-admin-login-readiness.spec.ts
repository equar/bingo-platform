import { expect, test } from '@playwright/test';
import { loginAdmin, qaBaseUrls, requiredQaEnv } from './support/qaHelpers';

type AgentSummary = {
  normalizedPhone: string;
  status: string;
};

test('Admin login readiness and retained Agent states', async ({ page }) => {
  await loginAdmin(page);

  const session = await page.evaluate(() => {
    return JSON.parse(localStorage.getItem('bingo-admin-session') ?? '{}') as { accessToken?: string };
  });
  expect(session.accessToken).toBeTruthy();
  const response = await page.request.get(
    `${qaBaseUrls().backend}/api/v1/admin/agents?page=0&size=100`,
    { headers: { Authorization: `Bearer ${session.accessToken}` } },
  );
  expect(response.ok()).toBeTruthy();
  const agents = (await response.json()).content as AgentSummary[];
  const normalize = (phone: string) => phone.startsWith('0') ? `251${phone.slice(1)}` : phone;
  const candidate = agents.find((agent) => agent.normalizedPhone === normalize(requiredQaEnv('BINGO_QA_APPROVED_AGENT_PHONE')));
  const pending = agents.find((agent) => agent.normalizedPhone === normalize(requiredQaEnv('BINGO_QA_PENDING_AGENT_PHONE')));
  expect(candidate?.status).toBe('APPROVED');
  expect(pending?.status).toBe('PENDING');
});
