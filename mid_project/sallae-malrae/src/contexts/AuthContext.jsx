"use client";

import { createContext, useContext, useState } from "react";

// AT/RT는 HttpOnly 쿠키로만 다뤄짐(미들웨어 보호). 클라이언트는 UI 표시용 user만 보관.
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);

  const login = (userInfo) => setUser(userInfo);

  // 호출부는 logout 이후 window.location.replace로 전체 새로고침을 트리거함.
  // 새로고침이 React 트리/진행 중 fetch/refreshPromise를 모두 폐기하므로 setUser(null) 불필요.
  const logout = async () => {
    try {
      await fetch("/api/auth/logout", { method: "POST", credentials: "same-origin" });
    } catch {
      // 네트워크 오류여도 호출부는 그대로 진행(사용자 의도 보존).
    }
  };

  return (
    <AuthContext.Provider value={{ user, isLoggedIn: user !== null, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
