import { expect, test, type Page } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';

const runtimeEnv = (globalThis as any)['process']?.env ?? {};
const backendBaseUrl = runtimeEnv.BINGO_E2E_BACKEND_URL;
const adminEmail = runtimeEnv.BINGO_E2E_ADMIN_EMAIL;
const adminPassword = runtimeEnv.BINGO_E2E_ADMIN_PASSWORD;
const runId = runtimeEnv.BINGO_E2E_RUN_ID ?? `QA_M06A5_${new Date().toISOString().replace(/[:.]/g, '-')}`;
const manifestPath = runtimeEnv.BINGO_E2E_MANIFEST_PATH;

function requireSetting(value: unknown, name: string): string {
  if (typeof value !== 'string' || !value.trim()) {
    throw new Error(`${name} must be set in the normal local QA terminal`);
  }
  return value;
}

async function login(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Email address').fill(requireSetting(adminEmail, 'BINGO_E2E_ADMIN_EMAIL'));
  await page.locator('input[name="password"]').fill(requireSetting(adminPassword, 'BINGO_E2E_ADMIN_PASSWORD'));
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
}

function localDateTimeAfter(minutes: number) {
  const date = new Date(Date.now() + minutes * 60_000);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}

test('Module 06A-5 schedules and transitions one new isolated QA session', async ({ page }) => {
  const backend = new URL(requireSetting(backendBaseUrl, 'BINGO_E2E_BACKEND_URL'));
  expect(backend.hostname).toBe('127.0.0.1');
  expect(backend.port).toBe('8082');
  const runManifest = requireSetting(manifestPath, 'BINGO_E2E_MANIFEST_PATH');
  const sessionName = `${runId}_SESSION`;

  await login(page);
  await page.goto('/sessions');
  await expect(page.getByRole('heading', { name: 'Sessions' })).toBeVisible();
  const evidenceBase = runManifest.replace(/\.json$/i, '');
  await page.screenshot({ path: `${evidenceBase}-before.png`, fullPage: true });

  await page.getByRole('button', { name: 'New session' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Room').click();
  const roomOption = page.getByRole('option').filter({ hasText: /^QA\b/ }).first();
  await expect(roomOption, 'an existing active QA room is required; no room is created or modified by this test').toBeVisible();
  const roomName = (await roomOption.innerText()).trim();
  await roomOption.click();

  await dialog.getByLabel('Session name').fill(sessionName);
  await dialog.getByLabel('Scheduled start').fill(localDateTimeAfter(90));
  const createResponsePromise = page.waitForResponse((response) =>
    response.request().method() === 'POST' && /\/api\/v1\/admin\/rooms\/\d+\/sessions$/.test(response.url()),
  );
  await dialog.getByRole('button', { name: 'Create', exact: true }).click();
  const createResponse = await createResponsePromise;
  expect(createResponse.ok(), `session creation returned HTTP ${createResponse.status()}`).toBeTruthy();
  await expect(page).toHaveURL(/\/sessions\/\d+$/);
  await expect(page.getByText(sessionName)).toBeVisible();
  const sessionId = Number(new URL(page.url()).pathname.split('/').pop());
  expect(Number.isInteger(sessionId)).toBeTruthy();

  const record: {
    runId: string;
    backendBaseUrl: string;
    sessionId: number;
    sessionName: string;
    roomName: string;
    scheduledStartOffsetMinutes: number;
    lastObservedState: string;
    createdAt: string;
    completedAt?: string;
  } = {
    runId,
    backendBaseUrl: `${backend.origin}`,
    sessionId,
    sessionName,
    roomName,
    scheduledStartOffsetMinutes: 90,
    lastObservedState: 'OPEN',
    createdAt: new Date().toISOString(),
  };
  await mkdir(path.dirname(runManifest), { recursive: true });
  await writeFile(runManifest, `${JSON.stringify(record, null, 2)}\n`, 'utf8');

  await page.goto('/sessions');
  const row = page.getByRole('row').filter({ hasText: sessionName });
  await expect(row).toHaveCount(1);
  await row.getByRole('button', { name: 'STARTED', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('button', { name: 'Confirm', exact: true }).click();
  await expect(row.getByText('STARTED')).toBeVisible();
  record.lastObservedState = 'STARTED';
  await writeFile(runManifest, `${JSON.stringify(record, null, 2)}\n`, 'utf8');

  await row.getByRole('button', { name: 'CANCELLED', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('button', { name: 'Confirm', exact: true }).click();
  await expect(row.getByText('CANCELLED')).toBeVisible();
  record.lastObservedState = 'CANCELLED';
  record.completedAt = new Date().toISOString();
  await writeFile(runManifest, `${JSON.stringify(record, null, 2)}\n`, 'utf8');
  await page.screenshot({ path: `${evidenceBase}-after.png`, fullPage: true });
});
