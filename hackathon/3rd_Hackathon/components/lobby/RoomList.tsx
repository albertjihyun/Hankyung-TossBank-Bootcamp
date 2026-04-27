'use client';

import { useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { CreateRoomModal } from '@/components/lobby/CreateRoomModal';
import { RoomCard } from '@/components/lobby/RoomCard';
import { showToast } from '@/components/ui/Toast';
import { useRoomPoll } from '@/hooks/useRoomPoll';
import { useAuthStore } from '@/store/authStore';
import { ApiResponse, ActiveRoom, Room, User } from '@/types';

interface MeResponse {
  user: User;
  activeRoom: ActiveRoom | null;
}

async function fetchMe(token: string | null) {
  const res = await fetch('/api/auth/me', {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    cache: 'no-store',
  });
  const json: ApiResponse<MeResponse> = await res.json();
  if (!json.success) {
    throw new Error(json.error.code);
  }
  return json.data;
}

export function RoomList() {
  const router = useRouter();
  const token = useAuthStore((state) => state.token);
  const [modalOpen, setModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [joiningRoomId, setJoiningRoomId] = useState<number | null>(null);

  const meQuery = useQuery({
    queryKey: ['me', token],
    queryFn: () => fetchMe(token),
    retry: false,
  });

  useEffect(() => {
    const activeRoom = meQuery.data?.activeRoom;
    if (!activeRoom) return;

    if (activeRoom.status === 'waiting') {
      router.replace(`/room/${activeRoom.roomId}`);
      return;
    }

    if (activeRoom.status === 'active' && activeRoom.battleId) {
      router.replace(`/battle/${activeRoom.battleId}`);
    }
  }, [meQuery.data, router]);

  const roomPollQuery = useRoomPoll(Boolean(token) && !meQuery.isLoading && !meQuery.data?.activeRoom);

  useEffect(() => {
    if (!roomPollQuery.error) return;
    showToast('방 목록 갱신 중 연결이 불안정해요. 자동 재시도 중입니다.', 'error');
  }, [roomPollQuery.error]);

  const rooms: Room[] = useMemo(() => roomPollQuery.data?.rooms ?? [], [roomPollQuery.data?.rooms]);

  const handleCreateRoom = async (title: string) => {
    if (!title.trim()) {
      showToast('방 이름을 입력해주세요.', 'error');
      return;
    }

    setCreating(true);
    try {
      const res = await fetch('/api/rooms', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ title }),
      });
      const json: ApiResponse<{ roomId: number }> = await res.json();
      if (!json.success) {
        showToast(json.error.message, 'error');
        return;
      }
      setModalOpen(false);
      router.push(`/room/${json.data.roomId}`);
    } finally {
      setCreating(false);
    }
  };

  const handleJoinRoom = async (roomId: number) => {
    setJoiningRoomId(roomId);
    try {
      const res = await fetch(`/api/rooms/${roomId}/join`, {
        method: 'POST',
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      const json: ApiResponse<{ roomId: number; battleId?: number }> = await res.json();
      if (!json.success) {
        showToast(json.error.message, 'error');
        return;
      }
      if (json.data.battleId) {
        router.push(`/battle/${json.data.battleId}`);
      } else {
        router.push(`/room/${json.data.roomId}`);
      }
    } finally {
      setJoiningRoomId(null);
    }
  };

  return (
    <main className="mx-auto max-w-6xl px-6 py-10">
      <section className="flex flex-col gap-4 rounded-[2rem] border border-white/10 bg-white/5 p-6 shadow-xl backdrop-blur md:flex-row md:items-center md:justify-between">
        <div>
          <p className="text-sm uppercase tracking-[0.24em] text-sky-300">Lobby</p>
          <h1 className="mt-2 text-3xl font-black text-white">대기 중인 방 {rooms.length}개</h1>
          <p className="mt-2 text-sm text-slate-400">방 목록은 1초 간격으로 갱신됩니다.</p>
        </div>
        <button
          onClick={() => setModalOpen(true)}
          className="rounded-2xl bg-sky-500 px-5 py-3 text-sm font-semibold text-white transition hover:bg-sky-400"
        >
          방 만들기
        </button>
      </section>

      <section className="mt-8 grid gap-4 md:grid-cols-2">
        {rooms.length > 0 ? (
          rooms.map((room) => (
            <RoomCard
              key={room.id}
              room={room}
              joiningRoomId={joiningRoomId}
              onJoin={handleJoinRoom}
            />
          ))
        ) : (
          <div className="col-span-full rounded-[2rem] border border-dashed border-white/10 bg-slate-900/50 p-10 text-center text-slate-400">
            아직 열린 방이 없어요. 첫 번째 방을 만들어보세요.
          </div>
        )}
      </section>

      <CreateRoomModal
        open={modalOpen}
        loading={creating}
        onClose={() => setModalOpen(false)}
        onSubmit={handleCreateRoom}
      />
    </main>
  );
}
