'use client';

import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '@/store/authStore';
import { StockPrice } from '@/types';

interface PricesResponse {
  prices: StockPrice[];
  battleEnded: boolean;
  endsAt: string;
}

async function fetchPrices(battleId: number, token: string | null): Promise<PricesResponse> {
  const res = await fetch(`/api/battles/${battleId}/prices`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    cache: 'no-store',
  });
  const json = await res.json();
  if (!json.success) throw new Error(json.error?.code);
  return json.data;
}

export function usePrices(battleId: number) {
  const token = useAuthStore((s) => s.token);

  return useQuery({
    queryKey: ['prices', battleId, token],
    queryFn: () => fetchPrices(battleId, token),
    refetchInterval: 1000,
    refetchIntervalInBackground: true,
    staleTime: 0,
    retry: 3,
  });
}
