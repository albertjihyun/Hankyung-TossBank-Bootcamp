'use client';

import { useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { showToast } from '@/components/ui/Toast';
import { useAuthStore } from '@/store/authStore';
import { ApiResponse, ResultRanking } from '@/types';
import { RankingList } from '@/components/result/RankingList';

async function fetchResult(battleId: number, token: string | null) {
  const res = await fetch(`/api/battles/${battleId}/result`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  const json: ApiResponse<{ rankings: ResultRanking[] }> = await res.json();
  if (!json.success) {
    throw new Error(json.error.code);
  }
  return json.data;
}

export function ResultScreen({ battleId }: { battleId: number }) {
  const router = useRouter();
  const token = useAuthStore((state) => state.token);

  const resultQuery = useQuery({
    queryKey: ['result', battleId],
    queryFn: () => fetchResult(battleId, token),
    retry: false,
  });

  const handleRetry = async () => {
    const res = await fetch(`/api/battles/${battleId}/retry`, {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    const json: ApiResponse<{ redirect: 'room' | 'battle' | 'lobby'; roomId?: number; battleId?: number }> =
      await res.json();

    if (!json.success) {
      showToast(json.error.message, 'error');
      return;
    }

    if (json.data.redirect === 'room' && json.data.roomId) {
      router.push(`/room/${json.data.roomId}`);
      return;
    }

    if (json.data.redirect === 'battle' && json.data.battleId) {
      router.push(`/battle/${json.data.battleId}`);
      return;
    }

    showToast('방이 닫혀서 로비로 이동합니다.', 'info');
    router.push('/lobby');
  };

  if (resultQuery.isLoading) {
    return <main className="mx-auto max-w-4xl px-6 py-10 text-slate-300">결과를 불러오는 중...</main>;
  }

  if (resultQuery.error || !resultQuery.data) {
    return <main className="mx-auto max-w-4xl px-6 py-10 text-red-300">결과를 불러오지 못했어요.</main>;
  }

  return (
    <main className="mx-auto max-w-4xl px-6 py-10">
      <section className="rounded-[2rem] border border-white/10 bg-white/5 p-8 shadow-xl backdrop-blur">
        <p className="text-sm uppercase tracking-[0.24em] text-amber-300">Final Result</p>
        <h1 className="mt-3 text-4xl font-black text-white">최종 결과</h1>
        <p className="mt-3 text-slate-400">이번 배틀의 최종 자산 순위입니다.</p>

        <div className="mt-8">
          <RankingList rankings={resultQuery.data.rankings} />
        </div>

        <div className="mt-8 flex flex-wrap gap-3">
          <button
            onClick={() => router.push('/lobby')}
            className="rounded-2xl border border-white/10 px-5 py-3 text-sm font-semibold text-slate-100 transition hover:bg-white/5"
          >
            로비로
          </button>
          <button
            onClick={handleRetry}
            className="rounded-2xl bg-sky-500 px-5 py-3 text-sm font-semibold text-white transition hover:bg-sky-400"
          >
            다시 하기
          </button>
        </div>
      </section>
    </main>
  );
}
