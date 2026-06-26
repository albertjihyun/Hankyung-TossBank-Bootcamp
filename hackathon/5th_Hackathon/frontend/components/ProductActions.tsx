"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/providers/AuthProvider";
import { apiAddToCart } from "@/lib/client";
import QuantityStepper from "./QuantityStepper";

export default function ProductActions({
  productId,
  stock,
}: {
  productId: number;
  stock: number;
}) {
  const { user, refreshCartCount } = useAuth();
  const router = useRouter();
  const [qty, setQty] = useState(1);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const soldOut = stock <= 0;

  function requireLogin(): boolean {
    if (!user) {
      router.push(`/login?next=/products/${productId}`);
      return false;
    }
    return true;
  }

  async function addToCart(): Promise<boolean> {
    if (!requireLogin()) return false;
    setBusy(true);
    setMsg(null);
    try {
      await apiAddToCart(productId, qty);
      await refreshCartCount();
      return true;
    } catch (e) {
      setMsg(e instanceof Error ? e.message : "담기에 실패했습니다.");
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function handleAdd() {
    if (await addToCart()) setMsg("장바구니에 담았습니다.");
  }

  async function handleBuyNow() {
    if (await addToCart()) router.push("/checkout");
  }

  return (
    <div className="product-actions">
      {!soldOut && (
        <div className="qty-row">
          <span className="qty-label">수량</span>
          <QuantityStepper value={qty} onChange={setQty} max={stock} />
        </div>
      )}
      <div className="actions">
        <button className="btn" onClick={handleAdd} disabled={soldOut || busy}>
          장바구니
        </button>
        <button className="btn primary" onClick={handleBuyNow} disabled={soldOut || busy}>
          {soldOut ? "품절" : "바로 구매"}
        </button>
      </div>
      {msg && <p className="action-msg">{msg}</p>}
    </div>
  );
}
