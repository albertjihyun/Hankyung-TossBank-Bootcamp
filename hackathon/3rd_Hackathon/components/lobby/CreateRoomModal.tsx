'use client';

import { FormEvent, useState } from 'react';

interface CreateRoomModalProps {
  open: boolean;
  loading: boolean;
  onClose: () => void;
  onSubmit: (title: string) => Promise<void>;
}

export function CreateRoomModal({ open, loading, onClose, onSubmit }: CreateRoomModalProps) {
  const [title, setTitle] = useState('');

  if (!open) return null;

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    await onSubmit(title);
    setTitle('');
  };

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/70 px-4 backdrop-blur-sm">
      <div className="w-full max-w-md rounded-[2rem] border border-white/10 bg-slate-900 p-6 shadow-2xl">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-bold text-white">방 만들기</h2>
          <button onClick={onClose} className="text-sm text-slate-400 hover:text-white">
            닫기
          </button>
        </div>
        <form onSubmit={handleSubmit} className="mt-6 space-y-4">
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="방 이름을 입력하세요"
            maxLength={100}
            className="w-full rounded-2xl border border-white/10 bg-slate-950 px-4 py-3 text-white outline-none placeholder:text-slate-500 focus:border-sky-400"
          />
          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              className="rounded-xl border border-white/10 px-4 py-2 text-sm text-slate-300 transition hover:bg-white/5"
            >
              취소
            </button>
            <button
              type="submit"
              disabled={loading}
              className="rounded-xl bg-sky-500 px-4 py-2 text-sm font-semibold text-white transition hover:bg-sky-400 disabled:opacity-50"
            >
              {loading ? '생성 중...' : '생성'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
