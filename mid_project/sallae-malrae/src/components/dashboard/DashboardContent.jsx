import Link from "next/link";

import Card from "@/components/ui/Card";
import ShareButton from "./ShareButton";
import RecentItemsCard from "./RecentItemsCard";
import CategoryChartCard from "./CategoryChartCard";
import { CATEGORY_COLORS, LEVEL_LABEL, formatWon, getChartBackground } from "@/constants/dashboard";

export default function DashboardContent({ data }) {
  const { user, summary, recentItems, expired_count, categoryChart } = data;

  const passedCount = summary.passed_count;
  const savedAmount = summary.saved_amount;
  const successRate = summary.success_rate;
  const isEmpty = passedCount === 0 && recentItems.length === 0;

  const summaryCards = [
    {
      label: "이번 달 참은 횟수",
      value: passedCount.toLocaleString("ko-KR"),
      hint: passedCount === 0 ? "아직 기록이 없어요" : "이번 달 기준",
    },
    {
      label: "이번 달 절약 금액",
      value: formatWon(savedAmount),
      hint: "단위 (원)",
    },
    {
      label: "이번 달 성공률",
      value: `${successRate}%`,
      hint:
        passedCount + summary.bought_count === 0
          ? "아직 결정한 항목이 없어요"
          : "이번 달 기준",
      progress: successRate,
    },
    {
      label: "레벨",
      value: `Lv.${user.level}`,
      hint: LEVEL_LABEL[user.level] ?? "꾸준히 가는 중",
    },
  ];

  const chartItems = categoryChart.map((item, idx) => ({
    ...item,
    color: CATEGORY_COLORS[idx % CATEGORY_COLORS.length],
  }));
  const chartBackground = getChartBackground(chartItems);

  return (
    <Card className="flex flex-col gap-3 border border-white/70 p-4 shadow-[0_24px_60px_rgba(33,70,56,0.10)] sm:p-5 lg:gap-4 lg:p-6">
      <div className="pl-2 sm:pl-3">
        <h1 className="mt-2 text-[clamp(1.5rem,2vw,1.9rem)] font-bold tracking-tight text-[#1D2A21]">
          안녕하세요, {user.nickname}님
        </h1>

        <p className="mt-1 mb-12 text-sm text-[#6B766F]">
          {isEmpty ? (
            <>이번 달에 등록한 항목이 없어요. 첫 항목을 등록해보세요.</>
          ) : (
            <>
              이번 달에{" "}
              <span className="font-semibold text-[#2E7D5B]">
                {formatWon(savedAmount)}원
              </span>
              의 충동구매를 막아냈어요. 정말 대단해요.
            </>
          )}
        </p>

        {expired_count > 0 && (
          <Link
            href="/coolingoff"
            className="group mt-3 flex items-center gap-3 rounded-2xl border border-[#F4C57A] bg-gradient-to-r from-[#FFF4E0] to-[#FFE9C8] px-4 py-3 shadow-[0_6px_18px_rgba(180,113,28,0.10)] transition hover:from-[#FFEFCF] hover:to-[#FFE0B0]"
          >
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[#F4A93C] text-white">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.2"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="h-5 w-5"
                aria-hidden="true"
              >
                <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
                <line x1="12" y1="9" x2="12" y2="13" />
                <line x1="12" y1="17" x2="12.01" y2="17" />
              </svg>
            </span>

            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold text-[#8C5311]">
                기한이 지난 항목이 {expired_count}개 있어요
              </p>
              <p className="mt-0.5 text-xs text-[#A8763A]">
                결정하지 않으면 통계에 반영되지 않아요.
              </p>
            </div>

            <span className="flex items-center gap-1 text-xs font-medium text-[#8C5311] transition group-hover:gap-1.5">
              결정하러 가기
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="h-3.5 w-3.5"
                aria-hidden="true"
              >
                <polyline points="9 18 15 12 9 6" />
              </svg>
            </span>
          </Link>
        )}
      </div>

      <div className="grid grid-cols-2 gap-3 xl:grid-cols-4">
        {summaryCards.map((item) => (
          <div
            key={item.label}
            className="relative flex flex-col gap-1.5 rounded-2xl border border-[#EEF1EA] bg-[#F7F8F2] px-4 py-3 shadow-[0_8px_20px_rgba(33,70,56,0.05)]"
          >
            <div className="flex items-center justify-between">
              <p className="text-sm font-semibold text-[#314639]">
                {item.label}
              </p>
              {item.label === "레벨" && (
                <ShareButton user={user} summary={summary} />
              )}
            </div>

            <p className="text-[1.5rem] font-bold leading-none text-[#4A8A72] lg:text-[1.75rem]">
              {item.value}
            </p>

            {item.progress != null ? (
              <div>
                <div className="h-1.5 rounded-full bg-[#DCE6DB]">
                  <div
                    className="h-full rounded-full bg-[#4A8A72] transition-all"
                    style={{ width: `${item.progress}%` }}
                  />
                </div>
                <p className="mt-1 text-xs text-[#9BA59D]">{item.hint}</p>
              </div>
            ) : (
              <p className="text-xs text-[#9BA59D]">{item.hint}</p>
            )}
          </div>
        ))}
      </div>

      <div className="mb-6 grid grid-cols-1 items-stretch gap-3 xl:grid-cols-[1.12fr_0.88fr]">
        <RecentItemsCard items={recentItems} />
        <CategoryChartCard items={chartItems} background={chartBackground} />
      </div>
    </Card>
  );
}
