"use client";
// 클라이언트(CSR) 전용 API 헬퍼: 같은 오리진의 /api/* (BFF) 호출.
// 401 시 리프레시 토큰으로 1회 재발급 후 재시도.

import type { CartView, OrderView, User } from "./format";

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const opts: RequestInit = {
    method,
    cache: "no-store",
    headers: body ? { "content-type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  };

  let res = await fetch(`/api${path}`, opts);

  if (res.status === 401 && path !== "/auth/me") {
    const refreshed = await fetch("/api/auth/refresh", { method: "POST" });
    if (refreshed.ok) {
      res = await fetch(`/api${path}`, opts); // 새 access 쿠키로 재시도
    }
  }

  if (res.status === 204) return undefined as T;
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error((data as { error?: string }).error ?? `요청 실패 (${res.status})`);
  }
  return data as T;
}

// auth
export const apiLogin = (email: string, password: string) =>
  request<User>("POST", "/auth/login", { email, password });
export const apiSignup = (email: string, password: string, name: string) =>
  request<User>("POST", "/auth/signup", { email, password, name });
export const apiLogout = () => request<void>("POST", "/auth/logout");
export const apiMe = () => request<User>("GET", "/auth/me");

// cart
export const apiGetCart = () => request<CartView>("GET", "/cart");
export const apiCartCount = () => request<{ count: number }>("GET", "/cart/count");
export const apiAddToCart = (productId: number, quantity: number) =>
  request<CartView>("POST", "/cart", { productId, quantity });
export const apiUpdateCartItem = (itemId: number, quantity: number) =>
  request<CartView>("PATCH", `/cart/${itemId}`, { quantity });
export const apiRemoveCartItem = (itemId: number) =>
  request<CartView>("DELETE", `/cart/${itemId}`);

// orders
export const apiCheckout = (recipientName: string, phone: string, address: string) =>
  request<OrderView>("POST", "/orders", { recipientName, phone, address });
export const apiGetOrders = () => request<OrderView[]>("GET", "/orders");
