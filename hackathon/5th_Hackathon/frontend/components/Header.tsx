"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/providers/AuthProvider";

export default function Header() {
  const { user, loading, cartCount, logout } = useAuth();
  const router = useRouter();

  async function handleLogout() {
    await logout();
    router.push("/");
    router.refresh();
  }

  return (
    <header className="site-header">
      <div className="container bar">
        <nav className="nav-left">
          <Link href="/products?category=Apparel">의류</Link>
          <Link href="/products?category=Footwear">슈즈</Link>
          <Link href="/products?category=Accessories">액세서리</Link>
        </nav>

        <Link href="/" className="logo serif">
          OLIVE
        </Link>

        <nav className="nav">
          <Link href="/products">전체상품</Link>
          {loading ? null : user ? (
            <>
              <Link href="/mypage" className="nav-user">
                {user.name}님
              </Link>
              <button onClick={handleLogout} className="link-btn">
                로그아웃
              </button>
              <Link href="/cart" className="cart-link">
                장바구니{cartCount > 0 && <span className="cart-badge">{cartCount}</span>}
              </Link>
            </>
          ) : (
            <>
              <Link href="/login">로그인</Link>
              <Link href="/signup">회원가입</Link>
              <Link href="/cart" className="cart-link">
                장바구니
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
