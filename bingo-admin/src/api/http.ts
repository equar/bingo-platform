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
    const isPasswordVerificationFailure = error?.config?.url?.endsWith('/api/v1/admin/me/password');
    if (error?.response?.status === 401 && !isPasswordVerificationFailure) {
      clearSession();
    }
    return Promise.reject(parseApiError(error));
  },
);
