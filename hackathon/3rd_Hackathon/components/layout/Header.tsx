'use client';

import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/store/authStore';

export function Header() {
  const router = useRouter();
  const { nickname, clearAuth } = useAuthStore();

  const handleLogout = async () => {
    await fetch('/api/auth/logout', { method: 'POST' }).catch(() => {});
    clearAuth();
    document.cookie = 'token=; Path=/; Max-Age=0';
    router.push('/');
  };

  return (
    <header className="flex h-14 items-center justify-between border-b border-gray-800 bg-gray-950 px-6">
      <span
        className="cursor-pointer text-lg font-bold tracking-tight text-white"
        onClick={() => router.push('/lobby')}
      >
        StockBattle
      </span>
      <div className="flex items-center gap-4">
        <span className="text-sm text-gray-300">{nickname}</span>
        <button
          onClick={handleLogout}
          className="text-sm text-gray-400 transition-colors hover:text-white"
        >
          로그아웃
        </button>
      </div>
    </header>
  );
}
