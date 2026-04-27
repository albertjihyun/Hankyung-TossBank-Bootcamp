'use client';

import { create } from 'zustand';

interface HoldingState {
  stockId: number;
  quantity: number;
  avgBuyPrice: number;
}

interface BattleState {
  cashBalance: number;
  holdings: HoldingState[];
  tradeQty: Record<number, number>;
  setPortfolio: (cashBalance: number, holdings: HoldingState[]) => void;
  setTradeQty: (stockId: number, qty: number) => void;
  optimisticBuy: (stockId: number, price: number, qty: number) => void;
  optimisticSell: (stockId: number, price: number, qty: number) => void;
  rollback: (cashBalance: number, holdings: HoldingState[]) => void;
  reset: () => void;
}

export const useBattleStore = create<BattleState>()((set) => ({
  cashBalance: 0,
  holdings: [],
  tradeQty: {},

  setPortfolio: (cashBalance, holdings) => set({ cashBalance, holdings }),

  setTradeQty: (stockId, qty) =>
    set((s) => ({ tradeQty: { ...s.tradeQty, [stockId]: Math.max(0, qty) } })),

  optimisticBuy: (stockId, price, qty) =>
    set((s) => {
      const cost = price * qty;
      const existing = s.holdings.find((h) => h.stockId === stockId);
      let holdings: HoldingState[];
      if (existing) {
        const newQty = existing.quantity + qty;
        const newAvg = Math.round(
          (existing.avgBuyPrice * existing.quantity + price * qty) / newQty
        );
        holdings = s.holdings.map((h) =>
          h.stockId === stockId ? { ...h, quantity: newQty, avgBuyPrice: newAvg } : h
        );
      } else {
        holdings = [...s.holdings, { stockId, quantity: qty, avgBuyPrice: price }];
      }
      return { cashBalance: s.cashBalance - cost, holdings };
    }),

  optimisticSell: (stockId, price, qty) =>
    set((s) => {
      const gain = price * qty;
      const existing = s.holdings.find((h) => h.stockId === stockId);
      if (!existing) return s;
      const newQty = existing.quantity - qty;
      const holdings =
        newQty === 0
          ? s.holdings.filter((h) => h.stockId !== stockId)
          : s.holdings.map((h) =>
              h.stockId === stockId ? { ...h, quantity: newQty } : h
            );
      return { cashBalance: s.cashBalance + gain, holdings };
    }),

  rollback: (cashBalance, holdings) => set({ cashBalance, holdings }),

  reset: () => set({ cashBalance: 0, holdings: [], tradeQty: {} }),
}));
