'use client';

import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface AuthState {
  userId: number | null;
  nickname: string | null;
  token: string | null;
  setAuth: (userId: number, nickname: string, token: string) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      userId: null,
      nickname: null,
      token: null,
      setAuth: (userId, nickname, token) => set({ userId, nickname, token }),
      clearAuth: () => set({ userId: null, nickname: null, token: null }),
    }),
    { name: 'auth-storage' }
  )
);
