import { expect, test } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { loginAdmin, qaBaseUrls } from './support/qaHelpers';

const evidenceDir = path.resolve(process.cwd(), '../docs/qa/evidence');
const rewardsPath = '/api/v1/admin/wallet/signup-rewards';

test('Module 03D admin can review signup reward settings and history without changing policy', async ({ page, request }) => {
  const unauthorizedSettings = await request.get(`${qaBaseUrls().backend}${rewardsPath}/settings`);
  expect(unauthorizedSettings.status()).toBe(401);

  const responses: Array<{ path: string; status: number }> = [];
  page.on('response', (response) => {
    if (response.url().includes(rewardsPath)) {
      responses.push({ path: new URL(response.url()).pathname, status: response.status() });
    }
  });

  await loginAdmin(page);
  const currentResponsePromise = page.waitForResponse((response) =>
    response.url().endsWith(`${rewardsPath}/settings`) && response.request().method() === 'GET');
  const historyResponsePromise = page.waitForResponse((response) =>
    response.url().includes(`${rewardsPath}/settings/history`) && response.request().method() === 'GET');
  const grantsResponsePromise = page.waitForResponse((response) =>
    response.url().includes(`${rewardsPath}/grants`) && response.request().method() === 'GET');
  await page.goto('/wallet/signup-reward');
  await expect(page.getByRole('heading', { name: 'Signup reward' })).toBeVisible();
  await expect(page.getByLabel('Reward amount')).toHaveValue('0');
  await expect(page.getByLabel('Currency')).toHaveValue('ETB');
  await expect(page.getByText('Disabled', { exact: true })).toBeVisible();
  await expect(page.getByText('No active reward policy is currently in effect.')).toBeVisible();
  await expect(page.getByText('Reward history')).toBeVisible();
  await expect(page.getByText('Recent grants')).toBeVisible();

  const [currentResponse, historyResponse, grantsResponse] = await Promise.all([
    currentResponsePromise,
    historyResponsePromise,
    grantsResponsePromise,
  ]);
  expect(currentResponse.status()).toBe(200);
  expect(historyResponse.status()).toBe(200);
  expect(grantsResponse.status()).toBe(200);
  const beforeRefresh = await currentResponse.json();
  const history = await historyResponse.json();
  const grants = await grantsResponse.json();
  expect(beforeRefresh.enabled).toBe(false);
  expect(Number(beforeRefresh.rewardAmount)).toBe(0);
  expect(beforeRefresh.currency).toBe('ETB');
  expect(beforeRefresh.activeNow).toBe(false);
  await mkdir(evidenceDir, { recursive: true });
  const reloadResponsePromise = page.waitForResponse((response) =>
    response.url().endsWith(`${rewardsPath}/settings`) && response.request().method() === 'GET');
  await page.reload();
  const reloadResponse = await reloadResponsePromise;
  expect(reloadResponse.status()).toBe(200);
  const reloadedPolicy = await reloadResponse.json();
  expect(reloadedPolicy.id).toBe(beforeRefresh.id);
  expect(reloadedPolicy.enabled).toBe(false);
  expect(Number(reloadedPolicy.rewardAmount)).toBe(0);
  await expect(page.getByRole('heading', { name: 'Signup reward' })).toBeVisible();
  await expect(page.getByLabel('Reward amount')).toHaveValue('0');
  await expect(page.getByLabel('Currency')).toHaveValue('ETB');
  await expect(page.getByText('Disabled', { exact: true })).toBeVisible();

  expect(history.content.length).toBeGreaterThan(0);
  expect(history.content.every((entry: { enabled: boolean; rewardAmount: string; currency: string }) =>
    entry.enabled === false && Number(entry.rewardAmount) === 0 && entry.currency === 'ETB')).toBeTruthy();

  expect(grants.content).toHaveLength(0);
  const granted = responses.some((response) => response.path.endsWith('/grants') && response.status === 200);
  const settings = responses.some((response) => response.path.endsWith('/settings') && response.status === 200);
  expect(settings).toBeTruthy();
  expect(granted).toBeTruthy();

  await page.screenshot({ path: path.join(evidenceDir, 'module-03d-signup-policy-admin-readonly.png'), fullPage: true });
  await writeFile(path.join(evidenceDir, 'module-03d-admin-api-results.json'), JSON.stringify({
    anonymousSettingsHttp: unauthorizedSettings.status(),
    persistedCurrentPolicy: {
      id: beforeRefresh.id,
      enabled: beforeRefresh.enabled,
      rewardAmount: beforeRefresh.rewardAmount,
      currency: beforeRefresh.currency,
      effectiveFrom: beforeRefresh.effectiveFrom,
      effectiveUntil: beforeRefresh.effectiveUntil,
      activeNow: beforeRefresh.activeNow,
      createdAt: beforeRefresh.createdAt,
    },
    configurationHistoryRows: history.totalElements,
    configurationHistoryIsDisabledAndZero: true,
    positivePolicyPosting: 'NOT ATTEMPTED — persistent QA posting has no approval',
    settingsUnchangedAfterReload: true,
    endpointResponses: responses,
  }, null, 2) + '\n', 'utf8');
});
