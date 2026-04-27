'use client';

import { useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { HUD } from '@/components/battle/HUD';
import { RankingPanel } from '@/components/battle/RankingPanel';
import { StockGrid } from '@/components/battle/StockGrid';
import { showToast } from '@/components/ui/Toast';
import { usePrices } from '@/hooks/usePrices';
import { useRankings } from '@/hooks/useRankings';
import { useAuthStore } from '@/store/authStore';
import { useBattleStore } from '@/store/battleStore';
import { ApiResponse, Battle, Holding, Portfolio, StockPrice } from '@/types';

async function fetchBattle(battleId: number, token: string | null) {
  const res = await fetch(`/api/battles/${battleId}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  const json: ApiResponse<Battle> = await res.json();
  if (!json.success) throw new Error(json.error.code);
  return json.data;
}

async function fetchPortfolio(battleId: number, token: string | null) {
  const res = await fetch(`/api/battles/${battleId}/portfolio`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  const json: ApiResponse<Portfolio> = await res.json();
  if (!json.success) throw new Error(json.error.code);
  return json.data;
}

export function BattleScreen({ battleId }: { battleId: number }) {
  const router = useRouter();
  const token = useAuthStore((state) => state.token);
  const userId = useAuthStore((state) => state.userId);

  const cashBalance = useBattleStore((state) => state.cashBalance);
  const holdings = useBattleStore((state) => state.holdings);
  const tradeQty = useBattleStore((state) => state.tradeQty);
  const setPortfolio = useBattleStore((state) => state.setPortfolio);
  const setTradeQty = useBattleStore((state) => state.setTradeQty);
  const optimisticBuy = useBattleStore((state) => state.optimisticBuy);
  const optimisticSell = useBattleStore((state) => state.optimisticSell);
  const rollback = useBattleStore((state) => state.rollback);
  const reset = useBattleStore((state) => state.reset);

  const [remaining, setRemaining] = useState(0);
  const [trading, setTrading] = useState<number | null>(null);

  // 배틀 진입 시 이전 배틀 데이터 초기화
  useEffect(() => {
    reset();
  }, [reset]);

  const battleQuery = useQuery({
    queryKey: ['battle', battleId],
    queryFn: () => fetchBattle(battleId, token),
    retry: false,
  });

  const portfolioQuery = useQuery({
    queryKey: ['portfolio', battleId],
    queryFn: () => fetchPortfolio(battleId, token),
    retry: false,
  });

  const pricesQuery = usePrices(battleId);
  const rankingsQuery = useRankings(battleId);

  useEffect(() => {
    if (!portfolioQuery.data) return;
    setPortfolio(portfolioQuery.data.cashBalance, portfolioQuery.data.holdings);
  }, [portfolioQuery.data, setPortfolio]);

  useEffect(() => {
    const endsAt = pricesQuery.data?.endsAt ?? battleQuery.data?.endsAt;
    if (!endsAt) return;

    const update = () => {
      const diff = Math.max(0, Math.floor((new Date(endsAt).getTime() - Date.now()) / 1000));
      setRemaining(diff);
    };

    update();
    const timer = window.setInterval(update, 1000);
    return () => window.clearInterval(timer);
  }, [battleQuery.data?.endsAt, pricesQuery.data?.endsAt]);

  useEffect(() => {
    if (!pricesQuery.data?.battleEnded) return;
    showToast('배틀 종료! 결과 화면으로 이동합니다.', 'info');
    const timer = window.setTimeout(() => router.replace(`/result/${battleId}`), 3000);
    return () => window.clearTimeout(timer);
  }, [battleId, pricesQuery.data?.battleEnded, router]);

  const currentHoldings = useMemo(() => {
    const symbolMap = new Map(
      pricesQuery.data?.prices.map((p) => [p.stockId, p.symbol]) ?? []
    );
    return holdings.map((holding) => ({
      ...holding,
      symbol: symbolMap.get(holding.stockId) ?? '',
    }));
  }, [holdings, pricesQuery.data?.prices]);

  const totalAssets = useMemo(() => {
    const currentPrices = new Map(pricesQuery.data?.prices.map((item) => [item.stockId, item.currentPrice]) ?? []);
    return cashBalance + currentHoldings.reduce((sum, holding) => {
      return sum + holding.quantity * (currentPrices.get(holding.stockId) ?? holding.avgBuyPrice);
    }, 0);
  }, [cashBalance, currentHoldings, pricesQuery.data?.prices]);

  const initialCash = battleQuery.data?.initialCash ?? 1000000;
  const changeRate = (totalAssets - initialCash) / initialCash;

  const handleTrade = async (stock: StockPrice, type: 'buy' | 'sell') => {
    const quantity = tradeQty[stock.stockId] ?? 0;
    if (quantity <= 0 || !token) return;

    const previousCash = cashBalance;
    const previousHoldings = currentHoldings as Holding[];

    if (type === 'buy') {
      if (previousCash < stock.currentPrice * quantity) {
        showToast('잔액이 부족해요.', 'error');
        return;
      }
      optimisticBuy(stock.stockId, stock.currentPrice, quantity);
    } else {
      const existing = previousHoldings.find((item) => item.stockId === stock.stockId);
      if (!existing || existing.quantity < quantity) {
        showToast('보유 수량이 부족해요.', 'error');
        return;
      }
      optimisticSell(stock.stockId, stock.currentPrice, quantity);
    }

    setTrading(stock.stockId);
    try {
      const res = await fetch(`/api/battles/${battleId}/trade`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          stockId: stock.stockId,
          type,
          quantity,
        }),
      });

      const json: ApiResponse<{ cashBalance: number; holding: Holding | null }> = await res.json();
      if (!json.success) {
        rollback(previousCash, previousHoldings);
        showToast(json.error.message, 'error');
        return;
      }

      const nextHoldings = previousHoldings
        .filter((item) => item.stockId !== stock.stockId)
        .concat(
          json.data.holding
            ? [
                {
                  stockId: stock.stockId,
                  symbol: stock.symbol,
                  quantity: json.data.holding.quantity,
                  avgBuyPrice: json.data.holding.avgBuyPrice,
                },
              ]
            : []
        );

      rollback(json.data.cashBalance, nextHoldings);
      setTradeQty(stock.stockId, 0);
      void rankingsQuery.refetch();
      void pricesQuery.refetch();
    } catch {
      rollback(previousCash, previousHoldings);
      showToast('거래 처리 중 오류가 발생했어요.', 'error');
    } finally {
      setTrading(null);
    }
  };

  if (battleQuery.isLoading || portfolioQuery.isLoading) {
    return (
      <main className="mx-auto max-w-6xl px-5 py-10 text-slate-400">
        배틀 정보를 불러오는 중...
      </main>
    );
  }

  if (battleQuery.error || portfolioQuery.error || !battleQuery.data) {
    return (
      <main className="mx-auto max-w-6xl px-5 py-10 text-red-400">
        배틀 정보를 불러오지 못했어요.
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-6xl px-5 py-6">
      <HUD
        remaining={remaining}
        cashBalance={cashBalance}
        totalAssets={totalAssets}
        changeRate={changeRate}
      />

      <section className="mt-4 grid gap-4 xl:grid-cols-[1fr_260px]">
        <StockGrid
          prices={pricesQuery.data?.prices ?? []}
          holdings={currentHoldings}
          tradeQty={tradeQty}
          disabled={Boolean(pricesQuery.data?.battleEnded)}
          tradingStockId={trading}
          onQuantityChange={(stockId, next) => setTradeQty(stockId, next)}
          onTrade={(stock, type) => handleTrade(stock, type)}
        />
        <RankingPanel rankings={rankingsQuery.data?.rankings ?? []} myUserId={userId} />
      </section>
    </main>
  );
}
