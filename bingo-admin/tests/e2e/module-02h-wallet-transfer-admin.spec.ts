import { execFileSync } from 'node:child_process';
import { mkdir, readFileSync } from 'node:fs';
import path from 'node:path';
import { expect, test, type Page } from '@playwright/test';
import { loginAdmin, qaBaseUrls, requiredQaEnv } from './support/qaHelpers';

const backend = qaBaseUrls().backend;
const evidenceDir = path.resolve(process.cwd(), '../docs/qa/evidence');

function qaEnv(): Record<string, string> {
  const contents = readFileSync(path.resolve(process.cwd(), '../bingo-backend/.env.qa'), 'utf8');
  return Object.fromEntries(contents.split(/\r?\n/).filter(line => line && !line.startsWith('#'))
    .map(line => { const split = line.indexOf('='); return [line.slice(0, split), line.slice(split + 1).trim().replace(/^['"]|['"]$/g, '')]; }));
}

function db(sql: string): string {
  const env = qaEnv();
  return execFileSync('C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe', [
    '--batch', '--skip-column-names', '--host', env.BINGO_QA_MYSQL_HOST, '--port', env.BINGO_QA_MYSQL_PORT,
    '--user', env.BINGO_QA_MYSQL_USERNAME, env.BINGO_QA_MYSQL_DATABASE, '--execute', sql,
  ], { env: { ...process.env, MYSQL_PWD: env.BINGO_QA_MYSQL_PASSWORD }, encoding: 'utf8' }).trim();
}

function persistentSnapshot() {
  const transfers = db('SELECT COUNT(*) FROM wallet_transfers');
  const transferLedgerRows = db("SELECT COUNT(*) FROM wallet_transactions WHERE reference_type='WALLET_TRANSFER'");
  const wallets = db('SELECT GROUP_CONCAT(CONCAT(id, ":", available_balance, ":", promotional_balance) ORDER BY id SEPARATOR ",") FROM wallets');
  const policy = db('SELECT GROUP_CONCAT(CONCAT(id, ":", enabled, ":", currency, ":", max_single_transfer_amount) ORDER BY id SEPARATOR ",") FROM wallet_transfer_policies');
  return { transfers, transferLedgerRows, wallets, policy };
}

async function adminToken(page: Page): Promise<string> {
  return page.evaluate(() => JSON.parse(localStorage.getItem('bingo-admin-session') ?? '{}').accessToken as string);
}

async function capture(page: Page, name: string) {
  await new Promise<void>((resolve, reject) => mkdir(evidenceDir, { recursive: true }, error => error ? reject(error) : resolve()));
  await page.screenshot({ path: path.join(evidenceDir, `module-02h-${name}.png`), fullPage: true });
}

async function expectNoPersistentChanges(before: ReturnType<typeof persistentSnapshot>) {
  expect(persistentSnapshot()).toEqual(before);
}

test.describe('Module 02H wallet transfer administration', () => {
  test('W01/W08/W09 — admin list, search, status filter and refresh handle the retained empty QA state', async ({ page }) => {
    const before = persistentSnapshot();
    expect(before.transfers).toBe('0');
    expect(before.transferLedgerRows).toBe('0');

    await loginAdmin(page);
    const initialResponsePromise = page.waitForResponse(response => {
      const url = new URL(response.url());
      return url.pathname === '/api/v1/admin/wallet/transfers' && response.request().method() === 'GET';
    });
    await page.goto('/wallet/transfers');
    const initialResponse = await initialResponsePromise;
    expect(initialResponse.status()).toBe(200);
    await expect(page.getByRole('heading', { name: 'Transfer history' })).toBeVisible();
    await expect(page.getByText('No transfers match the current search and filters.')).toBeVisible();

    const search = page.getByRole('textbox', { name: /Search sender, recipient, note, or operation/ });
    const searchResponse = page.waitForResponse(response => {
      const url = new URL(response.url());
      return url.pathname === '/api/v1/admin/wallet/transfers' && url.searchParams.get('query') === 'QA-M02H-REF-NOT-PRESENT';
    });
    await search.fill('QA-M02H-REF-NOT-PRESENT');
    expect((await searchResponse).status()).toBe(200);
    await expect(page.getByText('No transfers match the current search and filters.')).toBeVisible();

    const statusResponse = page.waitForResponse(response => {
      const url = new URL(response.url());
      return url.pathname === '/api/v1/admin/wallet/transfers' && url.searchParams.get('status') === 'COMPLETED';
    });
    await page.getByRole('combobox', { name: 'Status' }).click();
    await page.getByRole('option', { name: 'Completed' }).click();
    expect((await statusResponse).status()).toBe(200);
    await expect(page.getByText('No transfers match the current search and filters.')).toBeVisible();

    const refreshedResponse = page.waitForResponse(response => {
      const url = new URL(response.url());
      return url.pathname === '/api/v1/admin/wallet/transfers' && response.request().method() === 'GET';
    });
    await page.reload();
    expect((await refreshedResponse).status()).toBe(200);
    await expect(page.getByRole('heading', { name: 'Transfer history' })).toBeVisible();
    await expectNoPersistentChanges(before);
    await capture(page, 'empty-transfer-list');
  });

  test('W11/W18 — invalid transfer ID returns a safe controlled API error', async ({ page }) => {
    const before = persistentSnapshot();
    await loginAdmin(page);
    await page.goto('/wallet/transfers');
    await expect(page.getByRole('heading', { name: 'Transfer history' })).toBeVisible();
    const token = await adminToken(page);
    const response = await page.request.get(`${backend}/api/v1/admin/wallet/transfers/9223372036854775807`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(response.status()).toBe(404);
    const error = await response.json() as Record<string, unknown>;
    expect(error.code).toBe('TRANSFER_NOT_FOUND');
    expect(JSON.stringify(error)).not.toMatch(/stacktrace|sqlstate|jdbc|select\s.+from/i);
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Transfer history' })).toBeVisible();
    await expectNoPersistentChanges(before);
    await capture(page, 'invalid-transfer-error');
  });

  test('W12/W13a — player credentials cannot access admin transfer operations', async ({ page }) => {
    const before = persistentSnapshot();
    await loginAdmin(page);
    await page.goto('/wallet/transfers');
    await expect(page.getByRole('heading', { name: 'Transfer history' })).toBeVisible();
    const env = qaEnv();
    const login = await page.request.post(`${backend}/api/v1/auth/login`, {
      data: { phoneNumber: env.BINGO_QA_PLAYER_001_PHONE, password: env.BINGO_QA_PLAYER_001_PIN },
    });
    expect(login.status()).toBe(200);
    const playerToken = (await login.json()).accessToken as string;
    const denied = await page.request.get(`${backend}/api/v1/admin/wallet/transfers`, {
      headers: { Authorization: `Bearer ${playerToken}` },
    });
    expect(denied.status()).toBe(403);
    await page.reload();
    await expectNoPersistentChanges(before);
    await capture(page, 'player-transfer-access-denied');
  });

  test('W12/W13b — agent phone and PIN credentials cannot access admin transfer operations', async ({ page }) => {
    const before = persistentSnapshot();
    await loginAdmin(page);
    await page.goto('/wallet/transfers');
    await expect(page.getByRole('heading', { name: 'Transfer history' })).toBeVisible();
    const env = qaEnv();
    const login = await page.request.post(`${backend}/api/v1/agent/login`, {
      data: { phoneNumber: env.BINGO_QA_APPROVED_AGENT_PHONE, pin: env.BINGO_QA_APPROVED_AGENT_PIN },
    });
    expect(login.status()).toBe(200);
    const agentToken = (await login.json()).accessToken as string;
    const denied = await page.request.get(`${backend}/api/v1/admin/wallet/transfers`, {
      headers: { Authorization: `Bearer ${agentToken}` },
    });
    expect(denied.status()).toBe(403);
    await page.reload();
    await expectNoPersistentChanges(before);
    await capture(page, 'agent-transfer-access-denied');
  });
});
