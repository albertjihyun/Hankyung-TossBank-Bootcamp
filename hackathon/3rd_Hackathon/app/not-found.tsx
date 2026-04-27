import Link from 'next/link';

export default function NotFound() {
  return (
    <main className="flex min-h-screen items-center justify-center px-6">
      <div className="rounded-3xl border border-white/10 bg-white/5 p-8 text-center shadow-xl">
        <h1 className="text-3xl font-black text-white">404</h1>
        <p className="mt-3 text-slate-300">찾을 수 없는 페이지예요.</p>
        <Link href="/lobby" className="mt-6 inline-flex rounded-2xl bg-sky-500 px-4 py-2 text-white">
          로비로 이동
        </Link>
      </div>
    </main>
  );
}
