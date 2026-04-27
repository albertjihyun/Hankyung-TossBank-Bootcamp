'use client';

import { RoomMember } from '@/types';

export function PlayerSlot({ member }: { member?: RoomMember }) {
  if (!member) {
    return (
      <div className="flex min-h-32 flex-col items-center justify-center rounded-3xl border border-dashed border-white/10 bg-slate-900/40 text-center">
        <div className="text-sm text-slate-500">대기 중...</div>
      </div>
    );
  }

  return (
    <div className="flex min-h-32 flex-col justify-between rounded-3xl border border-white/10 bg-slate-900/80 p-5">
      <div className="flex items-center justify-between gap-2">
        <span className="text-lg font-semibold text-white">{member.nickname}</span>
        {member.isHost && (
          <span className="rounded-full bg-amber-400/15 px-3 py-1 text-xs font-medium text-amber-200">
            방장
          </span>
        )}
      </div>
      <p className="text-sm text-slate-400">{member.isHost ? '게임 시작 권한 보유' : '참가자'}</p>
    </div>
  );
}
