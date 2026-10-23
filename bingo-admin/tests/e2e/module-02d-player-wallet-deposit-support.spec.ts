import { randomBytes, randomInt } from 'node:crypto';
import { expect, test, type Browser, type BrowserContext, type Page } from '@playwright/test';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { loginAdmin, qaBaseUrls, requiredQaEnv } from './support/qaHelpers';

const evidenceDir = path.resolve(process.cwd(), '../docs/qa/evidence');
const mailboxPath = path.resolve(process.cwd(), '../.tmp/smtp-catcher-m02b.txt');
const api = qaBaseUrls().backend;

type SyntheticPlayer = { id: number; name: string; phone: string; token: string };
type FinanceIdentity = { id: number; email: string; phone: string; password: string };

async function createFinanceIdentity(
  page: Page,
  backend: string,
  adminJwt: string,
  suffix: string,
  role: 'FINANCE_VERIFIER' | 'FINANCE_APPROVER',
): Promise<FinanceIdentity> {
  const identity = {
    email: `qa-m02d-${role.toLowerCase()}-${suffix}@example.test`,
    phone: `0911${String(randomInt(0, 1_000_000)).padStart(6, '0')}`,
    password: randomBytes(24).toString('base64url'),
  };
  await page.goto('/admin-users');
  await page.getByRole('button', { name: 'Create admin user' }).click();
  await page.getByLabel('Display name').fill(`QA M02D ${role} ${suffix}`);
  await page.getByRole('dialog').getByRole('textbox', { name: 'Email', exact: true }).fill(identity.email);
  await page.getByLabel('Phone number').fill(identity.phone);
  const createdResponsePromise = page.waitForResponse(response =>
    response.url().endsWith('/api/v1/admin/users') && response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Create user', exact: true }).click();
  const createdResponse = await createdResponsePromise;
  expect(createdResponse.ok()).toBeTruthy();
  const created = await createdResponse.json() as { id: number; status: string };
  expect(created.status).toBe('ACTIVE');

  const mailbox = await readFile(mailboxPath, 'utf8');
  const message = mailbox.split('---MESSAGE-END---').find(part => part.includes(identity.email));
  expect(message, 'new finance user activation email is captured by the local QA SMTP catcher').toBeTruthy();
  const token = message!.match(/#token=([A-Za-z0-9_-]+)/)?.[1];
  expect(token).toBeTruthy();
  const activation = await page.request.post(`${backend}/api/v1/admin/activate`, {
    data: { token, newPassword: identity.password },
  });
  expect(activation.status()).toBe(200);
  await writeFile(mailboxPath, '', 'utf8');

  const roleAssignment = await page.request.post(`${backend}/api/v1/admin/users/${created.id}/finance-roles`, {
    headers: { Authorization: `Bearer ${adminJwt}` },
    data: { roles: [role] },
  });
  expect(roleAssignment.status()).toBe(200);
  return { ...identity, id: created.id };
}

async function signInFinanceUser(
  browser: Browser,
  adminUrl: string,
  identity: FinanceIdentity,
): Promise<{ context: BrowserContext; page: Page }> {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(`${adminUrl}/login`);
  await page.getByLabel('Email address').fill(identity.email);
  await page.locator('input[name="password"]').fill(identity.password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  return { context, page };
}

async function adminToken(page: Page) {
  return page.evaluate(() => {
    const stored = localStorage.getItem('bingo-admin-session');
    return stored ? (JSON.parse(stored) as { accessToken: string }).accessToken : '';
  });
}

async function registerPlayer(page: Page, name: string, phone: string): Promise<SyntheticPlayer> {
  const response = await page.request.post(`${api}/api/v1/auth/register`, {
    data: { displayName: name, phoneNumber: phone, password: '962401' },
  });
  expect(response.status(), 'synthetic player registration should succeed').toBe(201);
  const body = await response.json();
  return { id: body.user.id, name, phone, token: body.accessToken };
}

async function changePlayerStatus(page: Page, player: SyntheticPlayer, status: string, reason: string) {
  await page.goto('/players');
  await page.getByLabel('Search by name, email, or phone').fill(player.name);
  const link = page.getByRole('link', { name: player.name, exact: true });
  await expect(link).toBeVisible();
  await link.click();
  await expect(page.getByText(`Player #${player.id}`, { exact: false })).toBeVisible();
  await page.getByRole('button', { name: 'Change account status' }).click();
  await page.getByRole('combobox', { name: 'Account status' }).click();
  await page.getByRole('option', { name: status, exact: true }).click();
  await page.getByLabel('Reason').fill(reason);
  const update = page.waitForResponse(response =>
    response.url().endsWith(`/api/v1/admin/players/${player.id}/status`) && response.request().method() === 'PUT');
  await page.getByRole('button', { name: 'Save status' }).click();
  const response = await update;
  expect(response.status()).toBe(200);
  expect((await response.json()).status).toBe(status);
  await expect(page.getByText(status, { exact: true }).first()).toBeVisible();
  await page.reload();
  await expect(page.getByText(status, { exact: true }).first()).toBeVisible();
}

async function setPageSize(page: Page, size: string) {
  await page.getByRole('combobox', { name: 'Rows per page:' }).click();
  await page.getByRole('option', { name: size, exact: true }).click();
}

test('player, maker-checker wallet, verified deposits, and support workflows preserve existing QA records', async ({ browser, page }) => {
  test.setTimeout(300_000);
  const pageErrors: string[] = [];
  const consoleErrors: string[] = [];
  const failedRequests: string[] = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') consoleErrors.push(message.text()); });
  page.on('requestfailed', request => failedRequests.push(`${request.method()} ${request.url()}: ${request.failure()?.errorText ?? 'failed'}`));
  await loginAdmin(page);
  const adminJwt = await adminToken(page);
  const adminBaseUrl = qaBaseUrls().admin;
  const runId = Date.now().toString().slice(-8);
  const players: SyntheticPlayer[] = [];
  for (let index = 0; index < 4; index += 1) {
    const suffix = String((Number(runId) + index) % 100_000_000).padStart(8, '0');
    players.push(await registerPlayer(page, `QA_M02D_PLAYER_00${index + 1}_${runId}`, `2519${suffix}`));
  }
  const [approvalPlayer, suspendedPlayer, depositPlayer, walletPlayer] = players;
  const verifier = await createFinanceIdentity(page, api, adminJwt, runId, 'FINANCE_VERIFIER');
  const approver = await createFinanceIdentity(page, api, adminJwt, runId, 'FINANCE_APPROVER');
  expect(verifier.id).not.toBe(approver.id);
  const verifierSession = await signInFinanceUser(browser, adminBaseUrl, verifier);
  const verifierPage = verifierSession.page;
  const approverSession = await signInFinanceUser(browser, adminBaseUrl, approver);
  const approverPage = approverSession.page;
  const evidence: Record<string, unknown> = { players: players.map(({ id, name }) => ({ id, name })) };
  evidence.financeUsers = { verifierId: verifier.id, approverId: approver.id };

  const anonymousRead = await page.request.get(`${api}/api/v1/admin/players/${approvalPlayer.id}`);
  expect(anonymousRead.status()).toBe(401);

  // The retained and newly created QA players exercise real roster pagination.
  const playerCountResponse = await page.request.get(`${api}/api/v1/admin/players?page=0&size=100`, {
    headers: { Authorization: `Bearer ${adminJwt}` },
  });
  expect(playerCountResponse.ok()).toBeTruthy();
  const totalPlayers = (await playerCountResponse.json()).totalElements as number;
  expect(totalPlayers).toBeGreaterThan(10);
  await page.goto('/players');
  await setPageSize(page, '10');
  await expect(page.locator('tbody tr')).toHaveCount(10);
  await page.getByRole('button', { name: 'Go to next page' }).click();
  const secondPlayerPageRows = Math.min(10, totalPlayers - 10);
  await expect(page.locator('tbody tr')).toHaveCount(secondPlayerPageRows);
  evidence.playerPagination = { totalPlayers, firstPageRows: 10, secondPageRows: secondPlayerPageRows };
  await page.goto('/players');
  await page.getByRole('combobox', { name: 'Status' }).click();
  await page.getByRole('option', { name: 'ACTIVE', exact: true }).click();
  const activePlayerRows = page.locator('tbody tr');
  await expect(activePlayerRows.first()).toBeVisible();
  for (const row of await activePlayerRows.all()) {
    await expect(row.locator('td').nth(2)).toContainText('ACTIVE');
  }

  // Pending approval, enum validation, and restricted session behavior.
  await changePlayerStatus(page, approvalPlayer, 'PENDING', 'Module 02D synthetic approval lifecycle');
  await mkdir(evidenceDir, { recursive: true });
  await page.screenshot({ path: path.join(evidenceDir, 'module-02d-player-pending.png'), fullPage: true });
  const invalidStatus = await page.request.put(`${api}/api/v1/admin/players/${approvalPlayer.id}/status`, {
    headers: { Authorization: `Bearer ${adminJwt}` },
    data: { status: 'NOT_A_STATUS', reason: 'Module 02D invalid enum input' },
  });
  expect(invalidStatus.status()).toBe(400);
  const pendingOldToken = await page.request.get(`${api}/api/v1/wallet/balance`, {
    headers: { Authorization: `Bearer ${approvalPlayer.token}` },
  });
  expect(pendingOldToken.status()).toBe(401);
  const pendingLogin = await page.request.post(`${api}/api/v1/auth/login`, {
    data: { phoneNumber: approvalPlayer.phone, password: '962401' },
  });
  expect(pendingLogin.status()).toBe(401);
  await changePlayerStatus(page, approvalPlayer, 'ACTIVE', 'Module 02D synthetic player approval');
  await page.screenshot({ path: path.join(evidenceDir, 'module-02d-player-approved.png'), fullPage: true });
  evidence.playerStatus = { pending: 200, invalidEnum: 400, restrictedOldToken: 401, restrictedLogin: 401, approved: 200 };

  // A separate synthetic account remains suspended; no retained player is changed.
  await changePlayerStatus(page, suspendedPlayer, 'SUSPENDED', 'Module 02D synthetic restricted account');
  await page.screenshot({ path: path.join(evidenceDir, 'module-02d-player-suspended.png'), fullPage: true });
  const suspendedOldToken = await page.request.get(`${api}/api/v1/wallet/balance`, {
    headers: { Authorization: `Bearer ${suspendedPlayer.token}` },
  });
  expect(suspendedOldToken.status()).toBe(401);
  const suspendedLogin = await page.request.post(`${api}/api/v1/auth/login`, {
    data: { phoneNumber: suspendedPlayer.phone, password: '962401' },
  });
  expect(suspendedLogin.status()).toBe(401);
  evidence.suspendedPlayer = { id: suspendedPlayer.id, oldToken: 401, newLogin: 401 };

  // Player/admin cross-role access and an existing approved agent cannot access admin records.
  const forbiddenPlayerRead = await page.request.get(`${api}/api/v1/admin/players/${walletPlayer.id}`, {
    headers: { Authorization: `Bearer ${approvalPlayer.token}` },
  });
  expect(forbiddenPlayerRead.status()).toBe(403);
  const agentLogin = await page.request.post(`${api}/api/v1/agent/login`, {
    data: {
      phoneNumber: requiredQaEnv('BINGO_QA_APPROVED_AGENT_PHONE'),
      password: requiredQaEnv('BINGO_QA_APPROVED_AGENT_PASSWORD'),
      pin: requiredQaEnv('BINGO_QA_APPROVED_AGENT_PIN'),
    },
  });
  expect(agentLogin.ok()).toBeTruthy();
  const agentJwt = (await agentLogin.json()).accessToken as string;
  const forbiddenAgentRead = await page.request.get(`${api}/api/v1/admin/players/${walletPlayer.id}`, {
    headers: { Authorization: `Bearer ${agentJwt}` },
  });
  expect(forbiddenAgentRead.status()).toBe(403);

  // A verifier submits a maker request; a distinct finance approver posts it once.
  await verifierPage.goto(`/players/${walletPlayer.id}`);
  await expect(verifierPage.getByRole('heading', { name: walletPlayer.name })).toBeVisible();
  await expect(verifierPage.getByText('0 ETB', { exact: true }).first()).toBeVisible();
  await verifierPage.getByRole('button', { name: 'Adjust wallet' }).click();
  await verifierPage.getByLabel('Amount').fill('1.25');
  await verifierPage.getByLabel('Reason').fill('QA-only Module 02D wallet adjustment');
  const adjustmentReason = 'QA-only Module 02D wallet adjustment';
  const adjustmentReference = `QA-M02D-${runId}`;
  await verifierPage.getByLabel('Reference').fill(`QA-M02D-${runId}`);
  await verifierPage.getByRole('button', { name: 'Review', exact: true }).click();
  const adjustmentRequestPromise = verifierPage.waitForResponse(response =>
    response.url().endsWith(`/api/v1/admin/players/${walletPlayer.id}/wallet-adjustments/requests`) &&
    response.request().method() === 'POST', { timeout: 30_000 });
  await verifierPage.getByRole('button', { name: 'Submit request' }).click();
  const adjustmentRequestResponse = await adjustmentRequestPromise;
  expect(adjustmentRequestResponse.status()).toBe(200);
  const adjustmentRequest = await adjustmentRequestResponse.json() as { id: number; status: string };
  expect(adjustmentRequest.status).toBe('PENDING');
  const adjustmentBalanceBeforeReview = await page.request.get(`${api}/api/v1/wallet/balance`, {
    headers: { Authorization: `Bearer ${walletPlayer.token}` },
  });
  expect(Number((await adjustmentBalanceBeforeReview.json()).availableBalance)).toBe(0);

  const legacyAdjustment = await page.request.post(
    `${api}/api/v1/admin/players/${walletPlayer.id}/wallet-adjustments`,
    {
      headers: { Authorization: `Bearer ${adminJwt}`, 'Idempotency-Key': `m02d-legacy-${runId}` },
      data: { amount: 5, currency: 'ETB', reason: 'legacy single-step credit must be blocked' },
    },
  );
  expect(legacyAdjustment.status()).toBe(409);
  const unauthorizedAdjustment = await page.request.post(
    `${api}/api/v1/admin/wallet-adjustment-requests/${adjustmentRequest.id}/approve`,
    {
      headers: { Authorization: `Bearer ${await adminToken(verifierPage)}` },
      data: { reviewNote: 'The maker cannot approve this request' },
    },
  );
  expect(unauthorizedAdjustment.status()).toBe(403);

  await approverPage.goto(`/players/${walletPlayer.id}`);
  await expect(approverPage.getByText(`#${adjustmentRequest.id}: 1.25 ETB · PENDING`)).toBeVisible();
  await approverPage.getByRole('button', { name: 'Approve request' }).click();
  await approverPage.getByLabel('Checker review note').fill('Independent QA checker approval');
  const adjustmentApprovalPromise = approverPage.waitForResponse(response =>
    response.url().endsWith(`/api/v1/admin/wallet-adjustment-requests/${adjustmentRequest.id}/approve`) &&
    response.request().method() === 'POST');
  await approverPage.getByRole('button', { name: 'Approve', exact: true }).click();
  const adjustmentApprovalResponse = await adjustmentApprovalPromise;
  const approverJwt = await adminToken(approverPage);
  const adjustmentApprovalStatus = adjustmentApprovalResponse.status();
  let adjustmentJournalId: number | null = null;
  let adjustmentReplayStatus: number | undefined;
  if (adjustmentApprovalStatus === 200) {
    const adjustment = await adjustmentApprovalResponse.json() as { status: string; journalId: number };
    expect(adjustment.status).toBe('APPROVED');
    adjustmentJournalId = adjustment.journalId;
    const adjustmentReplay = await approverPage.request.post(
      `${api}/api/v1/admin/wallet-adjustment-requests/${adjustmentRequest.id}/approve`,
      {
        headers: { Authorization: `Bearer ${approverJwt}` },
        data: { reviewNote: 'Independent QA checker approval' },
      },
    );
    adjustmentReplayStatus = adjustmentReplay.status();
    expect(adjustmentReplayStatus).toBe(200);
    expect((await adjustmentReplay.json()).journalId).toBe(adjustment.journalId);
    await expect(approverPage.locator('.MuiCard-root').filter({ has: approverPage.getByText('Available balance', { exact: true }) }))
      .toContainText('1.25 ETB');
    await approverPage.reload();
    await expect(approverPage.getByText(`Reason: ${adjustmentReason}`, { exact: true })).toBeVisible();
    await expect(approverPage.getByText(`Supporting reference: ${adjustmentReference}`, { exact: true })).toBeVisible();
    await expect(approverPage.getByText('Checker review note: Independent QA checker approval', { exact: true })).toBeVisible();
    await expect(approverPage.locator('.MuiCard-root').filter({ has: approverPage.getByText('Available balance', { exact: true }) }))
      .toContainText('1.25 ETB');
    await expect(approverPage.getByText(`#${adjustmentRequest.id}: 1.25 ETB · APPROVED`)).toBeVisible();
    await approverPage.getByRole('button', { name: 'View wallet transactions details' }).click();
    await expect(approverPage.getByRole('dialog')).toContainText('WALLET_ADJUSTMENT');
    await expect(approverPage.getByRole('dialog')).toContainText('1.25 ETB');
    await expect(approverPage.getByRole('dialog')).toContainText('Independent QA checker approval');
  } else {
    expect(adjustmentApprovalStatus).toBe(409);
    const adjustmentError = await adjustmentApprovalResponse.json() as { code: string };
    expect(adjustmentError.code).toBe('WALLET_ADJUSTMENT_POSTING_DISABLED');
    await expect(approverPage.getByRole('alert').filter({ hasText: 'Wallet adjustment posting is disabled' }))
      .toBeVisible();
    await approverPage.getByRole('button', { name: 'Cancel' }).click();
    await approverPage.reload();
    await expect(approverPage.getByText(`Reason: ${adjustmentReason}`, { exact: true })).toBeVisible();
    await expect(approverPage.getByText(`Supporting reference: ${adjustmentReference}`, { exact: true })).toBeVisible();
    await expect(approverPage.getByText(`#${adjustmentRequest.id}: 1.25 ETB · PENDING`)).toBeVisible();
    const blockedAdjustmentBalance = await page.request.get(`${api}/api/v1/wallet/balance`, {
      headers: { Authorization: `Bearer ${walletPlayer.token}` },
    });
    expect(Number((await blockedAdjustmentBalance.json()).availableBalance)).toBe(0);
  }
  await approverPage.screenshot({ path: path.join(evidenceDir, 'module-02d-wallet-adjustment.png'), fullPage: true });
  evidence.walletAdjustment = {
    playerId: walletPlayer.id,
    requestId: adjustmentRequest.id,
    status: adjustmentApprovalStatus === 200 ? 'APPROVED' : 'PENDING',
    approvalStatus: adjustmentApprovalStatus,
    journalId: adjustmentJournalId,
    balanceBeforeApproval: 0,
    balanceAfterApproval: adjustmentApprovalStatus === 200 ? '1.25' : '0.00',
    duplicateReplay: adjustmentReplayStatus ?? null,
    legacySingleStep: legacyAdjustment.status(),
    unauthorized: unauthorizedAdjustment.status(),
  };
  // D01-D20 use synthetic local QA evidence, finance-role identities, and real admin Chromium interactions.
  const additionalPlayers: SyntheticPlayer[] = [];
  for (let index = 4; index < 7; index += 1) {
    const suffix = String((Number(runId) + index) % 100_000_000).padStart(8, '0');
    additionalPlayers.push(await registerPlayer(page, `QA_M02D_FINANCE_PLAYER_00${index + 1}_${runId}`, `2519${suffix}`));
  }
  const [mismatchPlayer, rejectionPlayer, concurrentPlayer] = additionalPlayers;
  const verifierJwt = await adminToken(verifierPage);
  const bankAccountResponse = await page.request.post(`${api}/api/v1/admin/float-bank-accounts`, {
    headers: { Authorization: `Bearer ${adminJwt}` },
    data: {
      bankName: `QA M02D Synthetic Bank ${runId}`,
      accountNumber: `QA-M02D-${runId}`,
      accountHolderName: `QA M02D Deposits ${runId}`,
      currency: 'ETB',
    },
  });
  expect(bankAccountResponse.status()).toBe(200);
  const bankAccount = await bankAccountResponse.json() as { id: number };
  const depositEligibility = await page.request.put(
    `${api}/api/v1/admin/float-bank-accounts/${bankAccount.id}/player-deposit-eligibility?enabled=true`,
    { headers: { Authorization: `Bearer ${adminJwt}` } },
  );
  expect(depositEligibility.status()).toBe(200);
  expect((await depositEligibility.json()).playerDepositsEnabled).toBe(true);

  const createSyntheticStatement = async (reference: string, amount: number) => {
    const response = await verifierPage.request.post(`${api}/api/v1/admin/float-bank-statements/synthetic`, {
      headers: { Authorization: `Bearer ${verifierJwt}` },
      data: {
        receivingBankAccountId: bankAccount.id,
        transactionReference: reference,
        amount,
        currency: 'ETB',
        transactionDate: new Date().toISOString().slice(0, 10),
        settlementState: 'SETTLED',
      },
    });
    return response;
  };
  const submitDeposit = async (player: SyntheticPlayer, reference: string, amount: number, key: string) => {
    const response = await page.request.post(`${api}/api/v1/wallet/deposits`, {
      headers: { Authorization: `Bearer ${player.token}`, 'Idempotency-Key': key },
      data: { submittedCashAmount: amount, currency: 'ETB', telebirrReference: reference },
    });
    expect(response.status()).toBe(201);
    const claim = await response.json() as { id: number; normalizedTelebirrReference: string; status: string };
    expect(claim.status).toBe('PENDING');
    return claim;
  };
  const verifyInBrowser = async (reviewerPage: Page, claimId: number, reference: string, statementEntryId: number) => {
    await reviewerPage.goto('/deposits');
    await reviewerPage.getByLabel('Search deposit, player, payment reference, or operation').fill(reference);
    const row = reviewerPage.getByTestId(`deposit-row-${claimId}`);
    await expect(row).toBeVisible();
    await row.getByTestId(`deposit-verify-${claimId}`).click();
    await reviewerPage.getByLabel('Bank statement entry ID').fill(String(statementEntryId));
    const responsePromise = reviewerPage.waitForResponse(response =>
      response.url().endsWith(`/api/v1/admin/deposits/${claimId}/verify`) && response.request().method() === 'POST');
    await reviewerPage.getByRole('button', { name: 'Verify payment' }).click();
    const response = await responsePromise;
    expect(response.status()).toBe(200);
    return response.json();
  };

  const depositReference = `QA-M02D-${runId}-DEP`;
  const submittedDeposit = await submitDeposit(depositPlayer, depositReference, 2.5, `m02d-deposit-${runId}`);
  const depositRow = verifierPage.getByTestId(`deposit-row-${submittedDeposit.id}`);
  await verifierPage.goto('/deposits');
  await verifierPage.getByLabel('Search deposit, player, payment reference, or operation').fill(submittedDeposit.normalizedTelebirrReference);
  await verifierPage.getByRole('combobox', { name: 'Status' }).click();
  await verifierPage.getByRole('option', { name: 'PENDING', exact: true }).click();
  await expect(depositRow).toContainText('PENDING');
  await expect(depositRow).toContainText(submittedDeposit.normalizedTelebirrReference);
  await expect(depositRow).toContainText('2.5 ETB');

  const unverifiedApproval = await approverPage.request.post(
    `${api}/api/v1/admin/deposits/${submittedDeposit.id}/approve`,
    {
      headers: { Authorization: `Bearer ${approverJwt}`, 'Idempotency-Key': `m02d-unverified-${runId}` },
      data: { reviewNote: 'A note is not payment evidence' },
    },
  );
  expect(unverifiedApproval.status()).toBe(409);
  const unverifiedBalance = await page.request.get(`${api}/api/v1/wallet/balance`, {
    headers: { Authorization: `Bearer ${depositPlayer.token}` },
  });
  expect(Number((await unverifiedBalance.json()).availableBalance)).toBe(0);
  const unauthorizedVerification = await page.request.post(
    `${api}/api/v1/admin/deposits/${submittedDeposit.id}/verify`,
    { headers: { Authorization: `Bearer ${adminJwt}` }, data: { statementEntryId: 1 } },
  );
  expect(unauthorizedVerification.status()).toBe(403);
  const playerVerification = await page.request.post(
    `${api}/api/v1/admin/deposits/${submittedDeposit.id}/verify`,
    { headers: { Authorization: `Bearer ${depositPlayer.token}` }, data: { statementEntryId: 1 } },
  );
  expect(playerVerification.status()).toBe(403);

  const statementResponse = await createSyntheticStatement(depositReference, 2.5);
  expect(statementResponse.status(), await statementResponse.text()).toBe(200);
  const statement = await statementResponse.json() as { statementEntryId: number; synthetic: boolean };
  expect(statement.synthetic).toBe(true);
  const duplicateStatement = await createSyntheticStatement(depositReference, 2.5);
  expect(duplicateStatement.status()).toBe(409);
  const verified = await verifyInBrowser(
    verifierPage,
    submittedDeposit.id,
    submittedDeposit.normalizedTelebirrReference,
    statement.statementEntryId,
  ) as { outcome: string };
  expect(verified.outcome).toBe('MATCHED');
  await expect(verifierPage.getByTestId(`deposit-row-${submittedDeposit.id}`)).toContainText('VERIFIED');
  await verifierPage.screenshot({ path: path.join(evidenceDir, 'module-02d-deposit-verified.png'), fullPage: true });

  const reviewNote = 'QA synthetic statement independently matched; not real settlement';
  await approverPage.goto('/deposits');
  await approverPage.getByLabel('Search deposit, player, payment reference, or operation').fill(submittedDeposit.normalizedTelebirrReference);
  await approverPage.getByRole('combobox', { name: 'Status' }).click();
  await approverPage.getByRole('option', { name: 'VERIFIED', exact: true }).click();
  const verifiedRow = approverPage.getByTestId(`deposit-row-${submittedDeposit.id}`);
  await expect(verifiedRow).toContainText('VERIFIED');
  await expect(verifiedRow.getByTestId(`deposit-approve-${submittedDeposit.id}`)).toBeEnabled();
  await verifiedRow.getByTestId(`deposit-approve-${submittedDeposit.id}`).click();
  await approverPage.getByLabel('Review note').fill(reviewNote);
  const approvalResponsePromise = approverPage.waitForResponse(response =>
    response.url().endsWith(`/api/v1/admin/deposits/${submittedDeposit.id}/approve`) &&
    response.request().method() === 'POST');
  await approverPage.getByRole('button', { name: 'Confirm', exact: true }).click();
  const approvalResponse = await approvalResponsePromise;
  expect(approvalResponse.status()).toBe(200);
  const approvedDeposit = await approvalResponse.json() as { status: string; operationId: string };
  expect(approvedDeposit.status).toBe('APPROVED');
  const approvalKey = approvalResponse.request().headers()['idempotency-key'];
  const approvalReplay = await approverPage.request.post(
    `${api}/api/v1/admin/deposits/${submittedDeposit.id}/approve`,
    {
      headers: { Authorization: `Bearer ${approverJwt}`, 'Idempotency-Key': approvalKey },
      data: { reviewNote },
    },
  );
  expect(approvalReplay.status()).toBe(200);
  expect((await approvalReplay.json()).operationId).toBe(approvedDeposit.operationId);
  const postingResponse = await approverPage.request.get(
    `${api}/api/v1/admin/deposits/${submittedDeposit.id}/posting`,
    { headers: { Authorization: `Bearer ${approverJwt}` } },
  );
  expect(postingResponse.status()).toBe(200);
  const posting = await postingResponse.json() as {
    journalId: number; simulated: boolean; verifierUserId: number; approverUserId: number;
    walletBalanceBefore: number; walletBalanceAfter: number;
    lines: Array<{ debitAmount: number; creditAmount: number }>;
  };
  expect(posting.simulated).toBe(true);
  expect(posting.verifierUserId).toBe(verifier.id);
  expect(posting.approverUserId).toBe(approver.id);
  expect(posting.walletBalanceBefore).toBe(0);
  expect(posting.walletBalanceAfter).toBe(2.5);
  expect(posting.lines.reduce((sum, line) => sum + Number(line.debitAmount), 0)).toBe(2.5);
  expect(posting.lines.reduce((sum, line) => sum + Number(line.creditAmount), 0)).toBe(2.5);
  const approvedBalance = await page.request.get(`${api}/api/v1/wallet/balance`, {
    headers: { Authorization: `Bearer ${depositPlayer.token}` },
  });
  expect(Number((await approvedBalance.json()).availableBalance)).toBe(2.5);
  await approverPage.reload();
  await approverPage.getByLabel('Search deposit, player, payment reference, or operation').fill(submittedDeposit.normalizedTelebirrReference);
  await approverPage.getByRole('combobox', { name: 'Status' }).click();
  await approverPage.getByRole('option', { name: 'All statuses', exact: true }).click();
  await expect(approverPage.getByTestId(`deposit-row-${submittedDeposit.id}`)).toContainText('APPROVED');
  await approverPage.screenshot({ path: path.join(evidenceDir, 'module-02d-player-approved.png'), fullPage: true });

  const mismatchReference = `QA-M02D-${runId}-MISMATCH`;
  const mismatchDeposit = await submitDeposit(mismatchPlayer, mismatchReference, 3, `m02d-mismatch-${runId}`);
  const mismatchStatementResponse = await createSyntheticStatement(mismatchReference, 2.99);
  expect(mismatchStatementResponse.status(), await mismatchStatementResponse.text()).toBe(200);
  const mismatchStatement = await mismatchStatementResponse.json() as { statementEntryId: number };
  const mismatchVerification = await verifyInBrowser(
    verifierPage, mismatchDeposit.id, mismatchDeposit.normalizedTelebirrReference, mismatchStatement.statementEntryId,
  ) as { outcome: string };
  expect(mismatchVerification.outcome).not.toBe('MATCHED');
  await expect(verifierPage.getByTestId(`deposit-row-${mismatchDeposit.id}`)).toContainText('VERIFICATION_EXCEPTION');
  const mismatchBalance = await page.request.get(`${api}/api/v1/wallet/balance`, {
    headers: { Authorization: `Bearer ${mismatchPlayer.token}` },
  });
  expect(Number((await mismatchBalance.json()).availableBalance)).toBe(0);

  const rejectionReference = `QA-M02D-${runId}-REJECT`;
  const rejectionDeposit = await submitDeposit(rejectionPlayer, rejectionReference, 1.5, `m02d-reject-${runId}`);
  await approverPage.goto('/deposits');
  await approverPage.getByLabel('Search deposit, player, payment reference, or operation').fill(rejectionDeposit.normalizedTelebirrReference);
  await approverPage.getByRole('combobox', { name: 'Status' }).click();
  await approverPage.getByRole('option', { name: 'PENDING', exact: true }).click();
  const rejectionRow = approverPage.getByTestId(`deposit-row-${rejectionDeposit.id}`);
  await rejectionRow.getByTestId(`deposit-reject-${rejectionDeposit.id}`).click();
  await approverPage.getByLabel('Rejection reason').fill('Synthetic QA claim; no external settlement evidence');
  const rejectionResponsePromise = approverPage.waitForResponse(response =>
    response.url().endsWith(`/api/v1/admin/deposits/${rejectionDeposit.id}/reject`) &&
    response.request().method() === 'POST');
  await approverPage.getByRole('button', { name: 'Confirm', exact: true }).click();
  const rejectionResponse = await rejectionResponsePromise;
  expect(rejectionResponse.status()).toBe(200);
  expect((await rejectionResponse.json()).status).toBe('REJECTED');
  const rejectedBalance = await page.request.get(`${api}/api/v1/wallet/balance`, {
    headers: { Authorization: `Bearer ${rejectionPlayer.token}` },
  });
  expect(Number((await rejectedBalance.json()).availableBalance)).toBe(0);
  const invalidTransition = await approverPage.request.post(
    `${api}/api/v1/admin/deposits/${rejectionDeposit.id}/approve`,
    {
      headers: { Authorization: `Bearer ${approverJwt}`, 'Idempotency-Key': `m02d-invalid-transition-${runId}` },
      data: { reviewNote: 'Rejected claims cannot be posted' },
    },
  );
  expect(invalidTransition.status()).toBe(409);
  await approverPage.reload();
  await approverPage.getByLabel('Search deposit, player, payment reference, or operation').fill(rejectionDeposit.normalizedTelebirrReference);
  await approverPage.getByRole('combobox', { name: 'Status' }).click();
  await approverPage.getByRole('option', { name: 'All statuses', exact: true }).click();
  await expect(approverPage.getByTestId(`deposit-row-${rejectionDeposit.id}`)).toContainText('REJECTED');

  const concurrentReference = `QA-M02D-${runId}-CONCURRENT`;
  const concurrentDeposit = await submitDeposit(concurrentPlayer, concurrentReference, 4.25, `m02d-concurrent-${runId}`);
  const concurrentStatementResponse = await createSyntheticStatement(concurrentReference, 4.25);
  expect(concurrentStatementResponse.status()).toBe(200);
  const concurrentStatement = await concurrentStatementResponse.json() as { statementEntryId: number };
  const concurrentVerification = await verifyInBrowser(
    verifierPage, concurrentDeposit.id, concurrentDeposit.normalizedTelebirrReference, concurrentStatement.statementEntryId,
  ) as { outcome: string };
  expect(concurrentVerification.outcome).toBe('MATCHED');
  const concurrentApprovals = await Promise.all([1, 2].map(index => approverPage.request.post(
    `${api}/api/v1/admin/deposits/${concurrentDeposit.id}/approve`,
    {
      headers: {
        Authorization: `Bearer ${approverJwt}`,
        'Idempotency-Key': `m02d-concurrent-approve-${runId}-${index}`,
      },
      data: { reviewNote: 'Concurrent QA approval retry' },
    },
  )));
  expect(concurrentApprovals.map(response => response.status())).toEqual([200, 200]);
  const concurrentPosting = await approverPage.request.get(
    `${api}/api/v1/admin/deposits/${concurrentDeposit.id}/posting`,
    { headers: { Authorization: `Bearer ${approverJwt}` } },
  );
  expect(concurrentPosting.status()).toBe(200);
  const concurrentJournal = await concurrentPosting.json() as { journalId: number; walletBalanceAfter: number };
  expect(concurrentJournal.journalId).toBeGreaterThan(0);
  expect(concurrentJournal.walletBalanceAfter).toBe(4.25);

  await approverPage.route('**/api/v1/admin/deposits**', route => route.fulfill({
    status: 503,
    contentType: 'application/json',
    body: JSON.stringify({ message: 'Synthetic QA service outage' }),
  }));
  await approverPage.goto('/deposits');
  await expect(approverPage.getByRole('alert').filter({ hasText: /503|service outage/i })).toBeVisible();
  await approverPage.unroute('**/api/v1/admin/deposits**');

  evidence.deposit = {
    id: submittedDeposit.id,
    playerId: depositPlayer.id,
    status: approvedDeposit.status,
    statementEntryId: statement.statementEntryId,
    journalId: posting.journalId,
    verifierUserId: posting.verifierUserId,
    approverUserId: posting.approverUserId,
    balanceBefore: posting.walletBalanceBefore,
    balanceAfter: posting.walletBalanceAfter,
    unverifiedApproval: unverifiedApproval.status(),
    unauthorizedVerification: unauthorizedVerification.status(),
    duplicateBankReference: duplicateStatement.status(),
    approvalRetry: approvalReplay.status(),
    mismatchStatus: 'VERIFICATION_EXCEPTION',
    mismatchBalance: 0,
    rejectionStatus: 'REJECTED',
    rejectionBalance: 0,
    invalidTransition: invalidTransition.status(),
    concurrentApprovalStatuses: concurrentApprovals.map(response => response.status()),
    concurrentJournalId: concurrentJournal.journalId,
    networkFailure: 503,
    receivingBankAccountId: bankAccount.id,
  };
  evidence.walletAdjustment = { ...evidence.walletAdjustment, makerUserId: verifier.id, checkerUserId: approver.id };
  await verifierSession.context.close();
  await approverSession.context.close();
  // Create dedicated tickets through the player endpoint, then operate them from the real admin UI.
  const supportTickets: Array<{ id: number; ticketNumber: string; subject: string }> = [];
  for (let index = 0; index < 6; index += 1) {
    const subject = `QA_M02D_SUPPORT_${runId}_${index + 1}`;
    const response = await page.request.post(`${api}/api/v1/support/tickets`, {
      headers: { Authorization: `Bearer ${walletPlayer.token}` },
      data: {
        subject,
        description: `Synthetic Module 02D support ticket ${index + 1}`,
        category: 'WALLET',
        priority: 'HIGH',
      },
    });
    expect(response.status()).toBe(200);
    const ticket = await response.json();
    supportTickets.push({ id: ticket.id, ticketNumber: ticket.ticketNumber, subject });
  }
  const totalSupportTickets = (await (await page.request.get(`${api}/api/v1/admin/support/tickets?page=0&size=100`, {
    headers: { Authorization: `Bearer ${adminJwt}` },
  })).json()).totalElements as number;
  await page.goto('/support/tickets');
  const highPriorityResponsePromise = page.waitForResponse(response => {
    if (!response.url().includes('/api/v1/admin/support/tickets')) return false;
    return new URL(response.url()).searchParams.get('priority') === 'HIGH';
  });
  await page.getByRole('combobox', { name: 'Priority' }).click();
  await page.getByRole('option', { name: 'HIGH', exact: true }).click();
  const highPriorityResponse = await highPriorityResponsePromise;
  expect(highPriorityResponse.ok()).toBeTruthy();
  const highPriorityPage = await highPriorityResponse.json();
  const highPriorityRows = page.locator('tbody tr');
  await expect(highPriorityRows).toHaveCount(highPriorityPage.content.length);
  for (const filteredTicket of highPriorityPage.content) {
    await expect(page.getByTestId(`support-ticket-${filteredTicket.id}`)).toContainText('HIGH');
  }
  await page.goto('/support/tickets');
  await setPageSize(page, '10');
  await expect(page.locator('tbody tr')).toHaveCount(10);
  await page.getByRole('button', { name: 'Go to next page' }).click();
  const secondSupportPageRows = Math.min(10, totalSupportTickets - 10);
  await expect(page.locator('tbody tr')).toHaveCount(secondSupportPageRows);
  evidence.supportPagination = { totalSupportTickets, firstPageRows: 10, secondPageRows: secondSupportPageRows };

  const ticket = supportTickets[0];
  await page.goto('/support/tickets');
  await page.getByLabel('Search tickets, subjects, players, or phone').fill(ticket.subject);
  await expect(page.getByTestId(`support-ticket-${ticket.id}`)).toContainText(ticket.subject);
  await page.getByRole('link', { name: ticket.ticketNumber }).click();
  await expect(page.getByRole('heading', { name: ticket.ticketNumber })).toBeVisible();

  const adminUsersResponse = await page.request.get(`${api}/api/v1/admin/users?page=0&size=100`, {
    headers: { Authorization: `Bearer ${adminJwt}` },
  });
  expect(adminUsersResponse.ok()).toBeTruthy();
  const adminUsersPage = await adminUsersResponse.json();
  const qaAdmin = adminUsersPage.content.find((user: { id: number; roles: string[]; status: string }) =>
    user.id === 1 && user.roles.includes('ADMIN') && user.status === 'ACTIVE');
  expect(qaAdmin).toBeTruthy();

  const invalidAssignee = await page.request.post(`${api}/api/v1/admin/support/tickets/${ticket.id}/assign`, {
    headers: { Authorization: `Bearer ${adminJwt}` },
    data: { assignedToUserId: walletPlayer.id },
  });
  expect(invalidAssignee.status()).toBe(400);
  const invalidClose = await page.request.put(`${api}/api/v1/admin/support/tickets/${ticket.id}/status`, {
    headers: { Authorization: `Bearer ${adminJwt}` },
    data: { status: 'CLOSED', resolutionSummary: '' },
  });
  expect(invalidClose.status()).toBe(400);
  const ticketUnauthenticated = await page.request.get(`${api}/api/v1/admin/support/tickets/${ticket.id}`);
  expect(ticketUnauthenticated.status()).toBe(401);
  const ticketOtherPlayer = await page.request.get(`${api}/api/v1/support/tickets/${ticket.id}`, {
    headers: { Authorization: `Bearer ${approvalPlayer.token}` },
  });
  expect(ticketOtherPlayer.status()).toBe(400);
  expect(await ticketOtherPlayer.text()).not.toContain(ticket.subject);
  const ticketPlayerReply = await page.request.post(`${api}/api/v1/admin/support/tickets/${ticket.id}/messages`, {
    headers: { Authorization: `Bearer ${approvalPlayer.token}` },
    data: { body: 'must be denied' },
  });
  expect(ticketPlayerReply.status()).toBe(403);
  const ticketAgentReply = await page.request.post(`${api}/api/v1/admin/support/tickets/${ticket.id}/messages`, {
    headers: { Authorization: `Bearer ${agentJwt}` },
    data: { body: 'must be denied' },
  });
  expect(ticketAgentReply.status()).toBe(403);

  await page.getByRole('combobox', { name: 'Assignee' }).click();
  await page.getByRole('option', { name: qaAdmin.displayName, exact: true }).click();
  const assignResponsePromise = page.waitForResponse(response =>
    response.url().endsWith(`/api/v1/admin/support/tickets/${ticket.id}/assign`) &&
    response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Save assignment' }).click();
  const assignResponse = await assignResponsePromise;
  expect(assignResponse.status()).toBe(200);
  await expect(page.getByText(`Assigned to: ${qaAdmin.id}`, { exact: true })).toBeVisible();

  await page.getByLabel('Reply body').fill(`Public QA reply ${runId}`);
  const replyResponsePromise = page.waitForResponse(response =>
    response.url().endsWith(`/api/v1/admin/support/tickets/${ticket.id}/messages`) &&
    response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Send public reply' }).click();
  expect((await replyResponsePromise).status()).toBe(200);
  await expect(page.getByText(`Public QA reply ${runId}`)).toBeVisible();

  await page.getByLabel('Internal note').fill(`Private QA note ${runId}`);
  const noteResponsePromise = page.waitForResponse(response =>
    response.url().endsWith(`/api/v1/admin/support/tickets/${ticket.id}/notes`) &&
    response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Add internal note' }).click();
  expect((await noteResponsePromise).status()).toBe(200);
  await expect(page.getByText(`Private QA note ${runId}`)).toBeVisible();

  await page.getByRole('combobox', { name: 'New status' }).click();
  await page.getByRole('option', { name: 'CLOSED', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Review status change' })).toBeDisabled();
  await page.getByLabel('Resolution summary').fill(`Closed by Module 02D QA ${runId}`);
  const statusResponsePromise = page.waitForResponse(response =>
    response.url().endsWith(`/api/v1/admin/support/tickets/${ticket.id}/status`) &&
    response.request().method() === 'PUT');
  await page.getByRole('button', { name: 'Review status change' }).click();
  await page.getByRole('button', { name: 'Confirm', exact: true }).click();
  const statusResponse = await statusResponsePromise;
  expect(statusResponse.status()).toBe(200);
  expect((await statusResponse.json()).status).toBe('CLOSED');
  await expect(page.getByText('Status: CLOSED', { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByText('Status: CLOSED', { exact: true })).toBeVisible();
  await expect(page.getByText(`Public QA reply ${runId}`)).toBeVisible();
  await expect(page.getByText(`Private QA note ${runId}`)).toBeVisible();
  await page.screenshot({ path: path.join(evidenceDir, 'module-02d-support-closed.png'), fullPage: true });

  const supportReadback = await page.request.get(`${api}/api/v1/admin/support/tickets/${ticket.id}`, {
    headers: { Authorization: `Bearer ${adminJwt}` },
  });
  expect(supportReadback.ok()).toBeTruthy();
  const supportDetail = await supportReadback.json();
  expect(supportDetail.status).toBe('CLOSED');
  expect(supportDetail.messages.some((message: { body: string }) => message.body === `Public QA reply ${runId}`)).toBe(true);
  expect(supportDetail.internalNotes.some((note: { body: string }) => note.body === `Private QA note ${runId}`)).toBe(true);

  const auditResponse = await page.request.get(`${api}/api/v1/admin/audit-logs?page=0&size=100`, {
    headers: { Authorization: `Bearer ${adminJwt}` },
  });
  expect(auditResponse.ok()).toBeTruthy();
  const audit = await auditResponse.json();
  const auditActions = audit.content.map((row: { action: string }) => row.action);
  for (const action of ['UPDATE_PLAYER_STATUS', 'ADJUST_PLAYER_WALLET', 'REJECT_DEPOSIT', 'ASSIGN_TICKET', 'ADD_ADMIN_REPLY', 'ADD_INTERNAL_NOTE', 'UPDATE_STATUS']) {
    expect(auditActions).toContain(action);
  }

  evidence.deposit = { ...(evidence.deposit as object), auditLogged: auditActions.includes('REJECT_DEPOSIT') };
  evidence.support = {
    ticketIds: supportTickets.map(item => item.id),
    operatedTicket: ticket.id,
    invalidPlayerAssignee: invalidAssignee.status(),
    invalidCloseWithoutSummary: invalidClose.status(),
    anonymousDetail: ticketUnauthenticated.status(),
    crossAccountDetail: ticketOtherPlayer.status(),
    playerAdminReply: ticketPlayerReply.status(),
    agentAdminReply: ticketAgentReply.status(),
    finalStatus: supportDetail.status,
    messages: supportDetail.messages.length,
    internalNotes: supportDetail.internalNotes.length,
  };
  evidence.pageErrors = pageErrors;
  evidence.consoleErrors = consoleErrors;
  evidence.failedRequests = failedRequests;
  expect(pageErrors).toEqual([]);
  expect(consoleErrors).toEqual([]);
  expect(failedRequests).toEqual([]);

  await writeFile(path.join(evidenceDir, 'module-02d-execution.json'), JSON.stringify(evidence, null, 2) + '\n', 'utf8');
});
