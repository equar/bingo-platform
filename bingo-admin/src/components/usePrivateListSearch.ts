import { useEffect, useState } from "react";

/** Keeps potentially personal free-text search terms out of browser URLs. */
export function usePrivateListSearch(key: string) {
  const storageKey = `admin-list-search:${key}`;
  const [search, setSearch] = useState(() => {
    try {
      return window.sessionStorage.getItem(storageKey) ?? "";
    } catch {
      return "";
    }
  });

  useEffect(() => {
    try {
      if (search) window.sessionStorage.setItem(storageKey, search);
      else window.sessionStorage.removeItem(storageKey);
    } catch {
      // The list remains usable when storage is unavailable.
    }
  }, [search, storageKey]);

  return [search, setSearch] as const;
}
