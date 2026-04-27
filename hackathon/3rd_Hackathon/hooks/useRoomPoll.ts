'use client';

import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '@/store/authStore';
import { Room } from '@/types';

interface RoomPollData {
  rooms: Room[];
  timestamp: number;
}

async function fetchRoomsPoll(token: string | null): Promise<RoomPollData> {
  const res = await fetch('/api/rooms/poll', {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    cache: 'no-store',
  });

  const json = await res.json();
  if (!res.ok || !json?.success) {
    throw new Error(json?.error?.message ?? 'room poll failed');
  }
  return json.data;
}

export function useRoomPoll(enabled = true) {
  const token = useAuthStore((s) => s.token);

  return useQuery({
    queryKey: ['rooms-poll', token],
    queryFn: () => fetchRoomsPoll(token),
    enabled: enabled && Boolean(token),
    retry: 2,
    refetchInterval: 1000,
    refetchIntervalInBackground: true,
    staleTime: 0,
  });
}
