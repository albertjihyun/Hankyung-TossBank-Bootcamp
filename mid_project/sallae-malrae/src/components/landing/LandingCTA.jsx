"use client";

import Link from "next/link";
import { useLandingDay } from "@/contexts/LandingDayContext";

export default function LandingCTA() {
  const { day } = useLandingDay();

  return (
    <section
      id="cta"
      className="landing-section snap-section relative min-h-screen flex items-center justify-center overflow-hidden"
    >
      <div className="section-bg absolute inset-0 bg-[#edf5ef]" />
      <div className="section-content relative z-10 text-center px-6">
        <p className="text-xl md:text-4xl font-semibold text-brand-gray mb-3">
          살래말래와 함께
        </p>
        <h2 className="text-2xl md:text-6xl font-black text-brand-gray mb-12 gap-4 flex items-center justify-center gap-2 flex-wrap">
          딱
          <span className="inline-flex text-3xl md:text-6xl items-center rounded-xl md:rounded-2xl px-3 md:px-5 py-1.5 mt-2 shadow-2xl shadow-gray-500/80 bg-cream rotate-[-10deg] text-brand-green">
            {day}일
          </span>
          만 참아볼까요?
        </h2>
        <div>
          <Link
            href="/auth/login"
            className="px-8 py-5 border-2 border-brand-dark text-brand-dark rounded-full text-lg font-bold bg-cream shadow-xl transform transition-all duration-300 hover:-translate-y-2 hover:-translate-x-1 hover:shadow-lg"
          >
            쿨링오프 시작하기 →
          </Link>
        </div>
      </div>
    </section>
  );
}
