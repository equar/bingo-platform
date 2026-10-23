import { randomBytes, randomInt } from 'node:crypto';
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { expect, test, type Page } from '@playwright/test';
import { loginAdmin, qaBaseUrls, requiredQaEnv } from './support/qaHelpers';

const evidenceDir = path.resolve(process.cwd(), '../docs/qa/evidence');
const mailboxPath = path.resolve(process.cwd(), '../.tmp/smtp-catcher-m02b.txt');
const credentialsPath = path.resolve(process.cwd(), '../.tmp/module-02b-finance-e2e-credentials.json');

interface FinanceTestUser {
  id?: number;
  email: string;
  phone: string;
  name: string;
  password: string;
}

test('manual transfer is independently verified, separately approved, and posted once', async ({ browser, page }) => {
  const backend = qaBaseUrls().backend;
  const adminUrl = qaBaseUrls().admin;
  const reference = requiredQaEnv('BINGO_M02B_REFERENCE');
  const amount = requiredQaEnv('BINGO_M02B_AMOUNT');
  const rawAgentPhone = requiredQaEnv('BINGO_M02B_AGENT_PHONE');
  const agentPhone = rawAgentPhone.startsWith('0') ? `251${rawAgentPhone.slice(1)}` : rawAgentPhone;
  const accountId = Number(requiredQaEnv('BINGO_M02B_RECEIVING_ACCOUNT_ID'));
  const adminEmail = requiredQaEnv('BINGO_E2E_ADMIN_EMAIL');
  const adminPassword = requiredQaEnv('BINGO_E2E_ADMIN_PASSWORD');
  const suffix = `${Date.now()}-${randomBytes(3).toString('hex')}`;
  const verifier: FinanceTestUser = process.env.BINGO_M02B_VERIFIER_EMAIL
    ? {
      id: Number(requiredQaEnv('BINGO_M02B_VERIFIER_ID')),
      email: requiredQaEnv('BINGO_M02B_VERIFIER_EMAIL'),
      phone: requiredQaEnv('BINGO_M02B_VERIFIER_PHONE'),
      name: `QA M02B Finance Verifier ${suffix}`,
      password: randomBytes(24).toString('base64url'),
    }
    : {
      email: `qa-m02b-verifier-${suffix}@example.test`,
      phone: `0911${String(randomInt(0, 1_000_000)).padStart(6, '0')}`,
      name: `QA M02B Finance Verifier ${suffix}`,
      password: randomBytes(24).toString('base64url'),
    };
  const approver: FinanceTestUser = process.env.BINGO_M02B_APPROVER_EMAIL
    ? {
      id: Number(requiredQaEnv('BINGO_M02B_APPROVER_ID')),
      email: requiredQaEnv('BINGO_M02B_APPROVER_EMAIL'),
      phone: requiredQaEnv('BINGO_M02B_APPROVER_PHONE'),
      name: `QA M02B Finance Approver ${suffix}`,
      password: randomBytes(24).toString('base64url'),
    }
    : {
      email: `qa-m02b-approver-${suffix}@example.test`,
      phone: `0911${String(randomInt(0, 1_000_000)).padStart(6, '0')}`,
      name: `QA M02B Finance Approver ${suffix}`,
      password: randomBytes(24).toString('base64url'),
    };
  await writeFile(credentialsPath, JSON.stringify({ verifier, approver }, null, 2), 'utf8');

  await loginAdmin(page);
  const adminToken = await page.evaluate(() =>
    (JSON.parse(localStorage.getItem('bingo-admin-session') ?? '{}') as { accessToken: string }).accessToken,
  );
  expect(adminToken).toBeTruthy();
  const findRequest = async (status: string) => {
    const response = await page.request.get(
      `${backend}/api/v1/admin/agents/float-top-up-requests?status=${status}&query=${encodeURIComponent(agentPhone)}`,
      { headers: { Authorization: `Bearer ${adminToken}` } },
    );
    expect(response.status()).toBe(200);
    const result = await response.json() as { content: Array<Record<string, unknown>> };
    return result.content.find((item) => item.transferReference === reference);
  };
  const queuedRequest = await findRequest('PENDING')
    ?? await findRequest('VERIFIED')
    ?? await findRequest('APPROVED');
  expect(queuedRequest).toBeTruthy();
  const requestId = Number(queuedRequest!.id);
  let currentStatus = String(queuedRequest!.status);

  const createFinanceUser = async (user: FinanceTestUser, role: 'FINANCE_VERIFIER' | 'FINANCE_APPROVER') => {
    let userId = user.id;
    if (userId) {
      await page.goto('/admin-users');
      await page.getByLabel('Search by name, email, or phone').fill(user.email);
      const existingRow = page.getByRole('row').filter({ hasText: user.email });
      await expect(existingRow).toBeVisible();
      await existingRow.getByRole('button', { name: 'Edit' }).click();
      await page.getByLabel('Display name').fill(user.name);
      await page.getByRole('dialog').getByRole('textbox', { name: 'Email', exact: true }).fill(user.email);
      await page.getByLabel('Phone number').fill(user.phone);
      await page.getByLabel('Password (leave blank to keep current)').fill(user.password);
      const updatedResponse = page.waitForResponse((response) =>
        response.url().endsWith(`/api/v1/admin/users/${userId}`) && response.request().method() === 'PUT');
      await page.getByRole('button', { name: 'Save changes' }).click();
      expect((await updatedResponse).status()).toBe(200);
    } else {
      await page.goto('/admin-users');
      await page.getByRole('button', { name: 'Create admin user' }).click();
      await page.getByLabel('Display name').fill(user.name);
      await page.getByRole('dialog').getByRole('textbox', { name: 'Email', exact: true }).fill(user.email);
      await page.getByLabel('Phone number').fill(user.phone);
      const createdResponse = page.waitForResponse((response) =>
        response.url().endsWith('/api/v1/admin/users') && response.request().method() === 'POST');
      await page.getByRole('button', { name: 'Create user', exact: true }).click();
      const created = await (await createdResponse).json() as { id: number; status: string };
      expect(created.status).toBe('ACTIVE');
      userId = created.id;
    }

    if (!user.id) {
      const mailbox = await readFile(mailboxPath, 'utf8');
      const message = mailbox.split('---MESSAGE-END---').find((part) => part.includes(user.email));
      expect(message, `Activation message for ${user.email} is delivered to the local QA catcher`).toBeTruthy();
      const token = message!.match(/#token=([A-Za-z0-9_-]+)/)?.[1];
      expect(token).toBeTruthy();

      const activation = await page.request.post(`${backend}/api/v1/admin/activate`, {
        data: { token, newPassword: user.password },
      });
      expect(activation.status()).toBe(200);
      await writeFile(mailboxPath, '', 'utf8');
    }

    const assignment = await page.request.post(`${backend}/api/v1/admin/users/${userId}/finance-roles`, {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: { roles: [role] },
    });
    expect(assignment.status()).toBe(200);
    return userId!;
  };

  const verifierId = await createFinanceUser(verifier, 'FINANCE_VERIFIER');
  const approverId = await createFinanceUser(approver, 'FINANCE_APPROVER');
  expect(verifierId).not.toBe(approverId);

  const signInAs = async (identity: FinanceTestUser): Promise<{ page: Page; context: Awaited<ReturnType<typeof browser.newContext>> }> => {
    const context = await browser.newContext();
    const financePage = await context.newPage();
    await financePage.goto(`${adminUrl}/login`);
    await financePage.getByLabel('Email address').fill(identity.email);
    await financePage.locator('input[name="password"]').fill(identity.password);
    await financePage.getByRole('button', { name: 'Sign in' }).click();
    await expect(financePage.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
    return { page: financePage, context };
  };

  const verifierSession = await signInAs(verifier);
  const reviewerPage = verifierSession.page;
  await reviewerPage.goto('/agents');
  await expect(reviewerPage.getByRole('heading', { name: 'Float top-up review' })).toBeVisible();
  if (currentStatus !== 'PENDING') {
    await reviewerPage.getByLabel('Request status').click();
    await reviewerPage.getByRole('option', { name: currentStatus }).click();
  }
  await reviewerPage.getByLabel('Search by agent or phone').fill(agentPhone);
  const reviewRow = reviewerPage.getByRole('row').filter({ hasText: reference });
  await expect(reviewRow).toBeVisible();
  await expect(reviewRow).toContainText(currentStatus);
  await expect(reviewRow).toContainText(new RegExp(`${Number(amount)}(?:0)? ETB`));
  await reviewRow.getByRole('button', { name: 'Details' }).click();
  await reviewerPage.getByLabel('Review note').fill('Verified against synthetic independent QA statement.');
  if (currentStatus === 'VERIFIED') {
    await expect(reviewerPage.getByRole('button', { name: 'Post verified request (QA simulation)' })).toBeEnabled();
  } else {
    await expect(reviewerPage.getByRole('button', { name: 'Post verified request (QA simulation)' })).toBeDisabled();
  }

  let statementEntryId = 0;
  if (currentStatus === 'PENDING') {
    await reviewerPage.getByLabel('Receiving account').click();
    await reviewerPage.getByRole('option').filter({ hasText: 'QA Synthetic Bank' }).click();
    await reviewerPage.getByLabel('Bank transaction reference').fill(reference);
    await reviewerPage.getByLabel('Amount ETB').fill(amount);
    await reviewerPage.getByRole('button', { name: 'Record synthetic statement entry' }).click();
    const statementEntry = reviewerPage.getByText(/Synthetic statement entry #\d+/);
    await expect(statementEntry).toBeVisible();
    statementEntryId = Number((await statementEntry.textContent())?.match(/#(\d+)/)?.[1]);
    expect(statementEntryId).toBeGreaterThan(0);
    await reviewerPage.getByLabel('Synthetic statement entry ID').fill(String(statementEntryId));
    const verifyResponsePromise = reviewerPage.waitForResponse((response) =>
      response.url().includes('/verify') && response.request().method() === 'POST');
    await reviewerPage.getByRole('button', { name: 'Match independent statement entry' }).click();
    const verifyResponse = await verifyResponsePromise;
    expect(verifyResponse.status()).toBe(200);
    currentStatus = 'VERIFIED';
  } else {
    const verifierToken = await reviewerPage.evaluate(() =>
      (JSON.parse(localStorage.getItem('bingo-admin-session') ?? '{}') as { accessToken: string }).accessToken,
    );
    const historyResponse = await reviewerPage.request.get(
      `${backend}/api/v1/admin/agents/float-top-up-requests/${requestId}/verifications`,
      { headers: { Authorization: `Bearer ${verifierToken}` } },
    );
    expect(historyResponse.status()).toBe(200);
    const history = await historyResponse.json() as Array<{ outcome: string; statementEntryId: number | null }>;
    statementEntryId = Number(history.find((item) => item.outcome === 'MATCHED')?.statementEntryId);
    expect(statementEntryId).toBeGreaterThan(0);
  }
  if (currentStatus === 'VERIFIED') {
    await expect(reviewerPage.getByRole('button', { name: 'Post verified request (QA simulation)' })).toBeEnabled();
  } else {
    await expect(reviewerPage.getByRole('button', { name: 'Post verified request (QA simulation)' })).toBeDisabled();
  }
  await expect(reviewerPage.getByText(/MATCHED · SYNTHETIC_QA_BANK_STATEMENT/)).toBeVisible();
  await mkdir(evidenceDir, { recursive: true });
  await reviewerPage.screenshot({ path: path.join(evidenceDir, 'module-02b-funding-verified.png'), fullPage: true });

  const forbiddenPost = await reviewerPage.request.post(
    `${backend}/api/v1/admin/agents/float-top-up-requests/${requestId}/post`,
    { headers: { Authorization: `Bearer ${await reviewerPage.evaluate(() => (JSON.parse(localStorage.getItem('bingo-admin-session') ?? '{}') as { accessToken: string }).accessToken)}` }, data: { approve: true, reviewNote: 'Verifier may not approve own verification' } },
  );
  expect(forbiddenPost.status()).toBe(403);
  if (currentStatus === 'VERIFIED') {
    await expect(reviewerPage.getByRole('button', { name: 'Post verified request (QA simulation)' })).toBeEnabled();
  }
  await verifierSession.context.close();

  const approverSession = await signInAs(approver);
  const approverPage = approverSession.page;
  await approverPage.goto('/agents');
  await expect(approverPage.getByRole('heading', { name: 'Float top-up review' })).toBeVisible();
  await approverPage.getByLabel('Request status').click();
  await approverPage.getByRole('option', { name: currentStatus }).click();
  await approverPage.getByLabel('Search by agent or phone').fill(agentPhone);
  const verifiedRow = approverPage.getByRole('row').filter({ hasText: reference });
  await expect(verifiedRow).toBeVisible();
  await verifiedRow.getByRole('button', { name: 'Details' }).click();
  await approverPage.getByLabel('Review note').fill('Separate approver confirmed the verifier history.');
  let posted: { journalId: number; availableBalance: string; simulated: boolean };
  if (currentStatus === 'VERIFIED') {
    const postResponsePromise = approverPage.waitForResponse((response) =>
      response.url().endsWith(`/api/v1/admin/agents/float-top-up-requests/${requestId}/post`) && response.request().method() === 'POST');
    await approverPage.getByRole('button', { name: 'Post verified request (QA simulation)' }).click();
    const postResponse = await postResponsePromise;
    expect(postResponse.status()).toBe(200);
    posted = await postResponse.json() as typeof posted;
    await approverPage.reload();
    await expect(approverPage.getByRole('heading', { name: 'Float top-up review' })).toBeVisible();
    await approverPage.getByLabel('Request status').click();
    await approverPage.getByRole('option', { name: 'APPROVED' }).click();
    await approverPage.getByLabel('Search by agent or phone').fill(agentPhone);
    await expect(approverPage.getByRole('row').filter({ hasText: reference })).toContainText('APPROVED');
  } else {
    const alreadyPostedResponse = await approverPage.request.post(
      `${backend}/api/v1/admin/agents/float-top-up-requests/${requestId}/post`,
      { headers: { Authorization: `Bearer ${await approverPage.evaluate(() => (JSON.parse(localStorage.getItem('bingo-admin-session') ?? '{}') as { accessToken: string }).accessToken)}` }, data: { approve: true, reviewNote: 'Repeat the already posted QA request idempotently.' } },
    );
    expect(alreadyPostedResponse.status()).toBe(200);
    posted = await alreadyPostedResponse.json() as typeof posted;
    await expect(approverPage.getByRole('button', { name: 'Post verified request (QA simulation)' })).toBeDisabled();
  }
  expect(posted.simulated).toBe(true);
  expect(Number(posted.availableBalance)).toBeGreaterThan(0);

  const approverToken = await approverPage.evaluate(() =>
    (JSON.parse(localStorage.getItem('bingo-admin-session') ?? '{}') as { accessToken: string }).accessToken,
  );
  const repeatedPost = await approverPage.request.post(`${backend}/api/v1/admin/agents/float-top-up-requests/${requestId}/post`, {
    headers: { Authorization: `Bearer ${approverToken}` },
    data: { approve: true, reviewNote: 'Idempotent repeated QA posting attempt.' },
  });
  expect(repeatedPost.status()).toBe(200);
  const replay = await repeatedPost.json() as { journalId: number; availableBalance: string };
  expect(replay.journalId).toBe(posted.journalId);
  expect(replay.availableBalance).toBe(posted.availableBalance);

  await approverPage.getByLabel('Statement date').fill(new Date().toISOString().slice(0, 10));
  await approverPage.getByRole('button', { name: 'Run read-only reconciliation' }).click();
  await expect(approverPage.getByText(/SIMULATED QA/).first()).toBeVisible();
  await expect(approverPage.getByText(/debit .* \/ credit .*/).first()).toBeVisible();
  const reconciliationResponse = await approverPage.request.get(
    `${backend}/api/v1/admin/float-reconciliation/daily?date=${new Date().toISOString().slice(0, 10)}`,
    { headers: { Authorization: `Bearer ${approverToken}` } },
  );
  expect(reconciliationResponse.status()).toBe(200);
  const reconciliation = await reconciliationResponse.json() as { journalBalanced: boolean; syntheticQaData: boolean };
  expect(reconciliation.journalBalanced).toBe(true);
  expect(reconciliation.syntheticQaData).toBe(true);
  await approverPage.screenshot({ path: path.join(evidenceDir, 'module-02b-funding-posted.png'), fullPage: true });
  await writeFile(path.join(evidenceDir, 'module-02b-funding-browser.json'), JSON.stringify({
    reference, amount, agentPhone, requestId, statementEntryId, verifierUserId: verifierId,
    approverUserId: approverId, journalId: posted.journalId, approvedBalance: posted.availableBalance,
    repeatedPostJournalId: replay.journalId, distinctReviewerAndApprover: verifierId !== approverId,
    qaSimulationOnly: posted.simulated,
  }, null, 2) + '\n', 'utf8');
  await rm(credentialsPath, { force: true });
  await approverSession.context.close();
});
