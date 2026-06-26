"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from "react";
import {
  apiCartCount,
  apiLogin,
  apiLogout,
  apiMe,
  apiSignup,
} from "@/lib/client";
import type { User } from "@/lib/format";

type AuthState = {
  user: User | null;
  loading: boolean;
  cartCount: number;
  login: (email: string, password: string) => Promise<void>;
  signup: (email: string, password: string, name: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshCartCount: () => Promise<void>;
};

const AuthContext = createContext<AuthState | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [cartCount, setCartCount] = useState(0);

  const refreshCartCount = useCallback(async () => {
    try {
      const { count } = await apiCartCount();
      setCartCount(count);
    } catch {
      setCartCount(0);
    }
  }, []);

  // 최초 마운트: 세션(쿠키) 기반으로 로그인 상태 복원
  useEffect(() => {
    (async () => {
      try {
        const me = await apiMe();
        setUser(me);
        await refreshCartCount();
      } catch {
        setUser(null);
      } finally {
        setLoading(false);
      }
    })();
  }, [refreshCartCount]);

  const login = useCallback(
    async (email: string, password: string) => {
      const me = await apiLogin(email, password);
      setUser(me);
      await refreshCartCount();
    },
    [refreshCartCount]
  );

  const signup = useCallback(
    async (email: string, password: string, name: string) => {
      await apiSignup(email, password, name);
      // 가입 후 자동 로그인
      const me = await apiLogin(email, password);
      setUser(me);
      await refreshCartCount();
    },
    [refreshCartCount]
  );

  const logout = useCallback(async () => {
    try {
      await apiLogout();
    } finally {
      setUser(null);
      setCartCount(0);
    }
  }, []);

  return (
    <AuthContext.Provider
      value={{ user, loading, cartCount, login, signup, logout, refreshCartCount }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
