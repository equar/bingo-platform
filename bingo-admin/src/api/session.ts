import { useSyncExternalStore } from 'react';

export interface AdminUserProfile {
  id: number;
  displayName: string;
  phoneNumber: string;
  roles: string[];
  status: string;
  createdAt: string;
  updatedAt: string;
  mustChangePassword?: boolean;
}

export interface AdminAuthSession {
  accessToken: string;
  tokenType: string;
  user: AdminUserProfile;
}

const SESSION_KEY = 'bingo-admin-session';
const SESSION_EVENT = 'bingo-admin-session-change';
let cachedSessionRaw: string | null = null;
let cachedSessionValue: AdminAuthSession | null = null;

function emitChange() {
  window.dispatchEvent(new Event(SESSION_EVENT));
}

export function readSession(): AdminAuthSession | null {
  const raw = window.localStorage.getItem(SESSION_KEY);
  if (raw === cachedSessionRaw) {
    return cachedSessionValue;
  }

  cachedSessionRaw = raw;

  if (!raw) {
    cachedSessionValue = null;
    return null;
  }
  try {
    cachedSessionValue = JSON.parse(raw) as AdminAuthSession;
    return cachedSessionValue;
  } catch {
    cachedSessionValue = null;
    return null;
  }
}

export function writeSession(session: AdminAuthSession): void {
  window.localStorage.setItem(SESSION_KEY, JSON.stringify(session));
  cachedSessionRaw = JSON.stringify(session);
  cachedSessionValue = session;
  emitChange();
}

export function clearSession(): void {
  window.localStorage.removeItem(SESSION_KEY);
  cachedSessionRaw = null;
  cachedSessionValue = null;
  emitChange();
}

export function useSession(): AdminAuthSession | null {
  return useSyncExternalStore(
    (onStoreChange) => {
      window.addEventListener(SESSION_EVENT, onStoreChange);
      window.addEventListener('storage', onStoreChange);
      return () => {
        window.removeEventListener(SESSION_EVENT, onStoreChange);
        window.removeEventListener('storage', onStoreChange);
      };
    },
    readSession,
    readSession,
  );
}

export function hasRequiredRole(roles: string[], allowedRoles: string[]): boolean {
  return allowedRoles.length === 0 || roles.some((role) => allowedRoles.includes(role));
}
