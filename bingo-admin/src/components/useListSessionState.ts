import { useEffect, useState, type Dispatch, type SetStateAction } from "react";

/** Persists non-secret list controls across route unmounts within this tab. */
export function useListSessionState<T>(key: string, initialValue: T | (() => T)): [T, Dispatch<SetStateAction<T>>] {
  const storageKey = `admin-list-state:${key}`;
  const [value, setValue] = useState<T>(() => {
    try {
      const stored = window.sessionStorage.getItem(storageKey);
      if (stored !== null) return JSON.parse(stored) as T;
    } catch {
      // Fall through to the page default when storage is unavailable or invalid.
    }
    return typeof initialValue === "function" ? (initialValue as () => T)() : initialValue;
  });

  useEffect(() => {
    try {
      window.sessionStorage.setItem(storageKey, JSON.stringify(value));
    } catch {
      // Keep the list usable when storage is unavailable or full.
    }
  }, [storageKey, value]);

  return [value, setValue];
}
