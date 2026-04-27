'use client';

export default function GlobalError({ reset }: { error: Error; reset: () => void }) {
  return (
    <html lang="ko">
      <body className="flex min-h-screen items-center justify-center bg-slate-950 px-6 text-white">
        <div className="rounded-3xl border border-white/10 bg-white/5 p-8 text-center">
          <h1 className="text-2xl font-bold">문제가 발생했어요</h1>
          <p className="mt-3 text-slate-300">잠시 후 다시 시도해주세요.</p>
          <button
            onClick={reset}
            className="mt-6 rounded-2xl bg-sky-500 px-5 py-3 text-sm font-semibold text-white transition hover:bg-sky-400"
          >
            다시 시도
          </button>
        </div>
      </body>
    </html>
  );
}
