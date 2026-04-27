'use client';

import { useRouter } from 'next/navigation';

export default function RoomError({ reset }: { error: Error; reset: () => void }) {
  const router = useRouter();
  return (
    <div className="mx-auto max-w-5xl px-6 py-10">
      <p className="text-red-300">대기방 정보를 불러오지 못했어요.</p>
      <div className="mt-4 flex gap-3">
        <button
          onClick={reset}
          className="rounded-2xl bg-sky-500 px-5 py-3 text-sm font-semibold text-white transition hover:bg-sky-400"
        >
          다시 시도
        </button>
        <button
          onClick={() => router.replace('/lobby')}
          className="rounded-2xl border border-white/10 px-5 py-3 text-sm font-semibold text-slate-200 transition hover:bg-white/5"
        >
          로비로
        </button>
      </div>
    </div>
  );
}
