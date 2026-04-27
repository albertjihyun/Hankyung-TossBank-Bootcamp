'use client';

import { formatCurrency, formatPercent, formatRemaining } from '@/lib/format';

interface HUDProps {
  remaining: number;
  cashBalance: number;
  totalAssets: number;
  changeRate: number;
}

export function HUD({ remaining, cashBalance, totalAssets, changeRate }: HUDProps) {
  const changeClass = changeRate >= 0 ? 'text-emerald-300' : 'text-rose-300';

  return (
    <section className="grid gap-4 lg:grid-cols-4">
      <Card label="남은 시간" value={formatRemaining(remaining)} />
      <Card label="보유 현금" value={formatCurrency(cashBalance)} />
      <Card label="총 자산" value={formatCurrency(totalAssets)} />
      <Card label="수익률" value={formatPercent(changeRate)} valueClassName={changeClass} />
    </section>
  );
}

function Card({
  label,
  value,
  valueClassName,
}: {
  label: string;
  value: string;
  valueClassName?: string;
}) {
  return (
    <div className="rounded-3xl border border-white/10 bg-slate-900/80 p-5">
      <div className="text-sm text-slate-400">{label}</div>
      <div className={`mt-3 text-2xl font-black text-white ${valueClassName ?? ''}`}>{value}</div>
    </div>
  );
}
