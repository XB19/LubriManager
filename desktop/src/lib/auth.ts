import { create } from 'zustand';
import type { MeResponse } from './types';

const STORAGE_KEY = 'lubrimanager.auth';

interface Persisted {
  refreshToken: string;
  user: MeResponse;
}

function load(): Persisted | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Persisted) : null;
  } catch {
    return null;
  }
}

function save(value: Persisted | null) {
  try {
    if (value) localStorage.setItem(STORAGE_KEY, JSON.stringify(value));
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    /* stockage indisponible : session en mémoire seulement */
  }
}

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: MeResponse | null;
  setSession: (t: { accessToken: string; refreshToken: string; user: MeResponse }) => void;
  setAccessToken: (accessToken: string, refreshToken: string) => void;
  clear: () => void;
}

const persisted = load();

export const useAuth = create<AuthState>((set) => ({
  accessToken: null, // toujours en mémoire, jamais persisté
  refreshToken: persisted?.refreshToken ?? null,
  user: persisted?.user ?? null,
  setSession: ({ accessToken, refreshToken, user }) => {
    save({ refreshToken, user });
    set({ accessToken, refreshToken, user });
  },
  setAccessToken: (accessToken, refreshToken) => {
    set((s) => {
      if (s.user) save({ refreshToken, user: s.user });
      return { accessToken, refreshToken };
    });
  },
  clear: () => {
    save(null);
    set({ accessToken: null, refreshToken: null, user: null });
  },
}));

export const authStore = useAuth;
