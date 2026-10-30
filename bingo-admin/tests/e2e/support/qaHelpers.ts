import { expect, type APIResponse, type Page } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';

const runtimeEnv = (globalThis as any).process?.env ?? {};

export function requiredQaEnv(name: string): string {
  const value = runtimeEnv[name];
  if (typeof value !== 'string' || !value.trim()) {
    throw new Error(`${name} must be set for this QA action`);
  }
  return value;
}

export function qaBaseUrls() {
  return {
    admin: runtimeEnv.BINGO_E2E_ADMIN_URL ?? 'http://localhost:5174',
    website: runtimeEnv.BINGO_E2E_WEBSITE_URL ?? 'http://localhost:4321',
    backend: runtimeEnv.BINGO_E2E_BACKEND_URL ?? 'http://localhost:8081',
  };
}

export async function navigateTo(page: Page, route: string, baseUrl?: string) {
  const destination = new URL(route, baseUrl ?? qaBaseUrls().admin).toString();
  return page.goto(destination);
}

export async function loginAdmin(page: Page) {
  await navigateTo(page, '/login');
  await page.getByLabel('Email address').fill(requiredQaEnv('BINGO_E2E_ADMIN_EMAIL'));
  await page.locator('input[name="password"]').fill(requiredQaEnv('BINGO_E2E_ADMIN_PASSWORD'));
  const loginResponse = page.waitForResponse(
    (response) => response.url().endsWith('/api/v1/admin/login') && response.request().method() === 'POST',
    { timeout: 30_000 },
  );
  await page.getByRole('button', { name: 'Sign in' }).click();
  try {
    const response = await loginResponse;
    expect(response.ok(), `Admin login API returned ${response.status()}`).toBeTruthy();
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible({ timeout: 15_000 });
  } catch (error) {
    const passwordField = page.locator('input[name="password"]');
    const emailField = page.getByLabel('Email address');
    if (await passwordField.count() > 0) await passwordField.fill('');
    if (await emailField.count() > 0) await emailField.fill('');
    throw error;
  }
}

export async function captureScreenshot(page: Page, name: string): Promise<string> {
  const safeName = name.replace(/[^a-zA-Z0-9_-]+/g, '-');
  const destination = path.resolve(
    runtimeEnv.BINGO_E2E_RESULTS_DIR ?? path.join(process.cwd(), 'test-results', 'qa'),
    `${safeName}.png`,
  );
  await mkdir(path.dirname(destination), { recursive: true });
  await page.screenshot({ path: destination, fullPage: true });
  return destination;
}

export async function verifyApiResponse(response: APIResponse, context: string) {
  if (!response.ok()) {
    throw new Error(`${context} returned HTTP ${response.status()}`);
  }
  expect(response.ok(), `${context} should return a successful response`).toBeTruthy();
}

export async function reportFailure(page: Page, context: string, error: unknown) {
  const screenshot = await captureScreenshot(page, `failure-${context}`);
  const message = error instanceof Error ? error.message : String(error);
  const reportPath = screenshot.replace(/\.png$/, '.txt');
  await writeFile(reportPath, `${context}\n${message}\nScreenshot: ${screenshot}\n`, 'utf8');
  return { screenshot, reportPath };
}
