import { renderHook, act } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useIdempotencyKey } from "./useIdempotencyKey";

describe("useIdempotencyKey", () => {
  it("retains a key until reset is called", () => {
    vi.stubGlobal("crypto", { randomUUID: vi.fn(() => "key-1") });

    const { result } = renderHook(() => useIdempotencyKey());
    expect(result.current.getKey()).toBe("key-1");
    expect(result.current.getKey()).toBe("key-1");

    act(() => result.current.resetKey());
    vi.mocked(globalThis.crypto.randomUUID).mockReturnValueOnce("key-2");

    expect(result.current.getKey()).toBe("key-2");
  });
});
