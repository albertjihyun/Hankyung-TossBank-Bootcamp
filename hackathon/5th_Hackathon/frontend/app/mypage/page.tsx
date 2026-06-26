"use client";

import { useEffect } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/providers/AuthProvider";

export default function MyPage() {
  const { user, loading, logout } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && !user) router.replace("/login?next=/mypage");
  }, [loading, user, router]);

  if (loading || !user) {
    return <main className="container section"><p className="muted">불러오는 중…</p></main>;
  }

  async function handleLogout() {
    await logout();
    router.push("/");
    router.refresh();
  }

  return (
    <main className="container section">
      <p className="eyebrow">MY PAGE</p>
      <h2 className="serif page-title">마이페이지</h2>

      <div className="mypage-card">
        <div className="mypage-row"><span>이름</span><strong>{user.name}</strong></div>
        <div className="mypage-row"><span>이메일</span><strong>{user.email}</strong></div>
        <div className="mypage-row"><span>등급</span><strong>{user.role}</strong></div>
      </div>

      <div className="mypage-links">
        <Link href="/orders" className="btn">주문 내역</Link>
        <Link href="/cart" className="btn">장바구니</Link>
        <button className="btn" onClick={handleLogout}>로그아웃</button>
      </div>
    </main>
  );
}
