'use client';

import { ResultRanking } from '@/types';
import { formatCurrency, formatPercent } from '@/lib/format';

export function RankingList({ rankings }: { rankings: ResultRanking[] }) {
  return (
    <div className="space-y-4">
      {rankings.map((item) => (
        <div
          key={item.userId}
          className={`rounded-[1.75rem] border p-5 ${
            item.rank === 1
              ? 'border-amber-300/30 bg-amber-300/10'
              : item.isMe
                ? 'border-sky-400/30 bg-sky-400/10'
                : 'border-white/10 bg-slate-900/70'
          }`}
        >
          <div className="flex items-center justify-between gap-3">
            <div>
              <div className="text-sm text-slate-400">#{item.rank}</div>
              <div className="text-xl font-bold text-white">
                {item.nickname} {item.isMe ? '(나)' : ''}
              </div>
            </div>
            <div className="text-right">
              <div className="text-lg font-semibold text-white">{formatCurrency(item.finalAssets)}</div>
              <div className={item.changeRate >= 0 ? 'text-sm text-emerald-300' : 'text-sm text-rose-300'}>
                {formatPercent(item.changeRate)}
              </div>
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
