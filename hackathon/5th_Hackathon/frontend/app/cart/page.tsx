"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { useAuth } from "@/providers/AuthProvider";
import { apiGetCart, apiRemoveCartItem, apiUpdateCartItem } from "@/lib/client";
import { formatWon, type CartView } from "@/lib/format";
import QuantityStepper from "@/components/QuantityStepper";

export default function CartPage() {
  const { user, loading: authLoading, refreshCartCount } = useAuth();
  const router = useRouter();
  const [cart, setCart] = useState<CartView | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setCart(await apiGetCart());
    } catch (e) {
      setError(e instanceof Error ? e.message : "장바구니를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (authLoading) return;
    if (!user) {
      router.replace("/login?next=/cart");
      return;
    }
    load();
  }, [authLoading, user, router, load]);

  async function changeQty(itemId: number, qty: number) {
    setCart(await apiUpdateCartItem(itemId, qty));
    refreshCartCount();
  }
  async function remove(itemId: number) {
    setCart(await apiRemoveCartItem(itemId));
    refreshCartCount();
  }

  if (authLoading || loading) {
    return <main className="container section"><p className="muted">불러오는 중…</p></main>;
  }
  if (error) {
    return <main className="container section"><p className="form-error">{error}</p></main>;
  }

  const empty = !cart || cart.items.length === 0;

  return (
    <main className="container section">
      <p className="eyebrow">CART</p>
      <h2 className="serif page-title">장바구니</h2>

      {empty ? (
        <div className="empty-state">
          <p>장바구니가 비어 있습니다.</p>
          <Link href="/products" className="btn">쇼핑 계속하기</Link>
        </div>
      ) : (
        <div className="cart-layout">
          <ul className="cart-list">
            {cart!.items.map((it) => (
              <li key={it.id} className="cart-row">
                <Link href={`/products/${it.productId}`} className="cart-thumb">
                  <Image src={it.imageUrl} alt={it.name} fill sizes="96px" unoptimized />
                </Link>
                <div className="cart-info">
                  <Link href={`/products/${it.productId}`} className="cart-name">{it.name}</Link>
                  <div className="cart-price">
                    {it.discountRate > 0 && <span className="sale">{it.discountRate}%</span>}
                    <span>{formatWon(it.finalPrice)}</span>
                  </div>
                  {it.soldOut && <span className="soldout-tag">품절</span>}
                </div>
                <QuantityStepper value={it.quantity} max={Math.max(1, it.stock)} onChange={(q) => changeQty(it.id, q)} />
                <div className="cart-line-total">{formatWon(it.lineTotal)}</div>
                <button className="link-btn remove" onClick={() => remove(it.id)}>삭제</button>
              </li>
            ))}
          </ul>

          <aside className="cart-summary">
            <div className="summary-row">
              <span>총 수량</span>
              <span>{cart!.totalQuantity}개</span>
            </div>
            <div className="summary-row total">
              <span>결제 예정 금액</span>
              <span>{formatWon(cart!.totalAmount)}</span>
            </div>
            <button className="btn primary full" onClick={() => router.push("/checkout")}>
              주문하기
            </button>
          </aside>
        </div>
      )}
    </main>
  );
}
