import { createHmac } from 'node:crypto';
import { expect, test } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';

const adminEmail = process.env.BINGO_E2E_ADMIN_EMAIL ?? '';
const adminPassword = process.env.BINGO_E2E_ADMIN_PASSWORD ?? '';

test.use({ screenshot: 'only-on-failure', trace: 'on' });

test('A11: a real expired signed JWT clears the browser session and requires a fresh login', async ({ page }, testInfo) => {
  const evidenceDirectory = path.resolve(process.cwd(), '../docs/qa/evidence');
  await mkdir(evidenceDirectory, { recursive: true });
  const jwtSecret = process.env.BINGO_QA_JWT_SECRET;
  expect(jwtSecret, 'QA JWT secret must be loaded from the ignored local environment').toBeTruthy();
  await page.goto('/login');
  await expect(page.getByLabel('Email address')).toBeVisible();
  await page.getByLabel('Email address').fill(adminEmail);
  await page.locator('input[name="password"]').fill(adminPassword);
  const loginResponsePromise = page.waitForResponse((response) => response.url().endsWith('/api/v1/admin/login'));
  await page.getByRole('button', { name: 'Sign in' }).click();
  const loginResponse = await loginResponsePromise;
  expect(loginResponse.status()).toBe(200);
  const auth = await loginResponse.json();
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  await page.waitForLoadState('networkidle');
  const originalPayload = JSON.parse(Buffer.from(auth.accessToken.split('.')[1], 'base64url').toString('utf8'));
  expect(originalPayload.roles).toContain('ADMIN');
  expect(originalPayload.sub).toBeTruthy();

  const nowSeconds = Math.floor(Date.now() / 1000);
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  const unsigned = `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({
    ...originalPayload,
    iat: nowSeconds - 3600,
    exp: nowSeconds - 120,
  })}`;
  const expiredToken = `${unsigned}.${createHmac('sha256', jwtSecret!).update(unsigned).digest('base64url')}`;

  const protectedRequests: string[] = [];
  page.on('request', (request) => {
    if (request.url().includes('/api/v1/admin/')) protectedRequests.push(new URL(request.url()).pathname);
  });
  await page.evaluate(({ token, user }) => localStorage.setItem('bingo-admin-session', JSON.stringify({
    accessToken: token,
    tokenType: 'Bearer',
    user,
  })), { token: expiredToken, user: auth.user });

  const unauthorized = page.waitForResponse((response) =>
    response.url().includes('/api/v1/admin/players') && response.status() === 401,
  );
  await page.goto('/players');
  const response = await unauthorized;
  expect(response.status()).toBe(401);
  const responseBody = await response.json();
  expect(responseBody.status).toBe(401);
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByLabel('Email address')).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem('bingo-admin-session'))).toBeNull();
  await expect(page.getByRole('heading', { name: 'Players' })).toHaveCount(0);
  await page.screenshot({ path: path.join(evidenceDirectory, 'module-01-admin-expired-session.png'), fullPage: true });

  await page.reload();
  await expect(page).toHaveURL(/\/login$/);
  await page.goto('/players');
  await expect(page).toHaveURL(/\/login$/);
  const requestsBeforeReauthentication = [...protectedRequests];
  expect(protectedRequests).toHaveLength(1);
  expect(requestsBeforeReauthentication).toEqual(['/api/v1/admin/players']);
  await expect(page.getByLabel('Email address')).toBeVisible();

  await page.getByLabel('Email address').fill(adminEmail);
  await page.locator('input[name="password"]').fill(adminPassword);
  const freshLoginPromise = page.waitForResponse((r) => r.url().endsWith('/api/v1/admin/login'));
  await page.getByRole('button', { name: 'Sign in' }).click();
  const freshLogin = await freshLoginPromise;
  expect(freshLogin.status()).toBe(200);
  await expect(page.getByRole('heading', { name: 'Players', exact: true })).toBeVisible();
  await page.screenshot({ path: path.join(evidenceDirectory, 'module-01-admin-reauthenticated.png'), fullPage: true });

  const apiEvidence = {
      protectedRequestPathsBeforeReauthentication: requestsBeforeReauthentication,
      expiredTokenResponse: {
        status: response.status(),
        apiPath: new URL(response.url()).pathname,
        bodyStatus: responseBody.status,
      },
      sessionCleared: true,
      refreshStayedAtLogin: true,
      reauthenticated: freshLogin.status() === 200,
  };
  const apiEvidencePath = path.join(evidenceDirectory, 'module-01-admin-expired-jwt-api.json');
  await writeFile(apiEvidencePath, JSON.stringify(apiEvidence, null, 2), 'utf8');
  await testInfo.attach('a11-api-response-evidence', { path: apiEvidencePath, contentType: 'application/json' });
});
