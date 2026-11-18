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
  preferredCalendar?: string;
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
  let raw = window.sessionStorage.getItem(SESSION_KEY);
  if (raw === null) {
    // Migrate sessions created by older builds, then remove the persistent token copy.
    raw = window.localStorage.getItem(SESSION_KEY);
    if (raw !== null) {
      window.sessionStorage.setItem(SESSION_KEY, raw);
      window.localStorage.removeItem(SESSION_KEY);
    }
  }
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
  window.sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
  window.localStorage.removeItem(SESSION_KEY);
  cachedSessionRaw = JSON.stringify(session);
  cachedSessionValue = session;
  sessionExpiredPending = false;
  // The backend-stored preference is authoritative for the signed-in account.
  applyLanguage(resolvePreferredLanguage(session.user.preferredLanguage));
  emitChange();
}

export function clearSession(options: { expired?: boolean } = {}): void {
  window.sessionStorage.removeItem(SESSION_KEY);
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

export function useSession(): AdminAuthSession | null {
  return useSyncExternalStore(
    (onStoreChange) => {
      window.addEventListener(SESSION_EVENT, onStoreChange);
      return () => {
        window.removeEventListener(SESSION_EVENT, onStoreChange);
      };
    },
    readSession,
    readSession,
  );
}

export function hasRequiredRole(roles: string[], allowedRoles: string[]): boolean {
  return allowedRoles.length === 0 || roles.some((role) => allowedRoles.includes(role));
}
