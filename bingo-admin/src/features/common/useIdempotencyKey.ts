import { useCallback, useRef } from 'react';

export function useIdempotencyKey() {
  const keyRef = useRef<string | null>(null);

  const getKey = useCallback(() => {
    if (!keyRef.current) {
      keyRef.current = crypto.randomUUID();
    }
    return keyRef.current;
  }, []);

  const resetKey = useCallback(() => {
    keyRef.current = null;
  }, []);

  return { getKey, resetKey };
}
