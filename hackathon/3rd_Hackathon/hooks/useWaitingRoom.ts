'use client';

import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '@/store/authStore';
import { RoomDetail } from '@/types';

async function fetchRoom(id: number, token: string | null): Promise<RoomDetail> {
  const res = await fetch(`/api/rooms/${id}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    cache: 'no-store',
  });
  const json = await res.json();
  if (!json.success) throw new Error(json.error?.code);
  return json.data;
}

export function useWaitingRoom(roomId: number) {
  const token = useAuthStore((s) => s.token);

  return useQuery({
    queryKey: ['room', roomId, token],
    queryFn: () => fetchRoom(roomId, token),
    refetchInterval: 1000,
    refetchIntervalInBackground: true,
    staleTime: 0,
    retry: 3,
  });
}
