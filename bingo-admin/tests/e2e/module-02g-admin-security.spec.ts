import { randomBytes } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { expect, test, type Page } from '@playwright/test';
import { mkdir, readFile } from 'node:fs/promises';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { loginAdmin, qaBaseUrls, requiredQaEnv } from './support/qaHelpers';

const backend = qaBaseUrls().backend;
const evidenceDir = path.resolve(process.cwd(), '../docs/qa/evidence');
const smtpCaptureFile = path.resolve(process.cwd(), '../.tmp/smtp-catcher-m02b.txt');
const runId = `${Date.now()}-${randomBytes(3).toString('hex')}`;
type User = { id: number; displayName: string; email: string; phoneNumber: string; roles: string[]; status: string };
let supportUser: User;
let supportPassword: string;

function qaEnv(): Record<string, string> {
  const text = readFileSync(path.resolve(process.cwd(), '../bingo-backend/.env.qa'), 'utf8');
  return Object.fromEntries(text.split(/\r?\n/).filter((line: string) => line && !line.startsWith('#'))
    .map((line: string) => { const index = line.indexOf('='); return [line.slice(0, index), line.slice(index + 1).trim().replace(/^['"]|['"]$/g, '')]; }));
}

function db(sql: string): string {
  const env = qaEnv();
  return execFileSync('C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe', [
    '--batch', '--skip-column-names', '--host', env.BINGO_QA_MYSQL_HOST, '--port', env.BINGO_QA_MYSQL_PORT,
    '--user', env.BINGO_QA_MYSQL_USERNAME, env.BINGO_QA_MYSQL_DATABASE, '--execute', sql,
  ], { env: { ...process.env, MYSQL_PWD: env.BINGO_QA_MYSQL_PASSWORD }, encoding: 'utf8' }).trim();
}

async function token(page: Page): Promise<string> {
  return page.evaluate(() => JSON.parse(localStorage.getItem('bingo-admin-session') ?? '{}').accessToken as string);
}

async function adminApi(page: Page, method: string, route: string, data?: unknown) {
  return page.request.fetch(`${backend}${route}`, {
    method,
    headers: { Authorization: `Bearer ${await token(page)}` },
    data,
  });
}

async function createStaff(page: Page, role: 'ADMIN' | 'SUPPORT', label: string) {
  const user = await adminApi(page, 'POST', '/api/v1/admin/users', {
    displayName: `QA M02G ${label} ${runId}`,
    email: `qa-m02g-${label.toLowerCase().replace(/\s+/g, '-')}-${runId}@example.com`,
    phoneNumber: `091${String(randomBytes(4).readUInt32BE(0) % 10_000_000).padStart(7, '0')}`,
    status: 'ACTIVE', roles: [role],
  });
  expect(user.status(), await user.text()).toBe(200);
  return await user.json() as User;
}

async function capture(page: Page, name: string) {
  await mkdir(evidenceDir, { recursive: true });
  await page.screenshot({ path: path.join(evidenceDir, `module-02g-${name}.png`), fullPage: true });
}

async function loginSupport(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Email address').fill(supportUser.email);
  await page.locator('input[name="password"]').fill(supportPassword);
  const response = page.waitForResponse(r => r.url().endsWith('/api/v1/admin/login') && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Sign in' }).click();
  expect((await response).status()).toBe(200);
  await expect(page.getByRole('heading', { name: /Support/i })).toBeVisible();
}

async function createTicket(page: Page, suffix: string): Promise<number> {
  const env = qaEnv();
  const login = await page.request.post(`${backend}/api/v1/auth/login`, { data: {
    phoneNumber: env.BINGO_QA_PLAYER_001_PHONE, password: env.BINGO_QA_PLAYER_001_PIN,
  } });
  expect(login.status()).toBe(200);
  const playerToken = (await login.json()).accessToken as string;
  const response = await page.request.post(`${backend}/api/v1/support/tickets`, {
    headers: { Authorization: `Bearer ${playerToken}` },
    data: { subject: `QA M02G ${suffix} ${runId}`, description: 'Dedicated Module 02G ticket fixture', category: 'ACCOUNT', priority: 'NORMAL' },
  });
  expect(response.status()).toBe(200);
  return (await response.json()).id as number;
}

test.describe.serial('Module 02G admin identity, role security and support', () => {
  test.beforeAll(async ({ browser }) => {
    const context = await browser.newContext({ baseURL: requiredQaEnv('BINGO_E2E_ADMIN_URL') });
    const page = await context.newPage();
    const emailOffset = (await readFile(smtpCaptureFile, 'utf8')).length;
    try {
      await loginAdmin(page);
      supportUser = await createStaff(page, 'SUPPORT', 'Support Operator');
      await expect.poll(async () => {
        const current = await readFile(smtpCaptureFile, 'utf8');
        return current.slice(emailOffset);
      }, { timeout: 10_000, intervals: [250, 500] }).toContain(supportUser.email);
      const message = (await readFile(smtpCaptureFile, 'utf8')).slice(emailOffset).split('---MESSAGE-END---')[0];
      const activationLink = message.split(/\r?\n/).map(line => line.trim())
        .find(line => line.startsWith('http://localhost:5174/activate-account#token='));
      expect(activationLink).toBeTruthy();
      supportPassword = `QaM02G!${randomBytes(8).toString('hex')}`;
      await page.goto(activationLink!);
      await page.getByRole('textbox', { name: 'New password', exact: true }).fill(supportPassword);
      await page.getByRole('textbox', { name: 'Confirm new password', exact: true }).fill(supportPassword);
      const activation = page.waitForResponse(r => r.url().endsWith('/api/v1/admin/activate') && r.request().method() === 'POST');
      await page.getByRole('button', { name: 'Activate account' }).click();
      expect((await activation).status()).toBe(200);
    } finally {
      await context.close();
    }
  });

  test('finance role revocation removes permission from UI and database', async ({ page }) => {
    await loginAdmin(page);
    const target = await createStaff(page, 'SUPPORT', 'Finance Revocation');
    const grant = await adminApi(page, 'POST', `/api/v1/admin/users/${target.id}/finance-roles`, { roles: ['FINANCE_VERIFIER'] });
    expect(grant.status()).toBe(200);
    await page.goto('/admin-users');
    await page.getByLabel('Search by name, email, or phone').fill(target.email);
    const row = page.getByRole('row').filter({ hasText: target.email });
    await expect(row).toContainText('FINANCE_VERIFIER');
    const responsePromise = page.waitForResponse(r => r.url().includes(`/api/v1/admin/users/${target.id}/finance-roles`) && r.request().method() === 'DELETE');
    await row.getByRole('button', { name: /Revoke FINANCE_VERIFIER/ }).click();
    expect((await responsePromise).status()).toBe(200);
    expect(db(`select count(*) from user_roles where user_id=${target.id} and role='FINANCE_VERIFIER'`)).toBe('0');
    expect(db(`select count(*) from audit_log_entries where action='REVOKE' and target_id=${target.id}`)).toBe('1');
    await page.reload();
    await page.getByLabel('Search by name, email, or phone').fill(target.email);
    await expect(page.getByRole('row').filter({ hasText: target.email })).not.toContainText('FINANCE_VERIFIER');
    await capture(page, 'finance-revoke');
  });

  test('admin/support role transition persists and is audited', async ({ page }) => {
    await loginAdmin(page);
    const target = await createStaff(page, 'SUPPORT', 'Role Transition');
    await page.goto('/admin-users');
    await page.getByLabel('Search by name, email, or phone').fill(target.email);
    const row = page.getByRole('row').filter({ hasText: target.email });
    await row.getByRole('button', { name: 'Edit' }).click();
    await page.getByRole('dialog').getByLabel('Role').click();
    await page.getByRole('option', { name: 'ADMIN', exact: true }).click();
    const responsePromise = page.waitForResponse(r => r.url().endsWith(`/api/v1/admin/users/${target.id}`) && r.request().method() === 'PUT');
    await page.getByRole('dialog').getByRole('button', { name: 'Save changes' }).click();
    expect((await responsePromise).status()).toBe(200);
    expect(db(`select count(*) from user_roles where user_id=${target.id} and role='ADMIN'`)).toBe('1');
    expect(db(`select count(*) from audit_log_entries where action='UPDATE_USER' and target_id=${target.id}`)).toBe('1');
    await page.reload();
    await page.getByLabel('Search by name, email, or phone').fill(target.email);
    await expect(page.getByRole('row').filter({ hasText: target.email })).toContainText('ADMIN');
    await capture(page, 'role-transition');
  });

  test('agent PIN is set through masked admin UI and hashed in storage', async ({ page }) => {
    await loginAdmin(page);
    const phone = `091${String(randomBytes(4).readUInt32BE(0) % 10_000_000).padStart(7, '0')}`;
    const application = await page.request.post(`${backend}/api/v1/agent/register`, { data: {
      displayName: `QA M02G Agent ${runId}`, phoneNumber: phone, pin: '123456', city: 'Addis Ababa', territory: 'QA',
      idDocumentType: 'QA', idDocumentNumber: `M02G-${runId}`,
    } });
    expect(application.status()).toBe(201);
    const agent = await application.json() as { agentId: number; userId: number };
    const approve = await adminApi(page, 'POST', `/api/v1/admin/agents/${agent.agentId}/approve`);
    expect(approve.status()).toBe(200);
    await page.goto(`/agents/${agent.agentId}`);
    const input = page.getByLabel('New agent PIN');
    await input.fill('842691');
    await expect(input).toHaveAttribute('type', 'password');
    const responsePromise = page.waitForResponse(r => r.url().endsWith(`/api/v1/admin/users/${agent.userId}/agent-pin`) && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Set agent PIN' }).click();
    expect((await responsePromise).status()).toBe(200);
    expect(db(`select count(*) from users where id=${agent.userId} and agent_pin_hash is not null and agent_pin_hash <> '842691'`)).toBe('1');
    expect(db(`select count(*) from audit_log_entries where action='SET_AGENT_PIN' and target_id=${agent.userId} and metadata_json not like '%842691%'`)).toBe('1');
    await page.reload();
    await expect(page.getByText('Agent PIN updated. The PIN is not displayed again.')).toHaveCount(0);
    await capture(page, 'agent-pin');
  });

  test('SUPPORT ticket assignment persists after refresh', async ({ page }) => {
    await loginSupport(page);
    const ticketId = await createTicket(page, 'Assignment');
    await page.goto(`/support/tickets/${ticketId}`);
    await page.getByLabel('Assignee').click();
    await page.getByRole('option', { name: supportUser.displayName }).click();
    const responsePromise = page.waitForResponse(r => r.url().endsWith(`/api/v1/admin/support/tickets/${ticketId}/assign`) && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Save assignment' }).click();
    expect((await responsePromise).status()).toBe(200);
    expect(db(`select count(*) from support_tickets where id=${ticketId} and assigned_to_user_id=${supportUser.id} and status='IN_PROGRESS'`)).toBe('1');
    expect(db(`select count(*) from audit_log_entries where action='ASSIGN_TICKET' and target_id=${ticketId} and actor_user_id=${supportUser.id} and json_unquote(json_extract(metadata_json,'$.outcome'))='SUCCESS'`)).toBe('1');
    await page.reload();
    await expect(page.getByText(`Assigned to: ${supportUser.id}`)).toBeVisible();
    await capture(page, 'support-assignment');
  });

  test('SUPPORT ticket status change persists after refresh', async ({ page }) => {
    await loginSupport(page);
    const ticketId = await createTicket(page, 'Status');
    await page.goto(`/support/tickets/${ticketId}`);
    await page.getByLabel('Reply body').fill('Synthetic SUPPORT public reply for Module 02G.');
    const replyPromise = page.waitForResponse(r => r.url().endsWith(`/api/v1/admin/support/tickets/${ticketId}/messages`) && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Send public reply' }).click();
    expect((await replyPromise).status()).toBe(200);
    expect(db(`select count(*) from support_messages where ticket_id=${ticketId} and author_user_id=${supportUser.id} and body='Synthetic SUPPORT public reply for Module 02G.'`)).toBe('1');
    expect(db(`select count(*) from audit_log_entries where action='ADD_ADMIN_REPLY' and target_id=${ticketId} and actor_user_id=${supportUser.id} and json_unquote(json_extract(metadata_json,'$.outcome'))='SUCCESS'`)).toBe('1');
    await page.getByLabel('New status').click();
    await page.getByRole('option', { name: 'CLOSED', exact: true }).click();
    await page.getByLabel('Resolution summary').fill('Closed after synthetic Module 02G verification.');
    await page.getByRole('button', { name: 'Review status change' }).click();
    const responsePromise = page.waitForResponse(r => r.url().endsWith(`/api/v1/admin/support/tickets/${ticketId}/status`) && r.request().method() === 'PUT');
    await page.getByRole('button', { name: 'Confirm', exact: true }).click();
    expect((await responsePromise).status()).toBe(200);
    expect(db(`select count(*) from support_tickets where id=${ticketId} and status='CLOSED' and resolution_summary is not null`)).toBe('1');
    expect(db(`select count(*) from audit_log_entries where action='UPDATE_STATUS' and target_id=${ticketId} and actor_user_id=${supportUser.id} and json_unquote(json_extract(metadata_json,'$.outcome'))='SUCCESS'`)).toBe('1');
    await page.reload();
    await expect(page.getByText('Status: CLOSED')).toBeVisible();
    await capture(page, 'support-status');
  });

  test('unauthorized player cannot mutate support ticket from the browser context', async ({ page }) => {
    await loginSupport(page);
    const ticketId = await createTicket(page, 'Unauthorized');
    await page.goto(`/support/tickets/${ticketId}`);
    const env = qaEnv();
    const login = await page.request.post(`${backend}/api/v1/auth/login`, { data: {
      phoneNumber: env.BINGO_QA_PLAYER_001_PHONE, password: env.BINGO_QA_PLAYER_001_PIN,
    } });
    expect(login.status()).toBe(200);
    const playerToken = (await login.json()).accessToken as string;
    const result = await page.evaluate(async ({ api, id, tokenValue }) => {
      const response = await fetch(`${api}/api/v1/admin/support/tickets/${id}/status`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tokenValue}` },
        body: JSON.stringify({ status: 'CLOSED', resolutionSummary: 'unauthorized attempt' }),
      });
      return response.status;
    }, { api: backend, id: ticketId, tokenValue: playerToken });
    expect(result).toBe(403);
    expect(db(`select count(*) from support_tickets where id=${ticketId} and status='OPEN'`)).toBe('1');
    await capture(page, 'support-unauthorized-mutation');
  });
});
