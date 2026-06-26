"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/providers/AuthProvider";
import { apiCheckout, apiGetCart } from "@/lib/client";
import { formatWon, type CartView, type OrderView } from "@/lib/format";

export default function CheckoutPage() {
  const { user, loading: authLoading, refreshCartCount } = useAuth();
  const router = useRouter();
  const [cart, setCart] = useState<CartView | null>(null);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({ recipientName: "", phone: "", address: "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [paidOrder, setPaidOrder] = useState<OrderView | null>(null);

  const load = useCallback(async () => {
    try {
      const c = await apiGetCart();
      setCart(c);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (authLoading) return;
    if (!user) {
      router.replace("/login?next=/checkout");
      return;
    }
    setForm((f) => ({ ...f, recipientName: f.recipientName || user.name }));
    load();
  }, [authLoading, user, router, load]);

  async function pay(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const order = await apiCheckout(form.recipientName, form.phone, form.address);
      await refreshCartCount();
      setPaidOrder(order); // 결제 완료 팝업
    } catch (err) {
      setError(err instanceof Error ? err.message : "결제에 실패했습니다.");
    } finally {
      setBusy(false);
    }
  }

  if (authLoading || loading) {
    return <main className="container section"><p className="muted">불러오는 중…</p></main>;
  }
  if (!cart || cart.items.length === 0) {
    return (
      <main className="container section">
        <div className="empty-state">
          <p>주문할 상품이 없습니다.</p>
          <Link href="/products" className="btn">쇼핑하러 가기</Link>
        </div>
      </main>
    );
  }

  return (
    <main className="container section">
      <p className="eyebrow">CHECKOUT</p>
      <h2 className="serif page-title">주문 / 결제</h2>

      <div className="checkout-layout">
        <form className="checkout-form" onSubmit={pay}>
          <h3 className="block-title">배송 정보</h3>
          <label>
            받는 분
            <input value={form.recipientName} onChange={(e) => setForm({ ...form, recipientName: e.target.value })} required />
          </label>
          <label>
            연락처
            <input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} placeholder="010-0000-0000" required />
          </label>
          <label>
            주소
            <input value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} placeholder="배송지 주소" required />
          </label>
          {error && <p className="form-error">{error}</p>}
          <button type="submit" className="btn primary full" disabled={busy}>
            {busy ? "결제 중…" : `${formatWon(cart.totalAmount)} 결제하기`}
          </button>
        </form>

        <aside className="checkout-summary">
          <h3 className="block-title">주문 상품 {cart.items.length}건</h3>
          <ul className="checkout-items">
            {cart.items.map((it) => (
              <li key={it.id}>
                <span className="ci-name">{it.name}</span>
                <span className="ci-qty">x{it.quantity}</span>
                <span className="ci-amt">{formatWon(it.lineTotal)}</span>
              </li>
            ))}
          </ul>
          <div className="summary-row total">
            <span>합계</span>
            <span>{formatWon(cart.totalAmount)}</span>
          </div>
        </aside>
      </div>

      {paidOrder && (
        <div className="modal-overlay" role="dialog" aria-modal="true">
          <div className="modal-card">
            <div className="modal-check">✓</div>
            <h3 className="serif">결제가 완료되었습니다</h3>
            <p className="modal-sub">주문번호 #{paidOrder.id}</p>
            <p className="modal-amount">{formatWon(paidOrder.totalAmount)}</p>
            <div className="modal-actions">
              <button className="btn" onClick={() => router.push("/")}>홈으로</button>
              <button className="btn primary" onClick={() => router.push("/orders")}>주문내역 보기</button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
