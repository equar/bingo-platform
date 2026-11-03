import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import { http } from '../../api/http';
import i18n from '../../i18n';
import { CommunityPage } from './CommunityPage';

vi.mock('../../api/http', () => ({ http: { get: vi.fn(), post: vi.fn() } }));
afterEach(async () => { cleanup(); vi.resetAllMocks(); await i18n.changeLanguage('en'); });
it('requires a moderation reason before disabling entry', async () => {
  vi.mocked(http.get).mockImplementation(async (url) => ({ data: String(url).endsWith('/summary')
    ? { privateGames: 1, dailyClaims: 2, dailyPoints: 40, visiblePlayers: 1, timezone: 'Africa/Addis_Ababa' }
    : [{ roomId: 7, sessionId: 8, name: 'Friends', state: 'OPEN', active: true, memberCount: 2, capacity: 10, scheduledStartAt: '2026-10-29T17:00:00Z' }] }));
  vi.mocked(http.post).mockResolvedValue({ data: {} });
  const user = userEvent.setup();
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter><CommunityPage /></MemoryRouter></QueryClientProvider>);
  await user.click(await screen.findByRole('button', { name: 'Disable new entry' }));
  expect(screen.getByRole('button', { name: 'Disable entry' })).toBeDisabled();
  expect(http.post).not.toHaveBeenCalled();
  await user.type(screen.getByLabelText(/Moderation reason/), 'Reported invite abuse');
  await user.click(screen.getByRole('button', { name: 'Disable entry' }));
  expect(http.post).toHaveBeenCalledWith('/api/v1/admin/community/private-games/7/disable', { reason: 'Reported invite abuse' });
});

it('localizes community labels and API statuses without changing game names', async () => {
  vi.mocked(http.get).mockImplementation(async (url) => ({ data: String(url).endsWith('/summary')
    ? { privateGames: 1, dailyClaims: 2, dailyPoints: 40, visiblePlayers: 1, timezone: 'Africa/Addis_Ababa' }
    : [{ roomId: 7, sessionId: 8, name: 'Friends', state: 'OPEN', active: true, memberCount: 2, capacity: 10, scheduledStartAt: '2026-10-29T17:00:00Z' }] }));
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter><CommunityPage /></MemoryRouter></QueryClientProvider>);

  expect(await screen.findByRole('heading', { name: 'Player community' })).toBeInTheDocument();
  await act(async () => { await i18n.changeLanguage('am'); });

  expect(await screen.findByRole('heading', { name: 'የተጫዋቾች ማህበረሰብ' })).toBeInTheDocument();
  expect(screen.getByText('Friends')).toBeInTheDocument();
  expect(screen.getByText('ክፍት')).toBeInTheDocument();
  const user = userEvent.setup();
  await user.click(screen.getByRole('button', { name: 'አዲስ መግቢያን አሰናክል' }));
  expect(screen.getByText('የአስተዳደር ምክንያት ማስገባት ያስፈልጋል።')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'መግቢያን አሰናክል' })).toBeDisabled();
  expect(http.post).not.toHaveBeenCalled();
});
