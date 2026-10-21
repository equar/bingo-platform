import { AxiosError, AxiosHeaders, type AxiosAdapter, type AxiosResponse } from "axios";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { http } from "./http";
import { clearSession, readSession, writeSession } from "./session";
const session = {
  accessToken: "disposable-test-token",
  tokenType: "Bearer",
  user: {
    id: 1,
    displayName: "Test Admin",
    phoneNumber: "251911000001",
    roles: ["ADMIN"],
    status: "ACTIVE",
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
};

let originalAdapter: typeof http.defaults.adapter;

function successfulAdapter(requests: string[]) {
  return (async (config) => {
    requests.push(String(config.headers.get("Authorization") ?? ""));
    return {
      data: { ok: true },
      status: 200,
      statusText: "OK",
      headers: new AxiosHeaders(),
      config,
    };
  }) satisfies AxiosAdapter;
}

function forbiddenAdapter(status: number): AxiosAdapter {
  return async (config) => {
    const response: AxiosResponse = {
      data: { status, message: status === 401 ? "Unauthorized" : "Forbidden" },
      status,
      statusText: status === 401 ? "Unauthorized" : "Forbidden",
      headers: new AxiosHeaders(),
      config,
    };
    return Promise.reject(new AxiosError(response.statusText, "ERR_BAD_REQUEST", config, undefined, response));
  };
}

describe("HTTP client session handling", () => {
  beforeEach(() => {
    originalAdapter = http.defaults.adapter;
    clearSession();
  });

  afterEach(() => {
    http.defaults.adapter = originalAdapter;
    clearSession();
  });

  it("adds the bearer token to authenticated requests", async () => {
    const authorizationHeaders: string[] = [];
    writeSession(session);
    http.defaults.adapter = successfulAdapter(authorizationHeaders);

    await http.get("/api/v1/admin/players");

    expect(authorizationHeaders).toEqual(["Bearer disposable-test-token"]);
  });

  it("does not clear an admin session after a permission denial", async () => {
    writeSession(session);
    http.defaults.adapter = forbiddenAdapter(403);

    await expect(http.get("/api/v1/admin/players")).rejects.toMatchObject({
      status: 403,
      message: "Forbidden",
    });

    expect(readSession()).toEqual(session);
  });

  it("clears an expired session after an unauthorized response", async () => {
    writeSession(session);
    http.defaults.adapter = forbiddenAdapter(401);

    await expect(http.get("/api/v1/admin/players")).rejects.toMatchObject({
      status: 401,
      message: "Unauthorized",
    });

    expect(readSession()).toBeNull();
  });
});
