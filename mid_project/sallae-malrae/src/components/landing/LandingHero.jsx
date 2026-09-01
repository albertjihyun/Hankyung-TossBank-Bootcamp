"use client";

import Image from "next/image";
import Link from "next/link";
import { useLandingDay } from "@/contexts/LandingDayContext";

export default function LandingHero() {
  const { day, setDay } = useLandingDay();

  const incrementDay = () => setDay((d) => Math.min(d + 1, 30));
  const decrementDay = () => setDay((d) => Math.max(d - 1, 1));

  const scrollToCoolingOff = () => {
    document.documentElement.style.scrollSnapType = "none";
    const target = document.getElementById("cooling-off-section");
    if (target) {
      target.scrollIntoView({ behavior: "smooth" });
      setTimeout(() => {
        document.documentElement.style.scrollSnapType = "y mandatory";
      }, 1000);
    }
  };

  return (
    <section
      id="hero"
      className="landing-section snap-section relative min-h-screen overflow-hidden"
    >
      <div className="section-bg absolute inset-0 bg-gradient-to-b from-[#eefff4] to-[#ffffff]" />

      {/* 워터마크 */}
      <div
        aria-hidden="true"
        className="absolute top-[61px] left-0 w-full overflow-hidden pointer-events-none select-none"
      >
        <span
          className="block font-black text-brand-green leading-none whitespace-nowrap w-full text-center opacity-8"
          style={{ fontSize: "28vw" }}
        >
          살래말래
        </span>
      </div>

      {/* 지폐 왼쪽 */}
      <div className="hero-bill absolute -left-20 bottom-5 w-[250px] h-[225px] opacity-40 pointer-events-none md:top-10 md:w-[500px] md:h-[450px] md:opacity-75">
        <Image
          src="/images/landing_page/landing_bill_left.png"
          alt=""
          fill
          className="object-contain"
          priority
          sizes="(max-width: 768px) 250px, 500px"
        />
      </div>

      {/* 지폐 오른쪽 */}
      <div className="hero-bill absolute -right-5 bottom-15 -translate-y-1/2 w-[180px] h-[120px] opacity-40 pointer-events-none rotate-[-25deg] md:w-[350px] md:h-[240px] md:opacity-75">
        <Image
          src="/images/landing_page/landing_bill_right.png"
          alt=""
          fill
          className="object-contain"
          sizes="(max-width: 768px) 180px, 350px"
        />
      </div>

      <div className="section-content relative z-10 flex flex-col items-center justify-center min-h-screen px-6 text-center pt-16">
        <p className="text-xl md:text-6xl font-bold text-brand-dark mb-3">
          오늘 사고 싶은 그거,
        </p>

        <h1 className="text-2xl md:text-6xl font-extrabold text-brand-green leading-tight mb-8 flex items-center gap-3 flex-wrap justify-center">
          <span className="inline-flex items-center rounded-xl md:rounded-2xl mb-3 md:mb-0 px-3 md:px-5 py-1.5 mt-4 gap-3 shadow-2xl shadow-gray-500/80 bg-cream rotate-[-10deg]">
            <span className="text-4xl md:text-6xl">{day}일</span>
            <div className="flex flex-col gap-1">
              <button
                onClick={incrementDay}
                aria-label="일수 늘리기"
                className="text-[14px] md:text-[25px] leading-tight hover:opacity-60 transition-opacity border rounded-lg md:rounded-xl px-3 bg-[#f4faf6]"
              >
                ▲
              </button>
              <button
                onClick={decrementDay}
                aria-label="일수 줄이기"
                className="text-[14px] md:text-[25px] leading-tight hover:opacity-60 transition-opacity border rounded-lg md:rounded-xl px-3 bg-[#f4faf6]"
              >
                ▼
              </button>
            </div>
          </span>
          뒤에도 사고 싶을까요?
        </h1>

        <div className="flex flex-col gap-2 text-sm text-brand-dark mb-12 items-center text-center md:text-xl md:w-128 md:items-stretch md:text-left">
          <p className="md:self-start">
            &ldquo;내가 정한 <span className="font-bold">쿨링오프</span> 기간,
          </p>
          <p className="md:self-end md:mr-8">
            시간이 지나면 진짜 필요한 물건만 남아요.&rdquo;
          </p>
        </div>

        <div className="flex gap-3 md:gap-5">
          <Link
            href="/auth/login"
            className="px-5 py-2.5 text-sm border-2 border-brand-dark text-brand-dark rounded-full font-bold bg-cream shadow-xl transform transition-all duration-300 hover:-translate-y-2 hover:-translate-x-1 hover:shadow-lg md:px-8 md:py-4 md:text-lg"
          >
            시작하기
          </Link>
          <button
            onClick={scrollToCoolingOff}
            className="px-5 py-2.5 text-sm bg-brand-dark text-cream rounded-full font-bold shadow-xl transform transition-all duration-300 hover:-translate-y-2 hover:-translate-x-1 hover:shadow-lg md:px-8 md:py-4 md:text-lg"
          >
            쿨링오프가 뭐예요?
          </button>
        </div>
      </div>
    </section>
  );
}
