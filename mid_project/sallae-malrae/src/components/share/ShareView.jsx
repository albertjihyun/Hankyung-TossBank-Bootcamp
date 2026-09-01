import Image from "next/image";
import Link from "next/link";

import { getLevelMeta } from "@/lib/level";

export default function ShareView({ data }) {
  return (
    <div className="flex min-h-screen justify-center bg-[#F1F7F0] px-3 pt-2 pb-10 sm:px-6 sm:pt-4 sm:pb-14 lg:px-8">
      <div className="mx-auto w-full max-w-5xl">
        <div className="rounded-3xl bg-[#EEF6EE] p-4 sm:p-8">
          <div className="mb-6 flex justify-center sm:justify-start">
            <Link href="/" className="inline-flex">
              <Image
                src="/images/logo.png"
                alt="살래말래"
                width={200}
                height={80}
                priority
                className="h-[56px] w-auto sm:h-[64px]"
              />
            </Link>
          </div>

          <ShareContent data={data} />
        </div>
      </div>
    </div>
  );
}

function ShareContent({ data }) {
  const { nickname, level, summary } = data;
  const { name: title, description, image: levelImage } = getLevelMeta(level);

  const passedCount = summary.passed_count;
  const boughtCount = summary.bought_count;
  const successRate = summary.success_rate;
  const total = passedCount + boughtCount;

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_1.1fr]">
      <LevelCard
        level={level}
        title={title}
        description={description}
        nickname={nickname}
        levelImage={levelImage}
      />

      <div className="flex flex-col gap-4">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <CountCard label="이번 달 참은 횟수" value={passedCount} total={total} accent />
          <CountCard label="이번 달 구매 횟수" value={boughtCount} total={total} />
        </div>

        <SuccessRateCard
          rate={successRate}
          passed={passedCount}
          total={total}
        />

        <CTACard />
      </div>
    </div>
  );
}

function LevelCard({ level, title, description, nickname, levelImage }) {
  return (
    <div className="flex flex-col overflow-hidden rounded-3xl border border-[#CBE0CF] bg-white shadow-[0_18px_40px_rgba(33,70,56,0.08)]">
      <div className="flex flex-col items-center bg-gradient-to-b from-[#D8EBDD] to-[#EEF6EE] px-6 pt-8 pb-6">
        <span className="rounded-full bg-[#4A8A72] px-3 py-1 text-xs font-bold tracking-wide text-white">
          LV.{level}
        </span>

        <div className="mt-5 flex h-32 w-32 items-center justify-center rounded-3xl bg-white shadow-[0_8px_20px_rgba(33,70,56,0.08)] sm:h-44 sm:w-44">
          <Image
            src={levelImage}
            alt={`레벨 ${level} 이미지`}
            width={160}
            height={160}
            className="h-full w-full object-contain p-3"
          />
        </div>
      </div>

      <div className="flex flex-1 flex-col items-center px-6 pt-6 pb-7 text-center">
        <h2 className="text-xl font-bold text-[#1D2A21]">{title}</h2>
        <p className="mt-2 whitespace-pre-line text-sm leading-relaxed text-[#55655A]">
          {description}
        </p>

        {nickname && (
          <span className="mt-5 rounded-full bg-[#E8F2EA] px-3 py-1 text-xs font-medium text-[#3F6A4D]">
            @{nickname}의 기록
          </span>
        )}
      </div>
    </div>
  );
}

function CountCard({ label, value, total, accent = false }) {
  return (
    <div className="rounded-2xl border border-[#E4EBE0] bg-white p-5">
      <p className="text-sm font-medium text-[#55655A]">{label}</p>
      <p
        className={`mt-2 text-3xl font-bold ${
          accent ? "text-[#4A8A72]" : "text-[#1D2A21]"
        }`}
      >
        {value.toLocaleString("ko-KR")}
      </p>
      <p className="mt-1 text-xs text-[#9BA59D]">
        총 {total.toLocaleString("ko-KR")}번 중
      </p>
    </div>
  );
}

function SuccessRateCard({ rate, passed, total }) {
  const radius = 28;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference * (1 - rate / 100);

  return (
    <div className="flex items-center justify-between gap-4 rounded-2xl border border-[#E4EBE0] bg-white p-5">
      <div>
        <p className="text-sm font-medium text-[#55655A]">이번 달 쿨링오프 성공률</p>
        <p className="mt-2 text-3xl font-bold text-[#4A8A72]">{rate}%</p>
        <p className="mt-1 text-xs text-[#9BA59D]">
          이번 달 {total.toLocaleString("ko-KR")}번 중 {passed.toLocaleString("ko-KR")}번 참았어요
        </p>
      </div>

      <div className="relative h-20 w-20 shrink-0">
        <svg viewBox="0 0 72 72" className="h-full w-full -rotate-90">
          <circle
            cx="36"
            cy="36"
            r={radius}
            fill="none"
            stroke="#E4EBE0"
            strokeWidth="8"
          />
          <circle
            cx="36"
            cy="36"
            r={radius}
            fill="none"
            stroke="#4A8A72"
            strokeWidth="8"
            strokeLinecap="round"
            strokeDasharray={circumference}
            strokeDashoffset={offset}
          />
        </svg>
        <div className="absolute inset-0 flex items-center justify-center text-xs font-semibold text-[#4A8A72]">
          {rate}%
        </div>
      </div>
    </div>
  );
}

function CTACard() {
  return (
    <div className="flex items-center justify-between gap-4 rounded-2xl border border-[#E4EBE0] bg-white p-5">
      <div className="min-w-0">
        <p className="text-sm font-semibold text-[#1D2A21]">
          나도 충동구매를 줄여볼까요?
        </p>
        <p className="mt-1 text-xs text-[#6B766F]">
          살래? 말래? 조금만 기다려봐요.
          <br />
          충동구매 브레이크 서비스, 살래말래
        </p>
      </div>
      <Link
        href="/?from=share"
        className="shrink-0 rounded-xl bg-[#FBEEC4] px-5 py-3 text-sm font-semibold text-[#7A5A1C] transition hover:bg-[#F6E3A8]"
      >
        시작하기
      </Link>
    </div>
  );
}
