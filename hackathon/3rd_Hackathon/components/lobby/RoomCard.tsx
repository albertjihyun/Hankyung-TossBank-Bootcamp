'use client';

import { Room } from '@/types';

interface RoomCardProps {
  room: Room;
  onJoin: (roomId: number) => void;
  joiningRoomId?: number | null;
}

export function RoomCard({ room, onJoin, joiningRoomId }: RoomCardProps) {
  return (
    <article className="rounded-3xl border border-white/10 bg-slate-900/70 p-5 shadow-lg">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h3 className="text-lg font-semibold text-white">{room.title}</h3>
          <p className="mt-2 text-sm text-slate-400">방장 {room.hostNickname}</p>
        </div>
        <span className="rounded-full border border-emerald-400/20 bg-emerald-400/10 px-3 py-1 text-xs font-medium text-emerald-200">
          대기중
        </span>
      </div>
      <div className="mt-5 flex items-center justify-between">
        <p className="text-sm text-slate-300">
          참가 {room.memberCount}/{room.maxMembers}
        </p>
        <button
          onClick={() => onJoin(room.id)}
          disabled={joiningRoomId === room.id || room.memberCount >= room.maxMembers}
          className="rounded-xl bg-sky-500 px-4 py-2 text-sm font-semibold text-white transition hover:bg-sky-400 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {joiningRoomId === room.id ? '입장 중...' : '참가하기'}
        </button>
      </div>
    </article>
  );
}
