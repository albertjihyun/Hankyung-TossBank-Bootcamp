'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/store/authStore';
import { useWaitingRoom } from '@/hooks/useWaitingRoom';
import { PlayerSlot } from '@/components/room/PlayerSlot';
import { showToast } from '@/components/ui/Toast';
import { ApiResponse } from '@/types';

export function WaitingRoom({ roomId }: { roomId: number }) {
  const router = useRouter();
  const token = useAuthStore((state) => state.token);
  const userId = useAuthStore((state) => state.userId);
  const { data, isLoading, error } = useWaitingRoom(roomId);
  const [starting, setStarting] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const movedRef = useRef(false);

  useEffect(() => {
    if (!data) return;

    if (data.status === 'closed') {
      showToast('방장님이 나가서 방이 닫혔어요.', 'error');
      router.replace('/lobby');
      return;
    }

    const isMember = data.members.some((member) => member.userId === userId);
    if (!isMember) {
      showToast('잘못된 접근이에요.', 'error');
      router.replace('/lobby');
      return;
    }

    if (data.battleStarted && data.battleId) {
      movedRef.current = true;
      router.replace(`/battle/${data.battleId}`);
    }
  }, [data, router, userId]);

  useEffect(() => {
    const handleBeforeUnload = () => {
      if (!token || movedRef.current) return;
      navigator.sendBeacon?.(`/api/rooms/${roomId}/leave`);
    };

    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [roomId, token]);

  const slots = useMemo(() => {
    const arr = [...(data?.members ?? [])];
    while (arr.length < 4) {
      arr.push(undefined as never);
    }
    return arr;
  }, [data?.members]);

  if (isLoading) {
    return <main className="mx-auto max-w-5xl px-6 py-10 text-slate-300">대기방을 불러오는 중...</main>;
  }

  if (error || !data) {
    return <main className="mx-auto max-w-5xl px-6 py-10 text-red-300">방 정보를 불러오지 못했어요.</main>;
  }

  const isHost = data.hostId === userId;
  const canStart = isHost && data.members.length >= 2;

  const handleStart = async () => {
    setStarting(true);
    try {
      const res = await fetch(`/api/rooms/${roomId}/start`, {
        method: 'POST',
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      const json: ApiResponse<{ battleId: number }> = await res.json();
      if (!json.success) {
        showToast(json.error.message, 'error');
        return;
      }
      movedRef.current = true;
      router.push(`/battle/${json.data.battleId}`);
    } finally {
      setStarting(false);
    }
  };

  const handleLeave = async () => {
    setLeaving(true);
    try {
      const res = await fetch(`/api/rooms/${roomId}/leave`, {
        method: 'POST',
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      const json: ApiResponse<{ success: boolean }> = await res.json();
      if (!json.success) {
        showToast(json.error.message, 'error');
        return;
      }
      router.push('/lobby');
    } catch {
      showToast('방 나가기에 실패했어요. 잠시 후 다시 시도해주세요.', 'error');
    } finally {
      setLeaving(false);
    }
  };

  return (
    <main className="mx-auto max-w-5xl px-6 py-10">
      <section className="rounded-[2rem] border border-white/10 bg-white/5 p-8 shadow-xl backdrop-blur">
        <p className="text-sm uppercase tracking-[0.24em] text-sky-300">Waiting Room</p>
        <h1 className="mt-3 text-3xl font-black text-white">{data.title}</h1>
        <p className="mt-3 text-slate-400">
          참가자 {data.members.length}/4명. 2명 이상이면 시작할 수 있고, 4명이 모이면 바로 배틀이 시작됩니다.
        </p>

        <div className="mt-8 grid gap-4 sm:grid-cols-2">
          {slots.map((member, index) => (
            <PlayerSlot key={member?.userId ?? `empty-${index}`} member={member} />
          ))}
        </div>

        <div className="mt-8 flex flex-wrap gap-3">
          {isHost && (
            <button
              onClick={handleStart}
              disabled={!canStart || starting}
              className="rounded-2xl bg-emerald-500 px-5 py-3 text-sm font-semibold text-white transition hover:bg-emerald-400 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {starting ? '시작 중...' : '게임 시작'}
            </button>
          )}
          <button
            onClick={handleLeave}
            disabled={leaving}
            className="rounded-2xl border border-white/10 px-5 py-3 text-sm font-semibold text-slate-200 transition hover:bg-white/5"
          >
            {leaving ? '나가는 중...' : '방 나가기'}
          </button>
        </div>
      </section>
    </main>
  );
}
