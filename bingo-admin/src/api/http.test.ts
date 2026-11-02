import { AxiosError, AxiosHeaders, type AxiosAdapter, type AxiosResponse, type InternalAxiosRequestConfig } from "axios";
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
      message: "You don’t have permission to perform this action.",
    });

    expect(readSession()).toEqual(session);
  });

  it("clears an expired session after an unauthorized response", async () => {
    writeSession(session);
    http.defaults.adapter = forbiddenAdapter(401);

    await expect(http.get("/api/v1/admin/players")).rejects.toMatchObject({
      status: 401,
      message: "Your session has expired. Please sign in again.",
    });

    expect(readSession()).toBeNull();
  });

  it("does not expire a replacement session after a stale request returns 401", async () => {
    const previousSession = { ...session, accessToken: "previous-session-token" };
    const replacementSession = { ...session, accessToken: "replacement-session-token" };
    let requestConfig: InternalAxiosRequestConfig | undefined;
    let rejectRequest: ((error: AxiosError) => void) | undefined;
    let resolveStarted: (() => void) | undefined;
    const requestStarted = new Promise<void>((resolve) => {
      resolveStarted = resolve;
    });

    writeSession(previousSession);
    http.defaults.adapter = (async (config) => {
      requestConfig = config;
      return new Promise<AxiosResponse>((_resolve, reject) => {
        rejectRequest = (error) => reject(error);
        resolveStarted?.();
      });
    }) satisfies AxiosAdapter;

    const request = http.get("/api/v1/admin/players");
    await requestStarted;
    writeSession(replacementSession);

    const response: AxiosResponse = {
      data: { status: 401 },
      status: 401,
      statusText: "Unauthorized",
      headers: new AxiosHeaders(),
      config: requestConfig!,
    };
    rejectRequest?.(
      new AxiosError("Unauthorized", "ERR_BAD_REQUEST", requestConfig, undefined, response),
    );

    await expect(request).rejects.toMatchObject({ status: 401 });
    expect(readSession()).toEqual(replacementSession);
  });

  it("keeps the admin session after an incorrect current-password response", async () => {
    writeSession(session);
    http.defaults.adapter = forbiddenAdapter(401);

    await expect(http.put("/api/v1/admin/me/password", {
      currentPassword: "wrong-current-password",
      newPassword: "NewPassword123!",
    })).rejects.toMatchObject({
      status: 401,
      message: "The current password is incorrect.",
    });

    expect(readSession()).toEqual(session);
  });
});
