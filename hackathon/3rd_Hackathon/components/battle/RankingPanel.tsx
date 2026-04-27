'use client';

import { Ranking } from '@/types';
import { formatCurrency, formatPercent } from '@/lib/format';

export function RankingPanel({ rankings, myUserId }: { rankings: Ranking[]; myUserId: number | null }) {
  return (
    <aside className="rounded-[2rem] border border-white/10 bg-slate-900/85 p-5 shadow-lg">
      <h2 className="text-xl font-bold text-white">실시간 순위</h2>
      <div className="mt-5 space-y-3">
        {rankings.map((item) => {
          const isMe = item.userId === myUserId;
          return (
            <div
              key={item.userId}
              className={`rounded-2xl border px-4 py-3 ${
                isMe ? 'border-sky-400/30 bg-sky-400/10' : 'border-white/10 bg-slate-950/60'
              }`}
            >
              <div className="flex items-center justify-between gap-3">
                <div>
                  <div className="text-sm text-slate-400">#{item.rank}</div>
                  <div className="text-base font-semibold text-white">{item.nickname}</div>
                </div>
                <div className="text-right">
                  <div className="text-sm font-semibold text-white">{formatCurrency(item.totalAssets)}</div>
                  <div className={item.changeRate >= 0 ? 'text-xs text-emerald-300' : 'text-xs text-rose-300'}>
                    {formatPercent(item.changeRate)}
                  </div>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </aside>
  );
}
