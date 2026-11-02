import axios from 'axios';
import { clearSession, readSession } from './session';
import { parseApiError } from './types';

function getApiBaseUrl(): string {
  const baseUrl = import.meta.env.VITE_API_BASE_URL as string | undefined;
  return baseUrl?.replace(/\/$/, '') ?? '';
}

export const apiBaseUrl = getApiBaseUrl();

export const http = axios.create({
  baseURL: apiBaseUrl,
  timeout: 30000,
});

http.interceptors.request.use((config) => {
  const session = readSession();
  if (session?.accessToken) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${session.accessToken}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const requestUrl = String(error?.config?.url ?? "");
    const isPasswordVerificationFailure = requestUrl.endsWith('/api/v1/admin/me/password');
    const isLoginRequest = /(?:^|\/)admin\/login\/?$/.test(requestUrl);
    const sentAuthorization = error?.config?.headers?.Authorization;
    const session = readSession();
    const isCurrentSessionRequest =
      typeof sentAuthorization === "string" &&
      session?.accessToken != null &&
      sentAuthorization === `Bearer ${session.accessToken}`;
    if (
      error?.response?.status === 401 &&
      !isPasswordVerificationFailure &&
      !isLoginRequest &&
      isCurrentSessionRequest
    ) {
      clearSession({ expired: true });
    }
    return Promise.reject(parseApiError(error));
  },
);
