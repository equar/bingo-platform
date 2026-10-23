import { randomBytes } from 'node:crypto';
import { expect, test, type Page } from '@playwright/test';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { loginAdmin, qaBaseUrls, requiredQaEnv } from './support/qaHelpers';

const backend = qaBaseUrls().backend;
const evidenceDir = path.resolve(process.cwd(), '../docs/qa/evidence');
const smtpCaptureFile = path.resolve(process.cwd(), '../.tmp/smtp-catcher-m02b.txt');
const runId = `${Date.now()}-${randomBytes(3).toString('hex')}`;
let pageErrors: string[] = [];
let consoleErrors: string[] = [];

function localPhone(phoneNumber: string) {
  const normalized = phoneNumber.replace(/\D/g, '');
  return normalized.startsWith('251') && normalized.length === 12
    ? `0${normalized.slice(3)}`
    : phoneNumber;
}

test.beforeEach(async ({ page }) => {
  page.setDefaultTimeout(5000);
  pageErrors = [];
  consoleErrors = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') consoleErrors.push(message.text()); });
});
test.afterEach(() => {
  expect(pageErrors, 'browser page errors').toEqual([]);
  expect(consoleErrors.filter(message => !/responded with a status of 40[13]/.test(message)), 'unexpected browser console errors').toEqual([]);
});

test('A00: suspend any retained active M02F synthetic admin accounts from interrupted runs', async ({ page }) => {
  await loginAdmin(page);
  await page.goto('/admin-users');
  await page.getByLabel('Search by name, email, or phone').fill('QA M02F ');
  const rows = page.getByRole('row').filter({ hasText: 'QA M02F ' });
  await expect(rows.first()).toBeVisible();
  const count = await rows.count();
  expect(count).toBeGreaterThan(0);
  for (let index = 0; index < count; index += 1) {
    const row = rows.nth(index);
    if (await row.getByText('SUSPENDED', { exact: true }).count()) continue;
    await row.getByRole('button', { name: 'Edit' }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('Status').click();
    await page.getByRole('option', { name: 'SUSPENDED', exact: true }).click();
    const updatePromise = page.waitForResponse(response =>
      response.url().match(/\/api\/v1\/admin\/users\/\d+$/) !== null && response.request().method() === 'PUT',
      { timeout: 10_000 });
    await dialog.getByRole('button', { name: 'Save changes' }).click();
    const updateResponse = await updatePromise;
    expect(updateResponse.status()).toBe(200);
    expect((await updateResponse.json()).status).toBe('SUSPENDED');
    await expect(page.getByRole('dialog')).toHaveCount(0);
  }
  await page.reload();
  await page.getByLabel('Search by name, email, or phone').fill('QA M02F ');
  const persistedRows = page.getByRole('row').filter({ hasText: 'QA M02F ' });
  await expect(persistedRows).toHaveCount(count);
  for (const row of await persistedRows.all()) {
    await expect(row).toContainText('SUSPENDED');
  }
});

async function capture(page: Page, name: string) {
  await mkdir(evidenceDir, { recursive: true });
  await page.screenshot({ path: path.join(evidenceDir, `module-02f-${name}.png`), fullPage: true });
}

test('A01-A05: create and activate a synthetic admin, then suspend it through the browser', async ({ page, browser }) => {
  const emailOffset = (await readFile(smtpCaptureFile, 'utf8')).length;
  let activationPassword: string | undefined;
  let createdUser: { id: number; displayName: string; email: string; phoneNumber: string; roles: string[] } | undefined;
  let mainAdminToken: string | undefined;
  let suspended = false;
  try {
  await loginAdmin(page);
  mainAdminToken = await page.evaluate(() => JSON.parse(localStorage.getItem('bingo-admin-session') ?? '{}').accessToken as string);
  await page.goto('/admin-users');
  await expect(page.getByRole('heading', { name: 'Admin Users' })).toBeVisible();
  await page.getByRole('button', { name: 'Create admin user' }).click();
  const dialog = page.getByRole('dialog');
  const displayName = `QA M02F Admin ${runId}`;
  const email = `qa-m02f-admin-${runId}@example.invalid`;
  const phone = `091${String(randomBytes(4).readUInt32BE(0) % 10_000_000).padStart(7, '0')}`;
  await dialog.getByLabel('Display name').fill(displayName);
  await dialog.getByLabel('Email').fill(email);
  await dialog.getByLabel('Phone number').fill(phone);
  await dialog.getByLabel('Status').click();
  await page.getByRole('option', { name: 'ACTIVE', exact: true }).click();
  await dialog.getByLabel('Role').click();
  await page.getByRole('option', { name: 'ADMIN', exact: true }).click();
  const createPromise = page.waitForResponse(response =>
    response.url().endsWith('/api/v1/admin/users') && response.request().method() === 'POST');
  await dialog.getByRole('button', { name: 'Create user' }).click();
  const createResponse = await createPromise;
  expect(createResponse.status()).toBe(200);
  const user = await createResponse.json() as { id: number; displayName: string; email: string; phoneNumber: string; status: string; roles: string[] };
  createdUser = user;
  expect(user).toMatchObject({ email, status: 'ACTIVE', roles: ['ADMIN'] });

  let capturedEmail = '';
  await expect.poll(async () => {
    const content = await readFile(smtpCaptureFile, 'utf8');
    capturedEmail = content.slice(emailOffset);
    return capturedEmail.includes(email!) && capturedEmail.includes('---MESSAGE-END---');
  }, { timeout: 10_000, intervals: [100, 250, 500] }).toBeTruthy();
  const message = capturedEmail.split('---MESSAGE-END---')[0];
  const activationLink = message.split(/\r?\n/).map(line => line.trim())
    .find(line => line.startsWith('http://localhost:5174/activate-account#token='));
  expect(activationLink, 'activation link must use the Module 02 QA UI on port 5174').toBeTruthy();
  expect(message).not.toMatch(/https?:\/\/localhost:5173/);

  activationPassword = `QaM02F!${randomBytes(8).toString('hex')}`;
  await page.goto(activationLink!);
  await page.getByRole('textbox', { name: 'New password', exact: true }).fill(activationPassword);
  await page.getByRole('textbox', { name: 'Confirm new password', exact: true }).fill(activationPassword);
  const activationPromise = page.waitForResponse(response =>
    response.url().endsWith('/api/v1/admin/activate') && response.request().method() === 'POST', { timeout: 10_000 });
  await page.getByRole('button', { name: 'Activate account' }).click();
  expect((await activationPromise).status()).toBe(200);
  await expect(page.getByText('Your account is ready. Sign in with your new password.')).toBeVisible();
  await capture(page, 'admin-activation-complete');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await page.getByLabel('Email address').fill(email);
  await page.locator('input[name="password"]').fill(activationPassword);
  const loginPromise = page.waitForResponse(response =>
    response.url().endsWith('/api/v1/admin/login') && response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  expect((await loginPromise).status()).toBe(200);
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();

  await page.goto('/change-password');
  const changedPassword = `QaM02F!${randomBytes(8).toString('hex')}`;
  await page.getByRole('textbox', { name: 'Current password' }).fill(activationPassword);
  await page.getByRole('textbox', { name: 'New password', exact: true }).fill(changedPassword);
  await page.getByRole('textbox', { name: 'Confirm new password', exact: true }).fill(changedPassword);
  const passwordChangePromise = page.waitForResponse(response =>
    response.url().endsWith('/api/v1/admin/me/password') && response.request().method() === 'PUT');
  await page.getByRole('button', { name: 'Save password' }).click();
  expect((await passwordChangePromise).status()).toBe(200);
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  const oldPasswordLogin = await page.request.post(`${backend}/api/v1/admin/login`, {
    data: { email, password: activationPassword },
  });
  expect(oldPasswordLogin.status()).toBe(401);
  activationPassword = changedPassword;

  const adminContext = await browser.newContext({ baseURL: requiredQaEnv('BINGO_E2E_ADMIN_URL') });
  const adminPage = await adminContext.newPage();
  try {
    await loginAdmin(adminPage);
    await adminPage.goto('/admin-users');
    await adminPage.getByLabel('Search by name, email, or phone').fill(email);
    const row = adminPage.getByRole('row').filter({ hasText: email });
    await expect(row).toBeVisible();
    await expect(row).toContainText('ADMIN');
    await expect(row).toContainText('ACTIVE');
    await row.getByRole('button', { name: 'Edit' }).click();
    await adminPage.getByRole('dialog').getByLabel('Status').click();
    await adminPage.getByRole('option', { name: 'SUSPENDED', exact: true }).click();
    const updatePromise = adminPage.waitForResponse(response =>
      response.url().endsWith(`/api/v1/admin/users/${user.id}`) && response.request().method() === 'PUT');
    await adminPage.getByRole('dialog').getByRole('button', { name: 'Save changes' }).click();
    const updateResponse = await updatePromise;
    expect(updateResponse.status()).toBe(200);
    expect((await updateResponse.json()).status).toBe('SUSPENDED');
    suspended = true;
    await adminPage.reload();
    await adminPage.getByLabel('Search by name, email, or phone').fill(email);
    await expect(adminPage.getByRole('row').filter({ hasText: email })).toContainText('SUSPENDED');
    const suspendedLogin = await adminPage.request.post(`${backend}/api/v1/admin/login`, { data: { email, password: activationPassword } });
    expect(suspendedLogin.status()).toBe(401);
    const audit = await adminPage.request.get(`${backend}/api/v1/admin/audit-logs?page=0&size=100`, {
      headers: { Authorization: `Bearer ${await adminPage.evaluate(() => JSON.parse(localStorage.getItem('bingo-admin-session') ?? '{}').accessToken as string)}` },
    });
    expect(audit.status()).toBe(200);
    expect((await audit.json()).content.some((entry: { targetId: number }) => entry.targetId === user.id)).toBeTruthy();
  } finally {
    await adminContext.close();
  }
  } finally {
    if (createdUser && mainAdminToken && !suspended) {
      await page.request.put(`${backend}/api/v1/admin/users/${createdUser.id}`, {
        headers: { Authorization: `Bearer ${mainAdminToken}` },
        data: {
          displayName: createdUser.displayName,
          email: createdUser.email,
          phoneNumber: localPhone(createdUser.phoneNumber),
          status: 'SUSPENDED',
          roles: createdUser.roles,
        },
        timeout: 5000,
      });
    }
    // Activation links are secrets; keep only sanitized test evidence.
    await writeFile(smtpCaptureFile, '');
  }
});

test('A11-A14: SUPPORT users reach only support pages and their privileges stop when suspended', async ({ page, browser }) => {
  const emailOffset = (await readFile(smtpCaptureFile, 'utf8')).length;
  let createdUser: { id: number; displayName: string; email: string; phoneNumber: string; roles: string[] } | undefined;
  let mainAdminToken: string | undefined;
  let suspended = false;
  let supportContext: Awaited<ReturnType<typeof browser.newContext>> | undefined;
  const supportPageErrors: string[] = [];
  const supportConsoleErrors: string[] = [];
  try {
    await loginAdmin(page);
    mainAdminToken = await page.evaluate(() => JSON.parse(localStorage.getItem('bingo-admin-session') ?? '{}').accessToken as string);
    await page.goto('/admin-users');
    await page.getByRole('button', { name: 'Create admin user' }).click();
    const dialog = page.getByRole('dialog');
    const displayName = `QA M02F Support ${runId}`;
    const email = `qa-m02f-support-${runId}@example.invalid`;
    const phone = `091${String(randomBytes(4).readUInt32BE(0) % 10_000_000).padStart(7, '0')}`;
    await dialog.getByLabel('Display name').fill(displayName);
    await dialog.getByLabel('Email').fill(email);
    await dialog.getByLabel('Phone number').fill(phone);
    await dialog.getByLabel('Role').click();
    await page.getByRole('option', { name: 'SUPPORT', exact: true }).click();
    const createPromise = page.waitForResponse(response =>
      response.url().endsWith('/api/v1/admin/users') && response.request().method() === 'POST');
    await dialog.getByRole('button', { name: 'Create user' }).click();
    const createResponse = await createPromise;
    expect(createResponse.status()).toBe(200);
    const user = await createResponse.json() as { id: number; displayName: string; email: string; phoneNumber: string; status: string; roles: string[] };
    createdUser = user;
    expect(user).toMatchObject({ email, status: 'ACTIVE', roles: ['SUPPORT'] });

    let capturedEmail = '';
    await expect.poll(async () => {
      const content = await readFile(smtpCaptureFile, 'utf8');
      capturedEmail = content.slice(emailOffset);
      return capturedEmail.includes(email) && capturedEmail.includes('---MESSAGE-END---');
    }, { timeout: 10_000, intervals: [100, 250, 500] }).toBeTruthy();
    const message = capturedEmail.split('---MESSAGE-END---')[0];
    const activationLink = message.split(/\r?\n/).map(line => line.trim())
      .find(line => line.startsWith('http://localhost:5174/activate-account#token='));
    expect(activationLink, 'support activation link must use the QA UI on port 5174').toBeTruthy();
    expect(message).not.toMatch(/https?:\/\/localhost:5173/);
    const password = `QaM02F!${randomBytes(8).toString('hex')}`;
    await page.goto(activationLink!);
    await page.getByRole('textbox', { name: 'New password', exact: true }).fill(password);
    await page.getByRole('textbox', { name: 'Confirm new password', exact: true }).fill(password);
    const activationPromise = page.waitForResponse(response =>
      response.url().endsWith('/api/v1/admin/activate') && response.request().method() === 'POST', { timeout: 10_000 });
    await page.getByRole('button', { name: 'Activate account' }).click();
    expect((await activationPromise).status()).toBe(200);

    supportContext = await browser.newContext({ baseURL: requiredQaEnv('BINGO_E2E_ADMIN_URL') });
    const supportPage = await supportContext.newPage();
    supportPage.on('pageerror', error => supportPageErrors.push(error.message));
    supportPage.on('console', message => { if (message.type() === 'error') supportConsoleErrors.push(message.text()); });
    await supportPage.goto('/login');
    await supportPage.getByLabel('Email address').fill(email);
    await supportPage.locator('input[name="password"]').fill(password);
    const loginPromise = supportPage.waitForResponse(response =>
      response.url().endsWith('/api/v1/admin/login') && response.request().method() === 'POST');
    await supportPage.getByRole('button', { name: 'Sign in' }).click();
    expect((await loginPromise).status()).toBe(200);
    await expect(supportPage).toHaveURL(/\/support\/tickets$/);
    await expect(supportPage.getByRole('heading', { name: 'Support tickets' })).toBeVisible();
    await expect(supportPage.getByRole('link', { name: 'Support' })).toBeVisible();
    await expect(supportPage.getByRole('link', { name: 'Players' })).toHaveCount(0);
    const supportToken = await supportPage.evaluate(() => JSON.parse(localStorage.getItem('bingo-admin-session') ?? '{}').accessToken as string);
    const supportHeaders = { Authorization: `Bearer ${supportToken}` };
    expect((await supportPage.request.get(`${backend}/api/v1/admin/support/tickets`, { headers: supportHeaders })).status()).toBe(200);
    expect((await supportPage.request.get(`${backend}/api/v1/admin/users`, { headers: supportHeaders })).status()).toBe(403);
    await supportPage.goto('/dashboard');
    await expect(supportPage.getByText('Access denied')).toBeVisible();

    await loginAdmin(page);
    await page.goto('/admin-users');
    await page.getByLabel('Search by name, email, or phone').fill(email);
    const row = page.getByRole('row').filter({ hasText: email });
    await expect(row).toContainText('SUPPORT');
    await expect(row).toContainText('ACTIVE');
    await row.getByRole('button', { name: 'Edit' }).click();
    await page.getByRole('dialog').getByLabel('Status').click();
    await page.getByRole('option', { name: 'SUSPENDED', exact: true }).click();
    const updatePromise = page.waitForResponse(response =>
      response.url().endsWith(`/api/v1/admin/users/${user.id}`) && response.request().method() === 'PUT');
    await page.getByRole('dialog').getByRole('button', { name: 'Save changes' }).click();
    const updateResponse = await updatePromise;
    expect(updateResponse.status()).toBe(200);
    expect((await updateResponse.json()).status).toBe('SUSPENDED');
    suspended = true;
    await page.reload();
    await page.getByLabel('Search by name, email, or phone').fill(email);
    await expect(page.getByRole('row').filter({ hasText: email })).toContainText('SUSPENDED');
    expect((await supportPage.request.get(`${backend}/api/v1/admin/support/tickets`)).status()).toBe(401);
    expect(supportPageErrors).toEqual([]);
    expect(supportConsoleErrors.filter(message => !/responded with a status of 40[13]/.test(message))).toEqual([]);
  } finally {
    if (supportContext) await supportContext.close();
    if (createdUser && mainAdminToken && !suspended) {
      await page.request.put(`${backend}/api/v1/admin/users/${createdUser.id}`, {
        headers: { Authorization: `Bearer ${mainAdminToken}` },
        data: {
          displayName: createdUser.displayName,
          email: createdUser.email,
          phoneNumber: localPhone(createdUser.phoneNumber),
          status: 'SUSPENDED',
          roles: createdUser.roles,
        },
        timeout: 5000,
      });
    }
    await writeFile(smtpCaptureFile, '');
  }
});

test('A06-A07: password form validation and invalid current password do not change the shared QA admin', async ({ page }) => {
  await loginAdmin(page);
  await page.goto('/change-password');
  await page.getByRole('textbox', { name: 'New password', exact: true }).fill('Unrelated-Password-01!');
  await page.getByRole('textbox', { name: 'Confirm new password', exact: true }).fill('Unrelated-Password-02!');
  await page.getByRole('button', { name: 'Save password' }).click();
  await expect(page.getByRole('alert')).toContainText('New passwords do not match');

  await page.getByRole('textbox', { name: 'Current password' }).fill('Intentionally-Wrong-Current-Password');
  await page.getByRole('textbox', { name: 'New password', exact: true }).fill('Unrelated-Password-01!');
  await page.getByRole('textbox', { name: 'Confirm new password', exact: true }).fill('Unrelated-Password-01!');
  const changePromise = page.waitForResponse(response =>
    response.url().endsWith('/api/v1/admin/me/password') && response.request().method() === 'PUT');
  await page.getByRole('button', { name: 'Save password' }).click();
  expect((await changePromise).status()).toBe(401);
  await expect(page.getByRole('alert')).toContainText('Invalid credentials');
  await expect(page).toHaveURL(/\/change-password$/);
  consoleErrors = consoleErrors.filter(message => !/responded with a status of 401/.test(message));
  const login = await page.request.post(`${backend}/api/v1/admin/login`, {
    data: { email: requiredQaEnv('BINGO_E2E_ADMIN_EMAIL'), password: requiredQaEnv('BINGO_E2E_ADMIN_CURRENT_PASSWORD') },
  });
  expect(login.status()).toBe(200);
});

test('A08-A10: anonymous, player, and agent tokens cannot access admin or finance-role operations', async ({ page }) => {
  const anonymous = await page.request.get(`${backend}/api/v1/admin/users`);
  expect(anonymous.status()).toBe(401);

  const playerLogin = await page.request.post(`${backend}/api/v1/auth/login`, {
    data: {
      phoneNumber: requiredQaEnv('BINGO_E2E_PLAYER_PHONE'),
      password: requiredQaEnv('BINGO_E2E_PLAYER_PIN'),
    },
  });
  expect(playerLogin.status()).toBe(200);
  const playerToken = (await playerLogin.json()).accessToken as string;
  const agentLogin = await page.request.post(`${backend}/api/v1/agent/login`, {
    data: {
      phoneNumber: requiredQaEnv('BINGO_E2E_AGENT_PHONE'),
      password: requiredQaEnv('BINGO_E2E_AGENT_PASSWORD'),
      pin: requiredQaEnv('BINGO_E2E_AGENT_PIN'),
    },
  });
  expect(agentLogin.status()).toBe(200);
  const agentToken = (await agentLogin.json()).accessToken as string;

  for (const accessToken of [playerToken, agentToken]) {
    const users = await page.request.get(`${backend}/api/v1/admin/users`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(users.status()).toBe(403);
    const financeRole = await page.request.post(`${backend}/api/v1/admin/users/146/finance-roles`, {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: { roles: ['FINANCE_APPROVER'] },
    });
    expect(financeRole.status()).toBe(403);
    const floatPost = await page.request.post(`${backend}/api/v1/admin/agents/float-top-up-requests/1/post`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(floatPost.status()).toBe(403);
  }
});
