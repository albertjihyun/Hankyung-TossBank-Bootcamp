"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { useAuth } from "@/providers/AuthProvider";
import { apiGetOrders } from "@/lib/client";
import { formatWon, type OrderView } from "@/lib/format";

const STATUS_LABEL: Record<string, string> = {
  PAID: "결제완료",
  SHIPPING: "배송중",
  DONE: "배송완료",
};

export default function OrdersPage() {
  const { user, loading: authLoading } = useAuth();
  const router = useRouter();
  const [orders, setOrders] = useState<OrderView[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      setOrders(await apiGetOrders());
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (authLoading) return;
    if (!user) {
      router.replace("/login?next=/orders");
      return;
    }
    load();
  }, [authLoading, user, router, load]);

  if (authLoading || loading) {
    return <main className="container section"><p className="muted">불러오는 중…</p></main>;
  }

  return (
    <main className="container section">
      <p className="eyebrow">ORDERS</p>
      <h2 className="serif page-title">주문 내역</h2>

      {orders.length === 0 ? (
        <div className="empty-state">
          <p>주문 내역이 없습니다.</p>
          <Link href="/products" className="btn">쇼핑하러 가기</Link>
        </div>
      ) : (
        <ul className="order-list">
          {orders.map((o) => (
            <li key={o.id} className="order-card">
              <div className="order-head">
                <span className="order-no">주문 #{o.id}</span>
                <span className="order-date">{new Date(o.createdAt).toLocaleDateString("ko-KR")}</span>
                <span className="order-status">{STATUS_LABEL[o.status] ?? o.status}</span>
              </div>
              <ul className="order-items">
                {o.items.map((it, i) => (
                  <li key={i} className="order-item">
                    <div className="order-thumb">
                      <Image src={it.imageUrl} alt={it.productName} fill sizes="64px" unoptimized />
                    </div>
                    <span className="oi-name">{it.productName}</span>
                    <span className="oi-qty">x{it.quantity}</span>
                    <span className="oi-amt">{formatWon(it.unitPrice * it.quantity)}</span>
                  </li>
                ))}
              </ul>
              <div className="order-foot">
                <span>배송지: {o.recipientName} · {o.address}</span>
                <span className="order-total">합계 {formatWon(o.totalAmount)}</span>
              </div>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
