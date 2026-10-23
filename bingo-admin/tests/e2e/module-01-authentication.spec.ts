import { expect, test } from '@playwright/test';

const api = process.env.BINGO_E2E_BACKEND_URL ?? 'http://localhost:8081';
const adminEmail = process.env.BINGO_E2E_ADMIN_EMAIL ?? '';
const adminPassword = process.env.BINGO_E2E_ADMIN_PASSWORD ?? '';

test.use({ screenshot: 'only-on-failure', trace: 'retain-on-failure' });

test.afterEach(async ({ page }, testInfo) => {
  if (testInfo.status === 'passed') {
    await page.screenshot({ path: testInfo.outputPath('ui-state.png'), fullPage: true });
  }
});

async function openLogin(page: import('@playwright/test').Page) {
  await page.goto('/login');
  await expect(page.getByLabel('Email address')).toBeVisible();
}

async function submitLogin(page: import('@playwright/test').Page, email: string, password: string) {
  await page.getByLabel('Email address').fill(email);
  await page.locator('input[name="password"]').fill(password);
  const responsePromise = page.waitForResponse((response) =>
    response.url().endsWith('/api/v1/admin/login'),
  );
  await page.getByRole('button', { name: 'Sign in' }).click();
  return responsePromise;
}

test('A01/A08/A09/A10/A16/A17: login, refresh persistence, logout, protected route, back and sensitive browser data', async ({ page }) => {
  const browserOutput: string[] = [];
  page.on('console', (message) => browserOutput.push(message.text()));
  page.on('pageerror', (error) => browserOutput.push(error.message));
  await openLogin(page);
  const loginResponse = await submitLogin(page, adminEmail, adminPassword);
  expect(loginResponse.status()).toBe(200);
  const auth = await loginResponse.json();
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();

  const session = await page.evaluate(() => localStorage.getItem('bingo-admin-session'));
  expect(session).toBeTruthy();
  expect(session).not.toContain(adminPassword);
  expect(await page.evaluate(() => sessionStorage.length)).toBe(0);
  expect(browserOutput.join('\n')).not.toContain(adminPassword);
  expect(browserOutput.join('\n')).not.toContain(auth.accessToken);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();

  await page.getByText(auth.user.displayName, { exact: true }).click();
  await page.getByRole('menuitem', { name: 'Logout' }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect(await page.evaluate(() => localStorage.getItem('bingo-admin-session'))).toBeNull();
  await page.goto('/players');
  await expect(page).toHaveURL(/\/login$/);
  await page.goBack();
  await expect(page.getByLabel('Email address')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toHaveCount(0);
});

test('A02/A03: wrong password and unknown email are rejected by the backend', async ({ page }) => {
  await openLogin(page);
  const wrong = await submitLogin(page, adminEmail, `${adminPassword}wrong`);
  expect(wrong.status()).toBe(401);
  await expect(page.getByRole('alert')).toBeVisible();

  const unknown = await submitLogin(page, 'unknown-module-01@example.test', 'NeverThePassword1!');
  expect(unknown.status()).toBe(401);
  await expect(page.getByRole('alert')).toBeVisible();
});

test('A06/A07: repeated failures are throttled and the login screen remains usable', async ({ page }) => {
  await openLogin(page);
  const syntheticEmail = `module-01-throttle-${Date.now()}@example.test`;
  let limited;
  for (let attempt = 0; attempt < 10; attempt += 1) {
    const response = await submitLogin(page, syntheticEmail, 'WrongPassword123!');
    if (response.status() === 429) {
      limited = response;
      break;
    }
    expect(response.status()).toBe(401);
  }
  expect(limited?.status()).toBe(429);
  await expect(page.getByRole('alert')).toContainText('Too many authentication attempts');

  const recovered = await submitLogin(page, adminEmail, adminPassword);
  expect(recovered.status()).toBe(200);
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
});

test('A04/A05: empty values are rejected and malformed email is rejected by API validation', async ({ page }) => {
  await openLogin(page);
  const email = page.getByLabel('Email address');
  const password = page.locator('input[name="password"]');
  await page.getByRole('button', { name: 'Sign in' }).click();
  expect(await email.evaluate((element: HTMLInputElement) => element.validity.valueMissing)).toBe(true);
  expect(await password.evaluate((element: HTMLInputElement) => element.validity.valueMissing)).toBe(true);

  await email.fill('not-an-email');
  await password.fill('irrelevant');
  const malformed = await submitLogin(page, 'not-an-email', 'irrelevant');
  expect(malformed.status()).toBe(400);
  await expect(page.getByRole('alert')).toBeVisible();
});

test('A13/A14: protected URL requires authentication and player role is denied admin access', async ({ page, request }) => {
  await page.goto('/players');
  await expect(page).toHaveURL(/\/login$/);

  const phone = process.env.BINGO_E2E_PRIMARY_PHONE ?? '';
  const pin = process.env.BINGO_E2E_PRIMARY_PASSWORD ?? '';
  test.skip(!phone || !pin, 'QA player credentials are not available in the ignored local environment.');
  const login = await request.post(`${api}/api/v1/auth/login`, {
    data: { phoneNumber: phone, password: pin },
  });
  expect(login.status()).toBe(200);
  const auth = await login.json();
  await page.evaluate((value) => localStorage.setItem('bingo-admin-session', JSON.stringify({
    accessToken: value.accessToken,
    tokenType: value.tokenType,
    user: value.user,
  })), auth);
  await page.goto('/dashboard');
  await expect(page.getByText('Access denied')).toBeVisible();
});

test('A15: login network failure is surfaced and the same screen recovers', async ({ page }) => {
  await openLogin(page);
  await page.route('**/api/v1/admin/login', async (route) => route.abort('failed'));
  await page.getByLabel('Email address').fill(adminEmail);
  await page.locator('input[name="password"]').fill(adminPassword);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await page.unroute('**/api/v1/admin/login');

  const response = await submitLogin(page, adminEmail, adminPassword);
  expect(response.status()).toBe(200);
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
});

test('A12: invalid admin token returns the browser to login', async ({ page }) => {
  await page.goto('/login');
  await page.evaluate(() => localStorage.setItem('bingo-admin-session', JSON.stringify({
    accessToken: 'not-a-valid-jwt',
    tokenType: 'Bearer',
    user: { id: 1, displayName: 'QA Admin', roles: ['ADMIN'], status: 'ACTIVE' },
  })));
  const protectedResponse = page.waitForResponse((response) =>
    response.url().includes('/api/v1/admin/') && response.status() === 401,
  );
  await page.goto('/dashboard');
  expect((await protectedResponse).status()).toBe(401);
  await expect(page).toHaveURL(/\/login$/);
  expect(await page.evaluate(() => localStorage.getItem('bingo-admin-session'))).toBeNull();
});
