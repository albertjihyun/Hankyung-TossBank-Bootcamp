export default function Footer({ className = "" }) {
  return (
    <footer
      className={`w-full shrink-0 bg-[#214638] px-8 py-10 text-white ${className}`}
    >
      <div className="mx-auto flex max-w-7xl flex-col gap-8 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="mb-3 text-sm font-medium text-white/60">
            Hankyung X Toss Bank Bootcamp
          </p>

          <h2 className="text-2xl font-semibold tracking-tight">살래말래</h2>

          <p className="mt-3 max-w-md text-sm leading-6 text-white/70">
            소비를 기록하고, 위시리스트를 관리하며 더 나은 구매 결정을 돕는<br/>
            합리적 소비 습관 서비스입니다.
          </p>
        </div>

        <div className="flex flex-col gap-2 text-sm text-white/60 md:items-end">
          <p className="font-semibold text-white/80">Team DELTA</p>
          <p>Frontend · Backend · Design Collaboration</p>
          <p>© 2026 DELTA. All rights reserved.</p>
        </div>
      </div>
    </footer>
  );
}
