import axios, { AxiosError, type AxiosRequestConfig } from 'axios';
import { authStore } from './auth';
import type { ApiError, TokenResponse } from './types';

const STORAGE_BASE_URL = 'lubrimanager.apiBaseUrl';

export function getApiBaseUrl(): string {
  try {
    return localStorage.getItem(STORAGE_BASE_URL) || import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
  } catch {
    return import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
  }
}

export function setApiBaseUrl(url: string) {
  try {
    localStorage.setItem(STORAGE_BASE_URL, url.replace(/\/+$/, ''));
  } catch {
    /* ignore */
  }
  api.defaults.baseURL = getApiBaseUrl();
}

export const api = axios.create({ baseURL: getApiBaseUrl() });

api.interceptors.request.use((config) => {
  const token = authStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

let refreshing: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  const { refreshToken } = authStore.getState();
  if (!refreshToken) throw new Error('no refresh token');
  const { data } = await axios.post<TokenResponse>(`${getApiBaseUrl()}/api/auth/refresh`, { refreshToken });
  authStore.getState().setAccessToken(data.accessToken, data.refreshToken);
  return data.accessToken;
}

api.interceptors.response.use(
  (r) => r,
  async (error: AxiosError<ApiError>) => {
    const original = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined;
    const status = error.response?.status;
    const url = original?.url ?? '';

    if (status === 401 && original && !original._retried && !url.includes('/api/auth/')) {
      original._retried = true;
      try {
        if (!refreshing) refreshing = refreshAccessToken().finally(() => (refreshing = null));
        const token = await refreshing;
        original.headers = { ...original.headers, Authorization: `Bearer ${token}` };
        return api.request(original);
      } catch {
        authStore.getState().clear();
      }
    }
    return Promise.reject(error);
  },
);

/** Message d'erreur lisible depuis une erreur Axios. */
export function errorMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as ApiError | undefined;
    if (data?.message) return data.message;
    if (err.code === 'ERR_NETWORK') return "Impossible de joindre le serveur. Vérifiez qu'il est démarré.";
    return err.message;
  }
  return err instanceof Error ? err.message : 'Erreur inconnue';
}

export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}
