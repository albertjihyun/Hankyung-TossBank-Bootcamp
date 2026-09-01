"use client";

import { useState } from "react";
import StepCard from "@/components/landing/StepCard";

const STEPS = [
  {
    step: 1,
    title: "담기",
    subtitle: "Hot",
    description: '"와, 이건 사야 해!" 싶은 아이템을 등록하세요.',
    imageSrc: "/images/landing_page/shopping_cart.png",
    imageStyle: { width: "550px", height: "550px", bottom: 15, left: -70 },
    isActive: false,
  },
  {
    step: 2,
    title: "식히기",
    subtitle: "Cooling",
    description: "1일? 7일? 당신의 결심만큼 쿨링오프 기간을 설정하세요.",
    imageSrc: "/images/landing_page/clock_calendar.png",
    imageStyle: { width: "370px", height: "370px", top: -25, right: -35 },
    isActive: true,
  },
  {
    step: 3,
    title: "결정",
    subtitle: "Clear",
    description: '기간이 끝난 뒤, 집계된 통계를 보며 결정하세요. "아직도 사고 싶나요?"',
    imageSrc: "/images/landing_page/decision.png",
    imageStyle: { width: "280px", height: "280px", top: -5, right: 10 },
    isActive: false,
  },
];

export default function LandingSteps() {
  const [hoveredStep, setHoveredStep] = useState(null);

  return (
    <section
      id="steps"
      className="landing-section snap-section relative min-h-screen flex items-center justify-center md:overflow-hidden"
    >
      <div className="section-bg absolute inset-0" />
      <div className="section-content relative z-10 w-full px-6">
        <div className="text-center mb-10 md:mb-16 gap-4 flex flex-col">
          <h2 className="text-2xl md:text-4xl mt-0 md:mt-5 font-extrabold text-brand-dark mb-2">
            충동구매를 막는 3단계
          </h2>
          <p className="text-sm md:text-xl text-gray-500">뜨거운 마음, 차가운 결정으로</p>
        </div>

        {/* 모바일: 가로 스크롤 캐러셀 */}
        <div className="step-card-fan md:hidden flex overflow-x-auto snap-x snap-mandatory gap-5 -mx-6 px-4 pb-6">
          {STEPS.map((step) => (
            <div
              key={step.step}
              className="step-card-item snap-center flex-shrink-0 relative"
              style={{ width: "270px", height: "330px" }}
            >
              <div
                className="absolute"
                style={{ transform: "scale(0.75)", transformOrigin: "top left", width: "360px", height: "440px" }}
              >
                <StepCard {...step} />
              </div>
            </div>
          ))}
        </div>

        {/* 데스크톱: 팬 레이아웃 */}
        <div
          className="step-card-fan hidden md:flex relative justify-center items-center mx-auto"
          style={{ height: "480px" }}
        >
          {STEPS.map((step, i) => {
            const cfg = [
              { x: -320, y: 0,   rotate: -7, z: 1 },
              { x: 0,    y: -40, rotate:  6, z: 2 },
              { x: 320,  y: 0,   rotate: -7, z: 3 },
            ][i];
            const isHovered = hoveredStep === i;
            return (
              <div
                key={step.step}
                style={{
                  position: "absolute",
                  transform: isHovered
                    ? `translateX(${cfg.x}px) translateY(${cfg.y - 16}px) rotate(0deg)`
                    : `translateX(${cfg.x}px) translateY(${cfg.y}px) rotate(${cfg.rotate}deg)`,
                  zIndex: isHovered ? 10 : cfg.z,
                  transition: "transform 0.4s ease",
                  cursor: "pointer",
                }}
                onMouseEnter={() => setHoveredStep(i)}
                onMouseLeave={() => setHoveredStep(null)}
              >
                <div className="step-card-item">
                  <StepCard {...step} />
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
