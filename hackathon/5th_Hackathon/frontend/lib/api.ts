// 서버 컴포넌트(SSR) 전용: Spring을 직접 호출(브라우저는 BFF만 사용).
import type { Product, ProductPage } from "./format";

const API_BASE = process.env.API_BASE ?? "http://localhost:8080";

export async function fetchProducts(params: {
  page?: number;
  size?: number;
  category?: string;
}): Promise<ProductPage> {
  const qs = new URLSearchParams();
  qs.set("page", String(params.page ?? 0));
  qs.set("size", String(params.size ?? 40));
  if (params.category) qs.set("category", params.category);
  const res = await fetch(`${API_BASE}/api/products?${qs.toString()}`, { cache: "no-store" });
  if (!res.ok) throw new Error(`목록 조회 실패: ${res.status}`);
  return res.json();
}

export async function fetchProduct(id: number | string): Promise<Product | null> {
  const res = await fetch(`${API_BASE}/api/products/${id}`, { cache: "no-store" });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`상세 조회 실패: ${res.status}`);
  return res.json();
}

export async function fetchCategories(): Promise<string[]> {
  try {
    const res = await fetch(`${API_BASE}/api/categories`, { cache: "no-store" });
    if (!res.ok) return [];
    return res.json();
  } catch {
    return [];
  }
}
