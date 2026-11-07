import { useSyncExternalStore } from 'react';
import { applyLanguage, DEFAULT_LANGUAGE, resolvePreferredLanguage } from '../i18n';

export interface AdminUserProfile {
  id: number;
  displayName: string;
  email?: string | null;
  phoneNumber: string | null;
  roles: string[];
  status: string;
  createdAt: string;
  updatedAt: string;
  mustChangePassword?: boolean;
  preferredLanguage?: string;
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
let sessionExpiredPending = false;

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
  sessionExpiredPending = false;
  // The backend-stored preference is authoritative for the signed-in account.
  applyLanguage(resolvePreferredLanguage(session.user.preferredLanguage));
  emitChange();
}

export function clearSession(options: { expired?: boolean } = {}): void {
  window.localStorage.removeItem(SESSION_KEY);
  for (let index = window.sessionStorage.length - 1; index >= 0; index -= 1) {
    const key = window.sessionStorage.key(index);
    if (key?.startsWith('admin-list-search:') || key?.startsWith('admin-list-state:')) window.sessionStorage.removeItem(key);
  }
  cachedSessionRaw = null;
  cachedSessionValue = null;
  sessionExpiredPending = options.expired === true;
  // Unauthenticated screens always render in English; the account's saved
  // preference stays untouched in the backend.
  applyLanguage(DEFAULT_LANGUAGE);
  emitChange();
}

export function consumeSessionExpired(): boolean {
  const expired = sessionExpiredPending;
  sessionExpiredPending = false;
  return expired;
}

// Keep the active language in sync when another tab signs in or out.
if (typeof window !== "undefined") {
  window.addEventListener("storage", (event) => {
    if (event.key === null || event.key === SESSION_KEY) {
      const session = readSession();
      applyLanguage(session ? resolvePreferredLanguage(session.user.preferredLanguage) : DEFAULT_LANGUAGE);
    }
  });
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
