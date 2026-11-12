import { expect, test } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';

// UI contract exercise: API behavior is verified separately against disposable MySQL.
test('existing admin lifecycle presents funding, rules, review and wallet settlement', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.addInitScript(() => {
    localStorage.setItem('bingo-admin-session', JSON.stringify({ accessToken: 'synthetic-ui-token', tokenType: 'Bearer', user: {
      id: 1, displayName: 'Test administrator', email: 'admin@example.test', phoneNumber: null,
      roles: ['ADMIN'], status: 'ACTIVE', preferredLanguage: 'en', createdAt: '2026-11-11', updatedAt: '2026-11-11',
    } }));
    class QuietSocket {
      onopen?: () => void;
      onmessage?: (event: { data: string }) => void;
      onclose?: () => void;
      constructor() { setTimeout(() => this.onopen?.(), 0); }
      send(frame: string) { if (frame.startsWith('CONNECT\n')) setTimeout(() => this.onmessage?.({ data: 'CONNECTED\nversion:1.2\n\n\u0000' }), 0); }
      close() {}
    }
    Object.defineProperty(window, 'WebSocket', { value: QuietSocket });
  });
  let sessionState = 'OPEN';
  let claimStatus = 'PENDING_REVIEW';
  let prizeStatus = 'PENDING';
  const game = () => ({ id: 9, roomId: 2, roomName: 'Main hall', sessionName: 'Evening Bingo', state: sessionState,
    scheduledStartAt: '2026-11-11T23:00:00Z', startedAt: sessionState === 'OPEN' ? null : '2026-11-11T23:00:00Z', endedAt: null,
    ticketPrice: '100.00', currency: 'ETB', sessionCapacity: 100, maxTicketsPerPlayer: 10,
    ticketSalesCloseOnSessionStart: true, allowMultipleWinnersPerPrize: true, configuredPatterns: ['LINE'],
    configuredPrizes: [{ prizeType: 'LINE', amount: '500.00', settlementPath: 'PLAYER_WALLET', maxWinners: 1 }],
    ticketCount: 10, drawCount: 0, createdAt: '2026-11-11', updatedAt: '2026-11-11', rulesVersion: 'EARLIEST_SPLIT_V1',
    minimumHouseMarginBps: 2000, claimWindowSeconds: 86400, payoutsEnabled: true,
    funding: { cashBackedRevenue: '1000.00', commissionLiability: '50.00', prizeLiability: '500.00', costReserve: '25.00', requiredHouseRetention: '200.00', totalRequired: '775.00', funded: true },
  });
  const paged = (content: unknown[]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1, first: true, last: true });
  const entitlement = () => ({ id: 24, winClaimId: 12, sessionId: 9, ticketId: 44, claimantId: 31, claimantDisplayName: 'Test player',
    claimedPattern: 'LINE', claimStatus, sessionName: 'Evening Bingo', publicTicketCode: 'B-44', prizeType: 'LINE',
    settlementPath: 'PLAYER_WALLET', status: prizeStatus, prizeAmount: '250.00', currency: 'ETB', operationId: 'test-prize',
    createdAt: '2026-11-11T23:10:00Z', settledAt: null, settledBy: null, settlementReference: prizeStatus === 'SETTLED' ? 'WALLET-24' : null, settlementNote: null, payoutsEnabled: true });
  await page.route('http://127.0.0.1:8099/**', async route => {
    const request = route.request(); const pathname = new URL(request.url()).pathname;
    let body: unknown = {};
    if (pathname.endsWith('/auth/me')) body = { user: { id: 1, displayName: 'Test administrator', email: 'admin@example.test', phoneNumber: null, roles: ['ADMIN'], status: 'ACTIVE', preferredLanguage: 'en', createdAt: '2026-11-11', updatedAt: '2026-11-11' } };
    else if (pathname.endsWith('/state')) { sessionState = request.postDataJSON().state; body = game(); }
    else if (pathname.endsWith('/draws')) body = [];
    else if (pathname.endsWith('/sessions/9')) body = game();
    else if (pathname.endsWith('/win-claims/12/approve')) { claimStatus = 'APPROVED'; body = { id: 12, status: claimStatus }; }
    else if (pathname.endsWith('/win-claims')) body = paged([{ id: 12, ticketId: 44, sessionId: 9, claimantId: 31, claimantDisplayName: 'Test player', publicTicketCode: 'B-44', claimedPattern: 'LINE', status: claimStatus, serverValidationPassed: true, drawSequenceSnapshot: 20, calledNumbersSnapshot: [], operationId: 'test-claim', createdAt: '2026-11-11T23:09:00Z', updatedAt: '2026-11-11T23:09:00Z' }]);
    else if (pathname.endsWith('/prize-entitlements/24/settle')) { prizeStatus = 'SETTLED'; body = entitlement(); }
    else if (pathname.endsWith('/prize-entitlements')) body = paged([entitlement()]);
    await route.fulfill({ json: body, headers: { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': '*' } });
  });
  await page.goto('/sessions/9');
  await expect(page.getByText('Minimum house margin: 20%')).toBeVisible();
  await expect(page.getByText(/Cash-backed sales: 1000.00/)).toBeVisible();
  await page.getByTestId('session-transition-started').click();
  await page.getByRole('button', { name: 'Confirm', exact: true }).click();
  await expect(page.getByTestId('automatic-draw-button')).toBeEnabled();
  const evidence = path.resolve('../.tmp/local/admin-readiness'); await mkdir(evidence, { recursive: true });
  await expect(page.getByRole('dialog')).toBeHidden();
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
  await page.screenshot({ path: path.join(evidence, 'live-session.png'), fullPage: true, animations: 'disabled' });
  await page.goto('/claims');
  await page.getByTestId('claim-approve-12').click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('textbox').fill('Verified earliest-completion evidence');
  await dialog.getByRole('button', { name: 'Confirm', exact: true }).click();
  await expect(page.getByTestId('claim-approve-12')).toBeDisabled();
  await page.goto('/prize-entitlements');
  await page.getByTestId('entitlement-settle-24').click();
  await page.getByRole('textbox', { name: 'Settlement reference' }).fill('WALLET-24');
  await page.getByRole('button', { name: 'Confirm settlement' }).click();
  await expect(page.getByTestId('entitlement-settle-24')).toBeDisabled();
  await expect(page.getByRole('dialog')).toBeHidden();
  await page.screenshot({ path: path.join(evidence, 'paid-prize.png'), fullPage: true, animations: 'disabled' });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/sessions/9');
  await expect(page.getByText('Minimum house margin: 20%')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: path.join(evidence, 'mobile-session.png'), fullPage: true, animations: 'disabled' });
  expect(errors).toEqual([]);
});
