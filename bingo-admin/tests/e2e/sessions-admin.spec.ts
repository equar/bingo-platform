import { expect, test, type Locator, type Page } from '@playwright/test';

const runtimeEnv = (globalThis as any)['process']?.env ?? {};
const backendBaseUrl = runtimeEnv.BINGO_E2E_BACKEND_URL ?? 'http://localhost:8080';
const adminEmail = requiredEnv('BINGO_E2E_ADMIN_EMAIL');
const adminPassword = requiredEnv('BINGO_E2E_ADMIN_PASSWORD');
const runId = `Sessions E2E ${new Date().toISOString().replace(/[:.]/g, '-')}`;

function requiredEnv(name: string): string {
  const value = runtimeEnv[name];
  if (typeof value !== 'string' || !value.trim()) {
    throw new Error(`${name} must be set to run the admin E2E tests`);
  }
  return value;
}

function dateTimeLocal(offsetMinutes: number) {
  const date = new Date(Date.now() + offsetMinutes * 60_000);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}

async function login(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Email address').fill(adminEmail);
  await page.locator('input[name="password"]').fill(adminPassword);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
}

async function clickAndConfirm(row: Locator, action: string, page: Page) {
  await row.getByRole('button', { name: action, exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('button', { name: 'Confirm', exact: true }).click();
}

test('sessions page supports creation, search, filters, details, state changes, and empty results', async ({ page }) => {
  await login(page);
  const accessToken = await page.evaluate(() => {
    const session = JSON.parse(localStorage.getItem('bingo-admin-session') ?? '{}');
    return session.accessToken as string;
  });
  expect(accessToken).toBeTruthy();

  const roomNames = [`${runId} Room A`, `${runId} Room B`];
  const createdRoomIds: number[] = [];

  // Recover test-created records from an interrupted earlier run before adding fresh data.
  const previousSessionsResponse = await page.request.get(`${backendBaseUrl}/api/v1/admin/sessions?size=200`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (previousSessionsResponse.ok()) {
    const previousSessions = (await previousSessionsResponse.json()) as {
      content: Array<{ id: number; sessionName: string; state: string }>;
    };
    for (const session of previousSessions.content.filter((item) => item.sessionName.startsWith('Sessions E2E '))) {
      if (session.state !== 'CANCELLED' && session.state !== 'SETTLED') {
        await page.request.put(`${backendBaseUrl}/api/v1/admin/sessions/${session.id}/state`, {
          headers: { Authorization: `Bearer ${accessToken}` },
          data: { state: 'CANCELLED' },
        });
      }
    }
  }
  const previousRoomsResponse = await page.request.get(`${backendBaseUrl}/api/v1/admin/rooms`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (previousRoomsResponse.ok()) {
    const previousRooms = (await previousRoomsResponse.json()) as Array<{
      id: number;
      name: string;
      description: string | null;
      ticketPrice: string;
      currency: string;
      timezone: string;
      scheduleRule: string;
      sessionCapacity: number;
      maxTicketsPerPlayer: number;
      ticketSalesCloseOnSessionStart: boolean;
      allowMultipleWinnersPerPrize: boolean;
      active: boolean;
      configuredPatterns: string[];
      configuredPrizes: Array<{ prizeType: string; amount: number; settlementPath: string; maxWinners?: number }>;
    }>;
    for (const room of previousRooms.filter((item) => item.name.startsWith('Sessions E2E ') && item.active)) {
      await page.request.put(`${backendBaseUrl}/api/v1/admin/rooms/${room.id}`, {
        headers: { Authorization: `Bearer ${accessToken}` },
        data: { ...room, active: false },
      });
    }
  }
  for (const roomName of roomNames) {
    const response = await page.request.post(`${backendBaseUrl}/api/v1/admin/rooms`, {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: {
        name: roomName,
        description: `Temporary UI test data ${runId}`,
        ticketPrice: 25,
        currency: 'ETB',
        timezone: 'Africa/Addis_Ababa',
        scheduleRule: 'WEEKLY',
        sessionCapacity: 100,
        maxTicketsPerPlayer: 3,
        ticketSalesCloseOnSessionStart: true,
        allowMultipleWinnersPerPrize: true,
        active: true,
        configuredPatterns: ['LINE', 'TWO_LINES', 'FULL_HOUSE'],
        configuredPrizes: [{ prizeType: 'LINE', amount: 100, settlementPath: 'PLAYER_WALLET', maxWinners: 1 }],
      },
    });
    if (!response.ok()) throw new Error(`Room creation failed: ${response.status()} ${await response.text()}`);
    createdRoomIds.push(((await response.json()) as { id: number }).id);
  }

  const sessions: Array<{ id: number; name: string; state: string }> = [];
  const createViaApi = async (name: string, roomId: number, state: string, offsetMinutes: number) => {
    const response = await page.request.post(`${backendBaseUrl}/api/v1/admin/rooms/${roomId}/sessions`, {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: {
        sessionName: name,
        scheduledStartAt: new Date(Date.now() + offsetMinutes * 60_000).toISOString(),
        state,
      },
    });
    if (!response.ok()) throw new Error(`Session creation failed: ${response.status()} ${await response.text()}`);
    const session = (await response.json()) as { id: number; sessionName: string; state: string };
    sessions.push({ id: session.id, name: session.sessionName, state: session.state });
  };

  try {
    await createViaApi(`${runId} Past Draft`, createdRoomIds[1], 'DRAFT', -60);
    await createViaApi(`${runId} Future Closed`, createdRoomIds[1], 'CLOSED', 60);
    await createViaApi(`${runId} Past Open`, createdRoomIds[1], 'OPEN', -30);

    await page.goto('/sessions');
    await expect(page.getByRole('heading', { name: 'Sessions' })).toBeVisible();
    await page.getByRole('button', { name: 'New session' }).click();
    const createDialog = page.getByRole('dialog');
    await createDialog.getByLabel('Room').click();
    await page.getByRole('option', { name: roomNames[0] }).click();
    const createdName = `${runId} UI Created Upcoming`;
    await createDialog.getByLabel('Session name').fill(createdName);
    await createDialog.getByLabel('Scheduled start').fill(dateTimeLocal(90));
    await createDialog.getByRole('button', { name: 'Create', exact: true }).click();
    await expect(page).toHaveURL(/\/sessions\/\d+$/);
    await expect(page.getByText(createdName)).toBeVisible();
    const createdId = Number(new URL(page.url()).pathname.split('/').pop());
    sessions.push({ id: createdId, name: createdName, state: 'OPEN' });

    await page.goto('/sessions');
    const search = page.getByLabel('Search sessions');
    await search.fill(`${runId} UI Created`);
    let rows = page.getByRole('row').filter({ hasText: createdName });
    await expect(rows).toHaveCount(1);
    await expect(page.getByText(/Showing 1 of \d+ sessions/)).toBeVisible();

    await search.fill(roomNames[0]);
    await expect(rows).toHaveCount(1);
    await search.fill(String(createdId));
    await expect(rows).toHaveCount(1);

    await search.fill(runId);
    await page.getByRole('combobox', { name: /Status/ }).click();
    await page.getByRole('option', { name: 'DRAFT', exact: true }).click();
    await expect(page.getByRole('row').filter({ hasText: `${runId} Past Draft` })).toHaveCount(1);
    await page.getByRole('combobox', { name: /Room/ }).click();
    await page.getByRole('option', { name: roomNames[0], exact: true }).click();
    await expect(page.getByText('No sessions match your search and filters.')).toBeVisible();

    await page.getByRole('combobox', { name: /Status/ }).click();
    await page.getByRole('option', { name: 'All statuses' }).click();
    await page.getByRole('combobox', { name: /Start date/ }).click();
    await page.getByRole('option', { name: 'Upcoming' }).click();
    await expect(page.getByRole('row').filter({ hasText: createdName })).toHaveCount(1);
    await page.getByRole('combobox', { name: /Start date/ }).click();
    await page.getByRole('option', { name: 'Past' }).click();
    await expect(page.getByText('No sessions match your search and filters.')).toBeVisible();

    await page.getByRole('combobox', { name: /Start date/ }).click();
    await page.getByRole('option', { name: 'Any time' }).click();
    await page.getByRole('combobox', { name: /Room/ }).click();
    await page.getByRole('option', { name: 'All rooms' }).click();
    await search.fill('definitely-no-session-matches-this');
    await expect(page.getByText('No sessions match your search and filters.')).toBeVisible();
    await search.fill('');
    rows = page.getByRole('row').filter({ hasText: createdName });
    await expect(rows).toHaveCount(1);

    await rows.getByRole('button', { name: 'Open live view' }).click();
    await expect(page).toHaveURL(new RegExp(`/sessions/${createdId}$`));
    await expect(page.getByText(createdName)).toBeVisible();

    await page.goto('/sessions');
    rows = page.getByRole('row').filter({ hasText: createdName });
    await clickAndConfirm(rows, 'STARTED', page);
    await expect(page.getByRole('row').filter({ hasText: createdName }).getByText('STARTED')).toBeVisible();

    for (const session of sessions) {
      const row = page.getByRole('row').filter({ hasText: session.name });
      await expect(row).toHaveCount(1);
      await clickAndConfirm(row, 'CANCELLED', page);
      await expect(page.getByRole('row').filter({ hasText: session.name }).getByText('CANCELLED')).toBeVisible();
    }
  } finally {
    for (const session of sessions) {
      const detailResponse = await page.request.get(`${backendBaseUrl}/api/v1/sessions/${session.id}`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      if (detailResponse.ok()) {
        const detail = (await detailResponse.json()) as { state: string };
        if (detail.state !== 'CANCELLED' && detail.state !== 'SETTLED') {
          await page.request.put(`${backendBaseUrl}/api/v1/admin/sessions/${session.id}/state`, {
            headers: { Authorization: `Bearer ${accessToken}` },
            data: { state: 'CANCELLED' },
          });
        }
      }
    }
    for (const roomId of createdRoomIds) {
      const room = (await (await page.request.get(`${backendBaseUrl}/api/v1/admin/rooms`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      })).json() as Array<any>).find((item) => item.id === roomId);
      if (room?.active) {
        await page.request.put(`${backendBaseUrl}/api/v1/admin/rooms/${roomId}`, {
          headers: { Authorization: `Bearer ${accessToken}` },
          data: { ...room, active: false },
        });
      }
    }
  }
});
