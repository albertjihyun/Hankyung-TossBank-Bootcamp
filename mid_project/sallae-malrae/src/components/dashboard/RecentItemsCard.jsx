import Link from "next/link";

import { formatWon } from "@/constants/dashboard";

export default function RecentItemsCard({ items }) {
  return (
    <div className="flex flex-col rounded-2xl border border-[#EEF1EA] bg-[#F7F8F2] p-4 shadow-[0_10px_28px_rgba(33,70,56,0.06)]">
      <div className="flex items-center justify-between">
        <h2 className="text-base font-semibold text-[#24352A]">
          쿨링오프 목록
        </h2>
        <Link
          href="/coolingoff"
          className="text-sm text-[#9BA59D] transition hover:text-[#2E7D5B]"
        >
          더보기
        </Link>
      </div>

      {items.length === 0 ? (
        <div className="mt-4 flex flex-col items-center justify-center gap-3 rounded-2xl border border-dashed border-[#D5DBC9] bg-white py-8 text-center">
          <p className="text-sm text-[#6B766F]">
            아직 쿨링오프 중인 항목이 없어요.
          </p>
          <Link
            href="/coolingoff/new"
            className="rounded-full bg-[#8FA58D] px-4 py-1.5 text-xs font-medium text-white transition hover:bg-[#7C9279]"
          >
            항목 등록하기
          </Link>
        </div>
      ) : (
        <div className="mt-2.5 flex flex-col gap-2">
          {items.map((item) => {
            const daysLeft = item.days_left;
            return (
              <div
                key={item.item_id}
                className="flex items-center gap-3 rounded-2xl border border-[#EEF1EA] bg-white px-3 py-2.5"
              >
                <div className="flex h-12 w-12 shrink-0 items-center justify-center overflow-hidden rounded-xl bg-[linear-gradient(135deg,#2A473B,#7EA286)] text-[10px] font-semibold text-white">
                  {item.image ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={item.image}
                      alt={item.name}
                      className="h-full w-full object-cover"
                    />
                  ) : (
                    "ITEM"
                  )}
                </div>

                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-semibold text-[#223329]">
                    {item.name}
                  </p>
                  <p className="text-xs text-[#92A094]">
                    {item.category_name} ·{" "}
                    {daysLeft === 0 ? "오늘 마감" : `남은 ${daysLeft}일`}
                  </p>
                  <p className="text-sm font-semibold text-[#314639]">
                    {formatWon(item.price)}원
                  </p>
                </div>

                <span className="rounded-full bg-[#E8F1E9] px-2.5 py-1 text-xs font-medium text-[#5D7A62]">
                  꾹 참는 중
                </span>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
