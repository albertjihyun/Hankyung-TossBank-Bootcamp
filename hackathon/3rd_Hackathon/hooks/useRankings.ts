'use client';

import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '@/store/authStore';
import { Ranking } from '@/types';

async function fetchRankings(battleId: number, token: string | null): Promise<{ rankings: Ranking[] }> {
  const res = await fetch(`/api/battles/${battleId}/rankings`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    cache: 'no-store',
  });
  const json = await res.json();
  if (!json.success) throw new Error(json.error?.code);
  return json.data;
}

export function useRankings(battleId: number) {
  const token = useAuthStore((s) => s.token);

  return useQuery({
    queryKey: ['rankings', battleId, token],
    queryFn: () => fetchRankings(battleId, token),
    refetchInterval: 1000,
    refetchIntervalInBackground: true,
    staleTime: 0,
    retry: 3,
  });
}
