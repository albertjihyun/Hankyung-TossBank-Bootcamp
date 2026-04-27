'use client';

import { Holding, StockPrice } from '@/types';
import { StockCard } from '@/components/battle/StockCard';

interface StockGridProps {
  prices: StockPrice[];
  holdings: Holding[];
  tradeQty: Record<number, number>;
  disabled?: boolean;
  tradingStockId?: number | null;
  onQuantityChange: (stockId: number, next: number) => void;
  onTrade: (stock: StockPrice, type: 'buy' | 'sell') => void;
}

export function StockGrid({
  prices,
  holdings,
  tradeQty,
  disabled,
  tradingStockId,
  onQuantityChange,
  onTrade,
}: StockGridProps) {
  return (
    <section className="grid gap-3 sm:grid-cols-2">
      {prices.map((stock) => {
        const holding = holdings.find((item) => item.stockId === stock.stockId);
        const quantity = tradeQty[stock.stockId] ?? 0;

        return (
          <StockCard
            key={stock.stockId}
            stock={stock}
            holding={holding}
            quantity={quantity}
            disabled={disabled || tradingStockId === stock.stockId}
            onDecrease={() => onQuantityChange(stock.stockId, Math.max(0, quantity - 1))}
            onIncrease={() => onQuantityChange(stock.stockId, quantity + 1)}
            onTrade={(type) => onTrade(stock, type)}
          />
        );
      })}
    </section>
  );
}
