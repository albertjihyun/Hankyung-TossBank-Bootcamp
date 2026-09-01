"use client";

import { useState, useRef, useEffect } from "react";
import { CoolingOffCard } from "@/components/ui/Card";

const CAROUSEL_GAP = 16;
const CAROUSEL_VISIBLE = 3;

export default function PendingItemsSection({ pendingItems, onCardClick }) {
  const [isPendingExpanded, setIsPendingExpanded] = useState(false);
  const [carouselIndex, setCarouselIndex] = useState(0);
  const [carouselStep, setCarouselStep] = useState(0);
  const carouselRef = useRef(null);

  const maxCarouselIndex = Math.max(0, pendingItems.length - CAROUSEL_VISIBLE);

  useEffect(() => {
    const el = carouselRef.current;
    if (!el) return;
    const compute = () => {
      const w = el.offsetWidth;
      if (w === 0) return;
      setCarouselStep(
        (w - CAROUSEL_GAP * (CAROUSEL_VISIBLE - 1)) / CAROUSEL_VISIBLE + CAROUSEL_GAP
      );
    };
    compute();
    const ro = new ResizeObserver(compute);
    ro.observe(el);
    return () => ro.disconnect();
  }, [pendingItems.length]);

  const handlePrev = () => setCarouselIndex((i) => Math.max(0, i - 1));
  const handleNext = () => setCarouselIndex((i) => Math.min(maxCarouselIndex, i + 1));

  return (
    <div className="mb-10 p-6 bg-orange-50 rounded-3xl border border-orange-100">
      <div className="flex items-center gap-2 mb-4">
        <span className="w-2 h-2 rounded-full bg-orange-400 animate-pulse inline-block" />
        <h2 className="font-bold text-orange-500 text-base">결정 대기</h2>
        <span className="text-xs text-gray-400 ml-1">
          {pendingItems.length}개의 항목이 결정을 기다리고 있어요
        </span>
      </div>

      {/* 모바일: 1개 + 펼치기 버튼 */}
      <div className="md:hidden">
        <div className="flex flex-col gap-4">
          {(isPendingExpanded ? pendingItems : pendingItems.slice(0, 1)).map((item) => (
            <CoolingOffCard key={item.item_id} item={item} onClick={onCardClick} />
          ))}
        </div>
        {pendingItems.length > 1 && (
          <button
            onClick={() => setIsPendingExpanded((v) => !v)}
            className="mt-4 mx-auto flex items-center gap-1.5 text-xs font-medium text-orange-400 hover:text-orange-500 transition-colors"
          >
            {isPendingExpanded ? "접기" : `나머지 ${pendingItems.length - 1}개 보기`}
            <span className={`transition-transform duration-200 ${isPendingExpanded ? "rotate-180" : ""}`}>
              ▼
            </span>
          </button>
        )}
      </div>

      {/* 데스크톱: 캐러셀 */}
      <div className="hidden md:block">
        {pendingItems.length <= CAROUSEL_VISIBLE ? (
          <div className="grid grid-cols-3 gap-4">
            {pendingItems.map((item) => (
              <CoolingOffCard key={item.item_id} item={item} onClick={onCardClick} />
            ))}
          </div>
        ) : (
          <div className="relative">
            {carouselIndex > 0 && (
              <button
                onClick={handlePrev}
                className="absolute left-0 top-1/2 -translate-y-1/2 -translate-x-5 z-10 w-9 h-9 bg-white rounded-full shadow-md text-gray-500 hover:text-gray-800 flex items-center justify-center text-lg transition-colors"
              >
                ‹
              </button>
            )}
            <div className="overflow-hidden" ref={carouselRef}>
              <div
                className="flex transition-transform duration-500 ease-in-out"
                style={{
                  gap: `${CAROUSEL_GAP}px`,
                  transform: `translateX(-${carouselIndex * carouselStep}px)`,
                }}
              >
                {pendingItems.map((item) => (
                  <div
                    key={item.item_id}
                    style={{
                      width: carouselStep > 0
                        ? `${carouselStep - CAROUSEL_GAP}px`
                        : "calc(33.333% - 10.667px)",
                      flexShrink: 0,
                    }}
                  >
                    <CoolingOffCard item={item} onClick={onCardClick} />
                  </div>
                ))}
              </div>
            </div>
            {carouselIndex < maxCarouselIndex && (
              <button
                onClick={handleNext}
                className="absolute right-0 top-1/2 -translate-y-1/2 translate-x-5 z-10 w-9 h-9 bg-white rounded-full shadow-md text-gray-500 hover:text-gray-800 flex items-center justify-center text-lg transition-colors"
              >
                ›
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
