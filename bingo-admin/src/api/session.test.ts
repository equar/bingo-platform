import { afterEach, describe, expect, it, vi } from 'vitest';
import i18n from '../i18n';
import { clearSession, hasRequiredRole, readSession, writeSession } from './session';

function buildSession(overrides: { accessToken?: string; preferredLanguage?: string; id?: number } = {}) {
  return {
    accessToken: overrides.accessToken ?? 'token-1',
    tokenType: 'Bearer',
    user: {
      id: overrides.id ?? 1,
      displayName: 'Admin',
      phoneNumber: '251911000001',
      roles: ['ADMIN'],
      status: 'ACTIVE',
      createdAt: '2026-01-01T00:00:00.000Z',
      updatedAt: '2026-01-01T00:00:00.000Z',
      ...(overrides.preferredLanguage ? { preferredLanguage: overrides.preferredLanguage } : {}),
    },
  };
}

describe('session helpers', () => {
  afterEach(async () => {
    window.localStorage.clear();
    vi.restoreAllMocks();
    await i18n.changeLanguage('en');
  });

  it('reads and writes persisted sessions', () => {
    const session = buildSession();

    writeSession(session);
    expect(readSession()).toEqual(session);

    clearSession();
    expect(readSession()).toBeNull();
  });

  it('clears potentially personal list searches when the admin session ends', () => {
    window.sessionStorage.setItem('admin-list-search:players', 'player phone');
    window.sessionStorage.setItem('admin-list-state:agents:page', '2');
    clearSession();
    expect(window.sessionStorage.getItem('admin-list-search:players')).toBeNull();
    expect(window.sessionStorage.getItem('admin-list-state:agents:page')).toBeNull();
  });

  it('applies the saved account language on sign-in and returns to English on sign-out', () => {
    writeSession(buildSession({ preferredLanguage: 'am' }));
    expect(i18n.language).toBe('am');
    expect(document.documentElement.lang).toBe('am');

    clearSession();
    expect(i18n.language).toBe('en');
    expect(document.documentElement.lang).toBe('en');
  });

  it('keeps English after session expiry without touching the account preference server-side', () => {
    writeSession(buildSession({ preferredLanguage: 'am' }));
    clearSession({ expired: true });
    expect(i18n.language).toBe('en');
    expect(readSession()).toBeNull();
  });

  it('gives each signed-in account its own language on a shared browser', () => {
    writeSession(buildSession({ id: 1, accessToken: 'user-a', preferredLanguage: 'am' }));
    expect(i18n.language).toBe('am');

    clearSession();
    expect(i18n.language).toBe('en');

    // A second account with no saved preference stays in English.
    writeSession(buildSession({ id: 2, accessToken: 'user-b' }));
    expect(i18n.language).toBe('en');
  });

  it('defaults unsupported saved values to English', () => {
    writeSession(buildSession({ preferredLanguage: 'fr' }));
    expect(i18n.language).toBe('en');
  });

  it('matches roles using any allowed role', () => {
    expect(hasRequiredRole(['SUPPORT', 'AGENT'], ['ADMIN', 'AGENT'])).toBe(true);
    expect(hasRequiredRole(['PLAYER'], ['ADMIN'])).toBe(false);
    expect(hasRequiredRole(['PLAYER'], [])).toBe(true);
  });
});
