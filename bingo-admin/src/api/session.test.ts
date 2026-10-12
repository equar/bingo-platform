import { afterEach, describe, expect, it, vi } from 'vitest';
import { clearSession, hasRequiredRole, readSession, writeSession } from './session';

describe('session helpers', () => {
  afterEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('reads and writes persisted sessions', () => {
    const session = {
      accessToken: 'token-1',
      tokenType: 'Bearer',
      user: {
        id: 1,
        displayName: 'Admin',
        phoneNumber: '251911000001',
        roles: ['ADMIN'],
        status: 'ACTIVE',
        createdAt: '2026-01-01T00:00:00.000Z',
        updatedAt: '2026-01-01T00:00:00.000Z',
      },
    };

    writeSession(session);
    expect(readSession()).toEqual(session);

    clearSession();
    expect(readSession()).toBeNull();
  });

  it('matches roles using any allowed role', () => {
    expect(hasRequiredRole(['SUPPORT', 'AGENT'], ['ADMIN', 'AGENT'])).toBe(true);
    expect(hasRequiredRole(['PLAYER'], ['ADMIN'])).toBe(false);
    expect(hasRequiredRole(['PLAYER'], [])).toBe(true);
  });
});