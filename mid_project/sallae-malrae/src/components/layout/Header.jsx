"use client";

import { useState, useEffect, useRef } from "react";
import Image from "next/image";
import Link from "next/link";
import { useAuth } from "@/contexts/AuthContext";

const DEFAULT_NAV_ITEMS = [
  { key: "dashboard", href: "/dashboard", label: "대시보드" },
  { key: "coolingoff", href: "/coolingoff", label: "쿨링오프" },
];

function getNavItemClassName(isActive) {
  return [
    "rounded-xl px-4 py-1.5 text-sm font-semibold transition-colors",
    isActive ? "bg-[#7aaa8a] text-white" : "text-black hover:bg-[#E8F1E9]",
  ].join(" ");
}

export default function Header({
  variant = "default",
  activeMenu,
  navItems,
  rightSlot,
  logoHref = "auto",
  transparent = false,
  className = "",
}) {
  const { logout } = useAuth();
  const [visible, setVisible] = useState(true);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const lastScrollY = useRef(0);

  const handleLogout = async () => {
    if (isLoggingOut) return;
    setIsLoggingOut(true);
    setMobileMenuOpen(false);
    await logout();
    // 전체 새로고침으로 React 트리/진행 중 fetch/refreshPromise를 완전히 폐기.
    // router.push는 트리를 유지해 stale fetch가 401→refresh를 트리거하면 쿠키가 다시 박힘.
    window.location.replace("/auth/login");
  };

  useEffect(() => {
    const handleScroll = () => {
      const currentY = window.scrollY;
      if (currentY < 10) {
        setVisible(true);
      } else if (currentY < lastScrollY.current) {
        setVisible(true);
      } else {
        setVisible(false);
      }
      lastScrollY.current = currentY;
    };

    window.addEventListener("scroll", handleScroll, { passive: true });
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  // 모바일 메뉴 열릴 때 스크롤 잠금
  useEffect(() => {
    document.body.style.overflow = mobileMenuOpen ? "hidden" : "";
    return () => { document.body.style.overflow = ""; };
  }, [mobileMenuOpen]);

  const isAuthPage = variant === "auth";
  const resolvedLogoHref = logoHref === "auto" ? (isAuthPage || transparent ? "/" : "/dashboard") : logoHref;
  const resolvedNavItems = navItems ?? (isAuthPage ? [] : DEFAULT_NAV_ITEMS);
  const logoutButton = (
    <button
      type="button"
      onClick={handleLogout}
      disabled={isLoggingOut}
      className="cursor-pointer text-sm font-normal text-[#5D7A62] hover:underline disabled:cursor-not-allowed disabled:opacity-60"
    >
      {isLoggingOut ? "로그아웃 중..." : "로그아웃"}
    </button>
  );
  const resolvedRightSlot = rightSlot ?? (!isAuthPage ? logoutButton : null);

  return (
    <>
      <header
        className={`fixed left-0 top-0 z-40 w-full px-6 md:px-10 py-2 transition-transform duration-300 ease-in-out ${
          transparent ? "bg-transparent" : "bg-[#f7fbf8]"
        } ${visible || mobileMenuOpen ? "translate-y-0" : "-translate-y-full"} ${className}`}
      >
        <div className="flex items-center justify-between gap-8">
          {/* 로고 + 데스크톱 nav */}
          <div className="flex items-end gap-8">
            <Link href={resolvedLogoHref} onClick={() => setMobileMenuOpen(false)}>
              <Image
                src="/images/logo.png"
                alt="살래말래"
                width={68}
                height={68}
                priority
                className="h-[45px] w-auto"
              />
            </Link>

            {resolvedNavItems.length > 0 && (
              <nav className="hidden md:flex items-center gap-2">
                {resolvedNavItems.map((item) => (
                  <Link
                    key={item.key ?? item.href}
                    href={item.href}
                    className={getNavItemClassName(activeMenu === item.key)}
                  >
                    {item.label}
                  </Link>
                ))}
              </nav>
            )}
          </div>

          {/* 데스크톱 우측 슬롯 */}
          <div className="hidden md:block">{resolvedRightSlot}</div>

          {/* 모바일 햄버거 버튼 */}
          {!isAuthPage && (
            <button
              type="button"
              aria-label={mobileMenuOpen ? "메뉴 닫기" : "메뉴 열기"}
              aria-expanded={mobileMenuOpen}
              onClick={() => setMobileMenuOpen((prev) => !prev)}
              className="md:hidden flex flex-col justify-center gap-[5px] w-8 h-8"
            >
              <span
                className={`block h-0.5 w-full bg-[#1a3a2e] rounded transition-all duration-300 ${
                  mobileMenuOpen ? "translate-y-[7px] rotate-45" : ""
                }`}
              />
              <span
                className={`block h-0.5 w-full bg-[#1a3a2e] rounded transition-all duration-300 ${
                  mobileMenuOpen ? "opacity-0" : ""
                }`}
              />
              <span
                className={`block h-0.5 w-full bg-[#1a3a2e] rounded transition-all duration-300 ${
                  mobileMenuOpen ? "-translate-y-[7px] -rotate-45" : ""
                }`}
              />
            </button>
          )}
        </div>
      </header>

      {/* 모바일 메뉴 오버레이 */}
      {!isAuthPage && (
        <div
          className={`fixed inset-0 z-30 flex flex-col pt-20 px-8 bg-[#f7fbf8] transition-transform duration-300 ease-in-out md:hidden ${
            mobileMenuOpen ? "translate-x-0" : "translate-x-full"
          }`}
        >
          <nav className="flex flex-col gap-2 mb-8">
            {resolvedNavItems.map((item) => (
              <Link
                key={item.key ?? item.href}
                href={item.href}
                onClick={() => setMobileMenuOpen(false)}
                className={`rounded-xl px-4 py-3 text-base font-semibold transition-colors ${
                  activeMenu === item.key
                    ? "bg-[#7aaa8a] text-white"
                    : "text-black hover:bg-[#E8F1E9]"
                }`}
              >
                {item.label}
              </Link>
            ))}
          </nav>
          <div>{resolvedRightSlot}</div>
        </div>
      )}
    </>
  );
}
