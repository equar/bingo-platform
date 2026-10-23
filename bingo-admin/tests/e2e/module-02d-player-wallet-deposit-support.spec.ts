import { expect, test, type Page } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { loginAdmin, qaBaseUrls, requiredQaEnv } from './support/qaHelpers';

const evidenceDir = path.resolve(process.cwd(), '../docs/qa/evidence');
const api = qaBaseUrls().backend;

type SyntheticPlayer = { id: number; name: string; phone: string; token: string };

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

test('player, wallet, deposit rejection, and support workflows preserve existing QA records', async ({ page }) => {
  test.setTimeout(300_000);
  const pageErrors: string[] = [];
  const consoleErrors: string[] = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') consoleErrors.push(message.text()); });
  await loginAdmin(page);
  const adminJwt = await adminToken(page);
  const runId = Date.now().toString().slice(-8);
  const players: SyntheticPlayer[] = [];
  for (let index = 0; index < 4; index += 1) {
    const suffix = String((Number(runId) + index) % 100_000_000).padStart(8, '0');
    players.push(await registerPlayer(page, `QA_M02D_PLAYER_00${index + 1}_${runId}`, `2519${suffix}`));
  }
  const [approvalPlayer, suspendedPlayer, depositPlayer, walletPlayer] = players;
  const evidence: Record<string, unknown> = { players: players.map(({ id, name }) => ({ id, name })) };

  const anonymousRead = await page.request.get(`${api}/api/v1/admin/players/${approvalPlayer.id}`);
  expect(anonymousRead.status()).toBe(401);

  // The existing seven QA players plus these four fixtures exercise real roster pagination.
  await page.goto('/players');
  await setPageSize(page, '10');
  await expect(page.locator('tbody tr')).toHaveCount(10);
  await page.getByRole('button', { name: 'Go to next page' }).click();
  await expect(page.locator('tbody tr')).toHaveCount(1);
  evidence.playerPagination = { firstPageRows: 10, secondPageRows: 1 };

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

  // Manual adjustment is an existing admin operation. Apply a small synthetic credit and replay exactly once.
  await page.goto(`/players/${walletPlayer.id}`);
  await expect(page.getByRole('heading', { name: walletPlayer.name })).toBeVisible();
  await expect(page.getByText('0 ETB', { exact: true }).first()).toBeVisible();
  await page.getByRole('button', { name: 'Adjust wallet' }).click();
  await page.getByLabel('Amount').fill('1.25');
  await page.getByLabel('Reason').fill('QA-only Module 02D wallet adjustment');
  const adjustmentReason = `QA-only Module 02D wallet adjustment (Reference: QA-M02D-${runId})`;
  await page.getByLabel('Reference').fill(`QA-M02D-${runId}`);
  await page.getByRole('button', { name: 'Review', exact: true }).click();
  const adjustmentResponsePromise = page.waitForResponse(response =>
    response.url().endsWith(`/api/v1/admin/players/${walletPlayer.id}/wallet-adjustments`) &&
    response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Confirm adjustment' }).click();
  const adjustmentResponse = await adjustmentResponsePromise;
  expect(adjustmentResponse.status()).toBe(200);
  const adjustmentBody = adjustmentResponse.request().postDataJSON() as Record<string, unknown>;
  const adjustmentKey = adjustmentResponse.request().headers()['idempotency-key'];
  const adjustment = await adjustmentResponse.json();
  expect(adjustment.balanceBefore).toBe('0.00');
  expect(adjustment.balanceAfter).toBe('1.25');
  const adjustmentReplay = await page.request.post(
    `${api}/api/v1/admin/players/${walletPlayer.id}/wallet-adjustments`,
    { headers: { Authorization: `Bearer ${adminJwt}`, 'Idempotency-Key': adjustmentKey }, data: adjustmentBody },
  );
  expect(adjustmentReplay.status()).toBe(200);
  expect(await adjustmentReplay.json()).toEqual(adjustment);
  const unauthorizedAdjustment = await page.request.post(
    `${api}/api/v1/admin/players/${walletPlayer.id}/wallet-adjustments`,
    {
      headers: { Authorization: `Bearer ${approvalPlayer.token}`, 'Idempotency-Key': `m02d-unauth-${runId}` },
      data: { amount: 5, currency: 'ETB', reason: 'must not post' },
    },
  );
  expect(unauthorizedAdjustment.status()).toBe(403);
  const invalidAdjustment = await page.request.post(
    `${api}/api/v1/admin/players/${walletPlayer.id}/wallet-adjustments`,
    {
      headers: { Authorization: `Bearer ${adminJwt}`, 'Idempotency-Key': `m02d-invalid-${runId}` },
      data: { amount: 0, currency: 'ETB', reason: 'invalid zero adjustment' },
    },
  );
  expect(invalidAdjustment.status()).toBe(400);
  await expect(page.locator('.MuiCard-root').filter({ has: page.getByText('Available balance', { exact: true }) }))
    .toContainText('1.25 ETB');
  await page.reload();
  await expect(page.locator('.MuiCard-root').filter({ has: page.getByText('Available balance', { exact: true }) }))
    .toContainText('1.25 ETB');
  await page.getByRole('button', { name: 'View wallet transactions details' }).click();
  await expect(page.getByRole('dialog')).toContainText('WALLET ADJUSTMENT');
  await expect(page.getByRole('dialog')).toContainText('1.25 ETB');
  await expect(page.getByRole('dialog')).toContainText(adjustmentReason);
  await page.screenshot({ path: path.join(evidenceDir, 'module-02d-wallet-adjustment.png'), fullPage: true });
  evidence.walletAdjustment = {
    playerId: walletPlayer.id,
    status: adjustmentResponse.status(),
    balanceBefore: adjustment.balanceBefore,
    balanceAfter: adjustment.balanceAfter,
    duplicateReplay: adjustmentReplay.status(),
    unauthorized: unauthorizedAdjustment.status(),
    invalidAmount: invalidAdjustment.status(),
  };

  // A synthetic claim is submitted, reviewed and rejected. No payment settlement or wallet credit is simulated.
  const depositReference = `QA-M02D-${runId}-DEP`;
  const depositSubmission = await page.request.post(`${api}/api/v1/wallet/deposits`, {
    headers: { Authorization: `Bearer ${depositPlayer.token}`, 'Idempotency-Key': `m02d-deposit-${runId}` },
    data: { submittedCashAmount: 2.5, currency: 'ETB', telebirrReference: depositReference },
  });
  expect(depositSubmission.status()).toBe(201);
  const submittedDeposit = await depositSubmission.json();
  expect(submittedDeposit.status).toBe('PENDING');
  await page.goto('/deposits');
  await page.getByLabel('Search deposit, player, payment reference, or operation').fill(depositReference);
  const depositRow = page.getByTestId(`deposit-row-${submittedDeposit.id}`);
  await expect(depositRow).toContainText('PENDING');
  await expect(depositRow).toContainText(depositReference);
  await expect(depositRow).toContainText('2.50 ETB');
  await depositRow.getByTestId(`deposit-reject-${submittedDeposit.id}`).click();
  await page.getByLabel('Rejection reason').fill('QA synthetic claim; no external payment verification performed');
  const rejectResponsePromise = page.waitForResponse(response =>
    response.url().endsWith(`/api/v1/admin/deposits/${submittedDeposit.id}/reject`) &&
    response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Confirm', exact: true }).click();
  const rejectResponse = await rejectResponsePromise;
  expect(rejectResponse.status()).toBe(200);
  const rejectKey = rejectResponse.request().headers()['idempotency-key'];
  const rejectedDeposit = await rejectResponse.json();
  expect(rejectedDeposit.status).toBe('REJECTED');
  await expect(page.getByTestId(`deposit-row-${submittedDeposit.id}`)).toContainText('REJECTED');
  await page.screenshot({ path: path.join(evidenceDir, 'module-02d-deposit-rejected.png'), fullPage: true });
  await page.reload();
  await expect(page.getByTestId(`deposit-row-${submittedDeposit.id}`)).toContainText('REJECTED');
  const rejectReplay = await page.request.post(
    `${api}/api/v1/admin/deposits/${submittedDeposit.id}/reject`,
    {
      headers: { Authorization: `Bearer ${adminJwt}`, 'Idempotency-Key': rejectKey },
      data: { reviewNote: 'QA synthetic claim; no external payment verification performed' },
    },
  );
  expect(rejectReplay.status()).toBe(200);
  expect((await rejectReplay.json()).status).toBe('REJECTED');
  const invalidTransition = await page.request.post(
    `${api}/api/v1/admin/deposits/${submittedDeposit.id}/approve`,
    {
      headers: { Authorization: `Bearer ${adminJwt}`, 'Idempotency-Key': `m02d-invalid-approve-${runId}` },
      data: { reviewNote: 'QA transition guard' },
    },
  );
  expect(invalidTransition.status()).toBe(409);
  const playerApproval = await page.request.post(
    `${api}/api/v1/admin/deposits/${submittedDeposit.id}/approve`,
    {
      headers: { Authorization: `Bearer ${depositPlayer.token}`, 'Idempotency-Key': `m02d-player-approve-${runId}` },
      data: { reviewNote: 'must be denied' },
    },
  );
  expect(playerApproval.status()).toBe(403);
  const agentApproval = await page.request.post(
    `${api}/api/v1/admin/deposits/${submittedDeposit.id}/approve`,
    { headers: { Authorization: `Bearer ${agentJwt}`, 'Idempotency-Key': `m02d-agent-approve-${runId}` },
      data: { reviewNote: 'must be denied' } },
  );
  expect(agentApproval.status()).toBe(403);
  const depositBalance = await page.request.get(`${api}/api/v1/wallet/balance`, {
    headers: { Authorization: `Bearer ${depositPlayer.token}` },
  });
  expect(depositBalance.ok()).toBeTruthy();
  expect((await depositBalance.json()).availableBalance).toBe('0.00');
  evidence.deposit = {
    id: submittedDeposit.id,
    playerId: depositPlayer.id,
    submitted: depositSubmission.status(),
    reviewedStatus: rejectedDeposit.status,
    duplicateReplay: rejectReplay.status(),
    approveAfterReject: invalidTransition.status(),
    playerApproval: playerApproval.status(),
    agentApproval: agentApproval.status(),
    availableBalanceAfterRejection: '0.00',
  };

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
  await page.goto('/support/tickets');
  await setPageSize(page, '10');
  await expect(page.locator('tbody tr')).toHaveCount(10);
  await page.getByRole('button', { name: 'Go to next page' }).click();
  await expect(page.locator('tbody tr')).toHaveCount(1);
  evidence.supportPagination = { firstPageRows: 10, secondPageRows: 1 };

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
  expect(pageErrors).toEqual([]);
  expect(consoleErrors).toEqual([]);

  await writeFile(path.join(evidenceDir, 'module-02d-execution.json'), JSON.stringify(evidence, null, 2) + '\n', 'utf8');
});
