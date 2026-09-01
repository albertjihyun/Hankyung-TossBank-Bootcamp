"use client";

export default function GlobalError({ reset }) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 px-4 text-center">
      <p className="text-lg font-semibold text-gray-800">문제가 발생했어요.</p>
      <p className="text-sm text-gray-400">잠시 후 다시 시도해주세요.</p>
      <button
        onClick={reset}
        className="rounded-xl bg-[#8FA58D] px-5 py-2 text-sm font-medium text-white hover:bg-[#7C9279] transition-colors"
      >
        다시 시도
      </button>
    </div>
  );
}
