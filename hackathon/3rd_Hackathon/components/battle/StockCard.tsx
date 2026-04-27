'use client';

import { Sparkline } from '@/components/battle/Sparkline';
import { Holding, StockPrice } from '@/types';
import { formatCurrency, formatPercent } from '@/lib/format';

const STOCK_COLORS: Record<string, string> = {
  'SAFE-X': '#22c55e',
  'GROW-X': '#f97316',
  'VOLT-X': '#38bdf8',
  'MOON-X': '#fb7185',
};

interface StockCardProps {
  stock: StockPrice;
  holding?: Holding;
  quantity: number;
  disabled?: boolean;
  onDecrease: () => void;
  onIncrease: () => void;
  onTrade: (type: 'buy' | 'sell') => void;
}

export function StockCard({
  stock,
  holding,
  quantity,
  disabled,
  onDecrease,
  onIncrease,
  onTrade,
}: StockCardProps) {
  const color = STOCK_COLORS[stock.symbol] ?? '#38bdf8';
  const positive = stock.changeRate >= 0;
  const holdText = holding
    ? `보유 ${holding.quantity}주 · 평균 ${formatCurrency(holding.avgBuyPrice)}`
    : '미보유';

  return (
    <article className="rounded-[1.75rem] border border-white/10 bg-slate-900/85 p-5 shadow-lg">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="text-sm font-medium uppercase tracking-[0.22em] text-slate-400">{stock.symbol}</div>
          <div className="mt-2 text-2xl font-black text-white">{formatCurrency(stock.currentPrice)}</div>
        </div>
        <span
          className={`rounded-full px-3 py-1 text-xs font-semibold ${
            positive ? 'bg-emerald-400/15 text-emerald-200' : 'bg-rose-400/15 text-rose-200'
          }`}
        >
          {formatPercent(stock.changeRate)}
        </span>
      </div>

      <div className="mt-4">
        <Sparkline
          points={stock.history.map((item) => item.price)}
          stroke={color}
          gradientId={`sg-${stock.stockId}`}
        />
      </div>

      <div className="mt-4 text-sm text-slate-400">{holdText}</div>

      <div className="mt-5 flex items-center justify-between gap-3">
        <button
          onClick={() => onTrade('sell')}
          disabled={disabled || quantity <= 0}
          className="rounded-xl border border-rose-400/30 bg-rose-400/10 px-4 py-2 text-sm font-semibold text-rose-100 transition hover:bg-rose-400/20 disabled:cursor-not-allowed disabled:opacity-40"
        >
          매도
        </button>
        <div className="flex items-center gap-3 rounded-xl border border-white/10 bg-slate-950/80 px-3 py-2">
          <button onClick={onDecrease} className="text-lg text-slate-300">
            -
          </button>
          <span className="min-w-8 text-center text-sm font-semibold text-white">{quantity}</span>
          <button onClick={onIncrease} className="text-lg text-slate-300">
            +
          </button>
        </div>
        <button
          onClick={() => onTrade('buy')}
          disabled={disabled || quantity <= 0}
          className="rounded-xl bg-sky-500 px-4 py-2 text-sm font-semibold text-white transition hover:bg-sky-400 disabled:cursor-not-allowed disabled:opacity-40"
        >
          매수
        </button>
      </div>
    </article>
  );
}
